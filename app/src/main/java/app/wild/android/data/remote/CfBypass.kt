package app.wild.android.data.remote

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Cloudflare 绕过（spec §3.4）：`/modules/article/…` 命中 CF 挑战时，
 * 用一个 1px 隐藏 WebView 加载同一 URL，等 `cf_clearance` cookie 落地后
 * 回填 OkHttp cookie 库并重试一次。clearance 与 UA/IP 绑定，故 WebView
 * UA 与 [Wenku8Client] 保持一致。
 *
 * - [attach] 由 MainActivity.onCreate 调用（WebView 需要 Activity context + 挂在视图树）。
 * - [guard] 包一层数据源调用：抛 [CfChallengeException] → 解 clearance → 重试一次。
 * - [solving]/[solveCount]/[failCount]/[lastError] 供 UI 状态条与架构文档引用。
 */
class CfBypass(
    private val client: Wenku8Client,
) {
    private var webView: WebView? = null
    private var attachedActivity: Activity? = null
    private val mutex = Mutex()

    private val _solving = MutableStateFlow(false)
    val solving: StateFlow<Boolean> = _solving
    private val _solveCount = MutableStateFlow(0)
    val solveCount: StateFlow<Int> = _solveCount
    private val _failCount = MutableStateFlow(0)
    val failCount: StateFlow<Int> = _failCount
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** MainActivity onCreate 调用：创建 1px WebView 挂进 decorView（不可见但在渲染管线内）。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        if (webView != null && attachedActivity === activity) return
        if (webView != null) detachWebView()
        attachedActivity = activity
        val wv = WebView(activity)
        wv.layoutParams = ViewGroup.LayoutParams(1, 1)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 与 Wenku8Client.uaFor 的浏览器 UA 一致（cf_clearance 按 UA 绑定）
            userAgentString = app.wild.android.data.prefs.SettingsStore.DEFAULT_UA
        }
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                android.util.Log.d("CfBypass", "wv onPageStarted: $url")
            }
            override fun onPageFinished(view: WebView, url: String) {
                android.util.Log.d("CfBypass", "wv onPageFinished: $url")
            }
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                android.util.Log.w("CfBypass", "wv error ${error.errorCode} ${error.description} url=${request.url}")
            }
            override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                android.util.Log.w("CfBypass", "wv http ${errorResponse.statusCode} url=${request.url}")
            }
        }
        wv.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("CfBypass", "wv console: ${msg.message()}")
                return true
            }
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)
        (activity.window.decorView as? ViewGroup)?.addView(wv)
        webView = wv
    }

    /**
     * Activity onDestroy 调：仅当销毁的正是当前持有者才拆 WebView。
     * 深链会再起一个 Activity 实例：新实例先 attach、旧实例后 onDestroy，
     * 不判持有者会把新 WebView 拆掉（表现为偶发 webView==null）。
     */
    fun detachFrom(activity: Activity) {
        if (attachedActivity === activity) detachWebView()
    }

    private fun detachWebView() {
        webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webView = null
        attachedActivity = null
    }

    /**
     * 执行 [block]；命中 CF 挑战 → 解 clearance → 重试一次。
     * 第二次仍失败则原样抛出（UI 呈现明确的 CF 错误态）。
     */
    suspend fun <T> guard(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CfChallengeException) {
            android.util.Log.d("CfBypass", "challenge hit on ${e.url}, solving…")
            if (ensureClearance(e.url)) return block()
            throw e
        }
    }

    /** 解 cf_clearance：加载挑战页 → 轮询挑战标记消失 → 回填 cookie。串行（Mutex）。 */
    suspend fun ensureClearance(url: String): Boolean = mutex.withLock {
        val wv = webView ?: run {
            _lastError.value = "WebView 未就绪"
            android.util.Log.w("CfBypass", "webView==null, cannot solve $url")
            return@withLock false
        }
        _solving.value = true
        android.util.Log.d("CfBypass", "ensureClearance start: $url")
        try {
            val full = if (url.startsWith("http")) url else client.apiHost() + url
            withContext(Dispatchers.Main) {
                // clearance 与 UA 绑定：WebView 固定浏览器 UA 已在 attach 时对齐 uaFor
                val cm = CookieManager.getInstance()
                val host = full.substringAfter("://").substringBefore('/')
                val pairs = client.sessionCookiePairs(host)
                android.util.Log.d("CfBypass", "seeding ${pairs.size} cookies: ${pairs.map { it.first }}")
                pairs.forEach { (n, v) ->
                    cm.setCookie("${client.apiHost()}/", "$n=$v")
                }
                cm.flush()
                wv.loadUrl(full)
                android.util.Log.d("CfBypass", "loadUrl issued: $full")
            }

            // 轮询：挑战标记消失（_cf_chl_opt 不存在）或 cf_clearance 落 cookie。
            // 先等页面真正加载（readyState=complete 且 URL 已是目标页），
            // 否则空白页会被误判为「挑战已过」。
            var pageReady = false
            var solved = false
            for (i in 0 until 90) {
                if (solved) break
                delay(500)
                if (!pageReady) {
                    pageReady = evaluateJs(
                        wv,
                        "document.readyState==='complete'" +
                            " && location.href.indexOf('wenku8')>=0",
                    )
                    if (i % 6 == 0) {
                        val title = evaluateJsString(wv, "document.title")
                        android.util.Log.d("CfBypass", "poll#$i loading title=$title")
                    }
                    continue
                }
                val challengeGone = evaluateJs(
                    wv,
                    "(typeof _cf_chl_opt==='undefined')" +
                        " && !document.title.includes('Just a moment')" +
                        " && !document.title.includes('请稍候')" +
                        " && location.pathname.indexOf('login')<0",
                )
                val cookieHeader = withContext(Dispatchers.Main) {
                    CookieManager.getInstance().getCookie(client.apiHost()).orEmpty()
                }
                if (i % 6 == 0) {
                    val title = evaluateJsString(wv, "document.title")
                    android.util.Log.d(
                        "CfBypass",
                        "poll#$i challengeGone=$challengeGone title=$title cookies=${cookieHeader.take(80)}",
                    )
                }
                val hasClearance = cookieHeader.contains("cf_clearance")
                if (hasClearance) {
                    client.importWebViewCookies(host(), cookieHeader)
                    solved = true
                } else if (challengeGone) {
                    // 挑战页已过但 cookie 可能刚写盘——再抓一次 cookie 看有没有
                    val header2 = withContext(Dispatchers.Main) {
                        CookieManager.getInstance().getCookie(client.apiHost()).orEmpty()
                    }
                    client.importWebViewCookies(host(), header2)
                    solved = true
                }
            }
            if (solved) {
                _solveCount.value += 1
                _lastError.value = null
                android.util.Log.d("CfBypass", "clearance solved for $url")
            } else {
                _failCount.value += 1
                _lastError.value = "CF 挑战等待超时（45s）"
                android.util.Log.w("CfBypass", "clearance TIMEOUT for $url")
            }
            solved
        } catch (e: Exception) {
            _failCount.value += 1
            _lastError.value = "CF 绕过失败：${e.message}"
            false
        } finally {
            _solving.value = false
        }
    }

    private suspend fun host(): String =
        client.apiHost().substringAfter("://").substringBefore('/')

    private suspend fun evaluateJs(wv: WebView, js: String): Boolean =
        withContext(Dispatchers.Main) {
            kotlinx.coroutines.withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    wv.evaluateJavascript(js) { v ->
                        if (cont.isActive) cont.resume(v?.trim('"') == "true")
                    }
                }
            } ?: false
        }

    private suspend fun evaluateJsString(wv: WebView, js: String): String =
        withContext(Dispatchers.Main) {
            kotlinx.coroutines.withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    wv.evaluateJavascript(js) { v ->
                        if (cont.isActive) cont.resume(v ?: "null")
                    }
                }
            } ?: "<timeout>"
        }
}
