package app.wild.android.data.remote

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import app.wild.android.data.prefs.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/** CF 通行状态机（SZKM-66 方案 §4）。
 *  Idle        —— 无挑战在途（初始/已解/已放弃后的回落态）
 *  Solving     —— 隐藏 WebView 正在解 cf_clearance
 *  NeedsUser   —— 需要用户在可见验证页手动过一次（UI 监听弹 CfVerifyScreen）
 *  Verified    —— 刚拿到 cf_clearance（短暂态，UI 用于自动收起验证页）
 *  Failed      —— 用户放弃/等待超时，UI 给出「重试验证」入口
 */
sealed interface CfState {
    data object Idle : CfState
    data object Solving : CfState
    data class NeedsUser(val url: String) : CfState
    data object Verified : CfState
    data class Failed(val reason: String) : CfState
}

/**
 * Cloudflare 会话守卫（替代旧 CfBypass，spec §3.4 修订版）：
 *
 * - [guard] 是所有数据源调用的唯一入口：原请求 → CfChallengeException →
 *   隐藏 WebView 求解（60s，判据=CookieManager 出现 cf_clearance）→ 透明重试；
 *   隐藏失败/重试仍被拦/硬阻断 → [CfState.NeedsUser] 等可见验证页放行后再重试。
 * - cookie/UA 一致性：WebView 固定 [SettingsStore.DEFAULT_UA]（与 OkHttp 同域 UA
 *   相同），进出 WebView 的 cookie 经 [Wenku8Client.sessionCookiePairs]/
 *   [Wenku8Client.importWebViewCookies] 双向同步。
 * - 兜底 [fetchHtmlViaWebView]：clearance 重放失效时直接用可见 WebView 取页面 HTML。
 * - [attach]/[detachFrom] 由 MainActivity 驱动（隐藏 WebView 需要视图树内实例）。
 */
class CfSession(
    private val client: Wenku8Client,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<CfState>(CfState.Idle)
    val state: StateFlow<CfState> = _state

    /** 统计（UI 状态条/日志/验收）：求解中、成功次数、失败次数、最后错误。 */
    private val _solveCount = MutableStateFlow(0)
    val solveCount: StateFlow<Int> = _solveCount
    private val _failCount = MutableStateFlow(0)
    val failCount: StateFlow<Int> = _failCount
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var solverWebView: WebView? = null
    private var attachedActivity: Activity? = null
    private val solveMutex = Mutex()
    private val warmMutex = Mutex()
    private var warmed = false

    @Volatile private var lastChallengeUrl: String? = null
    private var userWaiter: CompletableDeferred<Boolean>? = null
    private val waiterMutex = Mutex()
    private var verifyWatcher: Job? = null
    /** 用户放弃验证的时间戳；放弃后短暂抑制自动弹验证页（防「放弃→页面重试→再弹」循环）。 */
    @Volatile private var gaveUpAt: Long = 0L

    private val tag = "CfSession"

    /** 最近一次命中挑战的 URL（可见验证页加载目标；无记录回首页）。 */
    fun currentVerifyUrl(): String = lastChallengeUrl ?: "https://www.wenku8.net/"

    // ==================== attach / detach ====================

    /** MainActivity.onCreate：建 1px 隐藏 WebView 挂进 decorView（不可见但在渲染管线内）。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        if (solverWebView != null && attachedActivity === activity) return
        if (solverWebView != null) detachWebView()
        attachedActivity = activity
        val wv = try {
            WebView(activity)
        } catch (e: Exception) {
            android.util.Log.w(tag, "WebView 创建失败：${e.message}（设备无 WebView？）")
            _lastError.value = "设备无 WebView，无法过站点验证"
            return
        }
        wv.layoutParams = ViewGroup.LayoutParams(1, 1)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // cf_clearance 按 UA 绑定——与 Wenku8Client.uaFor 的同域 UA 一致
            userAgentString = SettingsStore.DEFAULT_UA
        }
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                android.util.Log.d(tag, "wv onPageFinished: $url")
            }
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                android.util.Log.w(tag, "wv error ${error.errorCode} ${error.description} url=${request.url}")
            }
            override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                android.util.Log.w(tag, "wv http ${errorResponse.statusCode} url=${request.url}")
            }
        }
        wv.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d(tag, "wv console: ${msg.message()}")
                return true
            }
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)
        (activity.window.decorView as? ViewGroup)?.addView(wv)
        solverWebView = wv
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
        solverWebView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        solverWebView = null
        attachedActivity = null
    }

    // ==================== guard（唯一入口） ====================

    /**
     * 执行 [block]；命中 CF 挑战 → 隐藏求解 → 重试一次；
     * 仍被拦或不可解 → 可见验证页（NeedsUser）→ 放行后再重试一次。
     * 第二次仍失败则原样抛出 CfChallengeException（UI 呈现错误态+重试验证入口）。
     */
    suspend fun <T> guard(block: suspend () -> T): T {
        var hardBlock = false
        try {
            return block()
        } catch (e: CfChallengeException) {
            lastChallengeUrl = e.url
            hardBlock = e.hardBlock
            android.util.Log.d(tag, "challenge hit on ${e.url} (hardBlock=${e.hardBlock})")
        }
        if (!client.hasClearance.value) {
            if (!solveHidden(lastChallengeUrl.orEmpty(), hardBlock = hardBlock)) {
                // 隐藏求解失败/无解 → 可见验证页等待用户
                if (!awaitUserVerification()) throw CfChallengeException(lastChallengeUrl.orEmpty())
            }
        }
        // 第一次透明重试
        try {
            return block()
        } catch (e2: CfChallengeException) {
            lastChallengeUrl = e2.url
            android.util.Log.d(tag, "retry still challenged on ${e2.url} (hardBlock=${e2.hardBlock})")
            // 手上的 clearance 可能已被服务端作废（最常见情形）：
            // 非硬阻断时先丢弃旧证再跑一次隐藏重解，失败才升级可见验证页。
            if (!e2.hardBlock && !suppressed()) {
                client.invalidateClearance(lastChallengeUrl.orEmpty())
                if (solveHidden(lastChallengeUrl.orEmpty(), hardBlock = false, force = true)) {
                    try {
                        return block()
                    } catch (e3: CfChallengeException) {
                        lastChallengeUrl = e3.url
                        android.util.Log.d(tag, "re-solve retry still challenged on ${e3.url} → NeedsUser")
                        if (!awaitUserVerification(mark = e3.hardBlock)) throw e3
                    }
                } else {
                    if (!awaitUserVerification()) throw e2
                }
            } else {
                if (!awaitUserVerification(mark = e2.hardBlock)) throw e2
            }
        }
        // 可见验证放行后最后一次重试；仍被拦则上抛（UI 给「打开验证页」入口）
        return block()
    }

    // ==================== 隐藏求解 ====================

    /**
     * 解 cf_clearance：1px WebView 加载挑战页 → 轮询 CookieManager 出
     * cf_clearance（或挑战页翻篇成正常页）。串行（solveMutex），超时 60s。
     * [hardBlock]=true 直接返回 false（无挑战可解，省得空转）。
     * [force]=true 跳过「已有 clearance 直接返回」的短路——用于服务端作废
     * 旧证后的重解（调用方需先 [Wenku8Client.invalidateClearance]）。
     */
    private suspend fun solveHidden(
        url: String,
        hardBlock: Boolean,
        force: Boolean = false,
    ): Boolean {
        if (hardBlock) {
            android.util.Log.w(tag, "hardBlock（Attention Required）：跳过隐藏求解")
            return false
        }
        // 冷启动竞态：Application 级预热可能早于 MainActivity.attach——
        // 有界等待 solver WebView 就位（≤15s），仍无才判失败。
        var wv = solverWebView
        if (wv == null) {
            android.util.Log.d(tag, "solverWebView==null, waiting for attach…")
            for (i in 0 until 75) {
                delay(200)
                wv = solverWebView
                if (wv != null) break
            }
        }
        if (wv == null) {
            _lastError.value = "WebView 未就绪"
            android.util.Log.w(tag, "solverWebView==null after wait, cannot solve $url")
            return false
        }
        return solveMutex.withLock {
            if (!force && client.hasClearance.value) return@withLock true // 排队期间已被别人解出
            _state.value = CfState.Solving
            android.util.Log.d(tag, "solveHidden start: $url")
            try {
                val full = if (url.startsWith("http")) url else client.apiHost() + url
                val host = full.substringAfter("://").substringBefore('/')
                val baselineClearance = clearanceValue(host)
                withContext(Dispatchers.Main) {
                    seedWebViewCookies(host)
                    wv.loadUrl(full)
                    android.util.Log.d(tag, "loadUrl issued: $full")
                }

                var pageReady = false
                var solved = false
                for (i in 0 until 120) {
                    if (solved) break
                    delay(500)
                    val (clearance, _) = clearanceCookie(host)
                    if (clearance != null && clearance != baselineClearance) {
                        harvest(host)
                        solved = true
                        break
                    }
                    if (!pageReady) {
                        pageReady = evaluateJs(
                            wv,
                            "document.readyState==='complete'" +
                                " && location.href.indexOf('wenku8')>=0",
                        )
                        if (i % 10 == 0) {
                            val title = evaluateJsString(wv, "document.title")
                            android.util.Log.d(tag, "poll#$i loading title=$title")
                        }
                        continue
                    }
                    val stillChallenged = evaluateJs(wv, CHALLENGE_PRESENT_JS)
                    val title = if (i % 10 == 0) evaluateJsString(wv, "document.title") else null
                    if (i % 10 == 0) {
                        android.util.Log.d(tag, "poll#$i challenged=$stillChallenged title=$title clearance=${clearance != null}")
                    }
                    if (!stillChallenged) {
                        harvest(host)
                        // 挑战页翻篇：无论 clearance 是否在手都视为解过，
                        // 让重试用事实说话（修 RC-1 双向误判）
                        solved = true
                    }
                }
                if (solved) {
                    _solveCount.value += 1
                    _lastError.value = null
                    _state.value = CfState.Verified
                    android.util.Log.d(tag, "clearance solved for $url")
                } else {
                    _failCount.value += 1
                    _lastError.value = "CF 挑战等待超时（60s）"
                    markNeedsUser()
                    android.util.Log.w(tag, "clearance TIMEOUT for $url")
                }
                solved
            } catch (e: Exception) {
                _failCount.value += 1
                _lastError.value = "CF 绕过失败：${e.message}"
                markNeedsUser()
                false
            }
        }
    }

    // ==================== 可见验证页协作 ====================

    /** UI 手动入口：把状态推进 NeedsUser（错误块「打开站点验证」按钮用）。
     *  求解中/已在验证页时不打断。 */
    fun requestUserVerify() {
        val s = _state.value
        if (s is CfState.Solving || s is CfState.NeedsUser) return
        val url = lastChallengeUrl ?: return
        _state.value = CfState.NeedsUser(url)
        startVerifyWatcher()
        android.util.Log.d(tag, "requestUserVerify → NeedsUser($url)")
    }

    private fun markNeedsUser() {
        if (suppressed()) {
            android.util.Log.d(tag, "markNeedsUser suppressed (user just gave up)")
            return
        }
        val url = lastChallengeUrl ?: "https://www.wenku8.net/"
        _state.value = CfState.NeedsUser(url)
        startVerifyWatcher()
    }

    /** 放弃后 GIVE_UP_SUPPRESS_MS 内不自动弹验证页；手动「打开站点验证」不受限。 */
    private fun suppressed(): Boolean =
        System.currentTimeMillis() - gaveUpAt < GIVE_UP_SUPPRESS_MS

    /** 等待用户在可见页通过（或放弃/超时 300s）。已在等待则加入同一 waiter。
     *  [mark]=true 时（重试仍被硬阻断）先推进 NeedsUser；抑制期内直接失败。 */
    private suspend fun awaitUserVerification(mark: Boolean = true): Boolean {
        if (suppressed()) return false
        if (mark) markNeedsUser()
        if (suppressed()) return false // markNeedsUser 期间可能刚被放弃
        // userWaiter 创建放锁内：并发请求共用同一个 deferred，
        // 否则后到者覆盖先到者的 waiter，先到者只能等满超时。
        val waiter = waiterMutex.withLock {
            userWaiter ?: CompletableDeferred<Boolean>().also { userWaiter = it }
        }
        return withTimeoutOrNull(300_000) { waiter.await() } ?: run {
            _state.value = CfState.Failed("验证等待超时")
            false
        }
    }

    /** 可见页后台哨兵：4s 一查 cf_clearance/挑战翻篇，命中即放行。 */
    private fun startVerifyWatcher() {
        if (verifyWatcher?.isActive == true) return
        verifyWatcher = scope.launch {
            val host = client.apiHost().substringAfter("://").substringBefore('/')
            val baseline = clearanceValue(host)
            repeat(300) {
                delay(4_000)
                if (checkPassed(host, baseline)) {
                    android.util.Log.d(tag, "verifyWatcher: passed")
                    onUserVerified()
                    return@launch
                }
            }
        }
    }

    /** 用户已通过：收割 cookie → 放行等待者 → Verified。 */
    suspend fun onUserVerified() {
        val host = client.apiHost().substringAfter("://").substringBefore('/')
        harvest(host)
        userWaiter?.complete(true)
        userWaiter = null
        gaveUpAt = 0L
        _state.value = CfState.Verified
        _solveCount.value += 1
        _lastError.value = null
        android.util.Log.d(tag, "user verified, cookies harvested")
    }

    /** 用户手动点「我已通过」时的即时检查（不必等 4s 哨兵）。返回 true=确认通过。 */
    suspend fun checkNow(): Boolean {
        val host = client.apiHost().substringAfter("://").substringBefore('/')
        val (clearance, _) = clearanceCookie(host)
        if (clearance != null) {
            onUserVerified()
            return true
        }
        return false
    }

    /**
     * 可见验证页 WebViewClient.onPageFinished 回调：
     * 页面已翻过挑战（无 CF 标记）且持有 clearance → 判通过。
     * 覆盖「clearance 值未变但挑战已放行」的情形（哨兵只认新值）。
     */
    suspend fun onVerifyPageFinished(wv: WebView) {
        val host = client.apiHost().substringAfter("://").substringBefore('/')
        val (clearance, _) = clearanceCookie(host)
        if (clearance == null) return
        val challenged = evaluateJs(wv, CHALLENGE_PRESENT_JS)
        if (!challenged) {
            android.util.Log.d(tag, "verify page finished, no challenge markers → verified")
            onUserVerified()
        }
    }

    /** 用户放弃验证（可见页返回键/关闭）：Failed + 放行等待者（false）。 */
    fun onUserGaveUp() {
        userWaiter?.complete(false)
        userWaiter = null
        verifyWatcher?.cancel()
        verifyWatcher = null
        gaveUpAt = System.currentTimeMillis()
        _state.value = CfState.Failed("用户取消了验证")
        _failCount.value += 1
        _lastError.value = "站点验证未完成"
        android.util.Log.d(tag, "user gave up verification")
    }

    /** 退出登录：状态归零（cookie 清理由 Wenku8Client.clearCookies 负责）。 */
    fun reset() {
        userWaiter?.complete(false)
        userWaiter = null
        verifyWatcher?.cancel()
        verifyWatcher = null
        _state.value = CfState.Idle
        lastChallengeUrl = null
        gaveUpAt = 0L
        warmed = false
    }

    // ==================== 会话预热 / WebView 兜底 ====================

    /** 幂等会话预热：GET `/` + `/login.php`（经 guard，命中挑战自动解）。 */
    suspend fun ensureWarmed() = warmMutex.withLock {
        if (warmed) return@withLock
        runCatching { guard { client.initSession() } }
            .onSuccess { warmed = true }
            .onFailure { android.util.Log.w(tag, "ensureWarmed: ${it.message}") }
    }

    /**
     * 第三级兜底（SZKM-66 §8）：clearance 重放失效时，让隐藏 WebView 直接
     * 加载目标 URL 并取回页面 HTML（不依赖 OkHttp cookie 重放）。
     * 返回 null = WebView 不可用/超时/取回的仍是挑战页。
     */
    suspend fun fetchHtmlViaWebView(url: String): String? {
        val wv = solverWebView ?: return null
        // 用户刚放弃验证（抑制期内）不再空转 60s——与硬阻断早退同类体验
        if (suppressed()) {
            android.util.Log.d(tag, "fetchHtmlViaWebView suppressed (user just gave up)")
            return null
        }
        return solveMutex.withLock {
            val full = if (url.startsWith("http")) url else client.apiHost() + url
            val host = full.substringAfter("://").substringBefore('/')
            android.util.Log.d(tag, "fetchHtmlViaWebView: $full")
            try {
                withContext(Dispatchers.Main) {
                    seedWebViewCookies(host)
                    wv.loadUrl(full)
                }
                var pageReady = false
                for (i in 0 until 120) {
                    delay(500)
                    val (clearance, _) = clearanceCookie(host)
                    if (clearance != null) harvest(host)
                    if (!pageReady) {
                        pageReady = evaluateJs(
                            wv,
                            "document.readyState==='complete'" +
                                " && location.href.indexOf('wenku8')>=0",
                        )
                        continue
                    }
                    val challenged = evaluateJs(wv, CHALLENGE_PRESENT_JS)
                    if (challenged) {
                        // IP 级硬阻断（Attention Required）无解可等——立即放弃
                        if (evaluateJs(wv, "document.title.indexOf('Attention Required')>=0")) {
                            android.util.Log.w(tag, "fetchHtmlViaWebView: hard-block page, bail")
                            return@withLock null
                        }
                        continue
                    }
                    val html = evaluateJsString(wv, "document.documentElement.outerHTML")
                    if (html != null && html.length > 500) {
                        android.util.Log.d(tag, "fetchHtmlViaWebView got ${html.length} chars")
                        return@withLock html
                    }
                }
                android.util.Log.w(tag, "fetchHtmlViaWebView timeout: $full")
                null
            } catch (e: Exception) {
                android.util.Log.w(tag, "fetchHtmlViaWebView failed: ${e.message}")
                null
            }
        }
    }

    // ==================== cookie 双向同步 ====================

    /** 把 OkHttp 侧 session cookie 种进 WebView（保登录态，避免挑战页被踢去登录）。 */
    private suspend fun seedWebViewCookies(host: String) {
        val cm = CookieManager.getInstance()
        val pairs = client.sessionCookiePairs(host)
        android.util.Log.d(tag, "seeding ${pairs.size} cookies: ${pairs.map { it.first }}")
        pairs.forEach { (n, v) ->
            runCatching {
                cm.setCookie("${client.apiHost()}/", "$n=$v; Domain=${rootDomain(host)}; Path=/")
            }
        }
        cm.flush()
    }

    /** WebView → OkHttp：收割 host + 根域两处 cookie。 */
    private suspend fun harvest(host: String) {
        val cm = CookieManager.getInstance()
        val headers = withContext(Dispatchers.Main) {
            buildList {
                cm.getCookie("${client.apiHost()}/")?.let { add(it) }
                cm.getCookie("https://${rootDomain(host)}/")?.let { add(it) }
            }
        }
        headers.forEach { client.importWebViewCookies(host, it) }
    }

    /** CookieManager 里 host 与根域两处合并的 cookie 头。 */
    private suspend fun webViewCookieHeader(host: String): String =
        withContext(Dispatchers.Main) {
            val cm = CookieManager.getInstance()
            val a = cm.getCookie("${client.apiHost()}/").orEmpty()
            val b = cm.getCookie("https://${rootDomain(host)}/").orEmpty()
            if (b.isEmpty()) a else if (a.isEmpty()) b else "$a; $b"
        }

    /** cf_clearance 的当前值（host/根域合并查找）；null=不存在。 */
    private suspend fun clearanceCookie(host: String): Pair<String?, String> {
        val header = webViewCookieHeader(host)
        val value = header.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("cf_clearance=") }
            ?.substringAfter('=')
        return value to header
    }

    private suspend fun clearanceValue(host: String): String? = clearanceCookie(host).first

    /** 通过判据：拿到新 clearance，或挑战页已翻篇且有 clearance。 */
    private suspend fun checkPassed(host: String, baseline: String?): Boolean {
        val (clearance, _) = clearanceCookie(host)
        if (clearance == null) return false
        return clearance != baseline
    }

    private fun rootDomain(host: String): String =
        host.split('.').takeLast(2).joinToString(".")

    private companion object {
        private const val GIVE_UP_SUPPRESS_MS = 30_000L
    }

    // ==================== JS 求值工具 ====================

    /** 挑战页存在判据（隐藏求解/可见页哨兵/兜底取 HTML 共用）。 */
    private val CHALLENGE_PRESENT_JS =
        "(typeof _cf_chl_opt!=='undefined')" +
            " || document.title.indexOf('Just a moment')>=0" +
            " || document.title.indexOf('请稍候')>=0" +
            " || document.title.indexOf('Attention Required')>=0" +
            " || (document.body && document.body.innerHTML.indexOf('challenge-platform')>=0)"

    private suspend fun evaluateJs(wv: WebView, js: String): Boolean =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    wv.evaluateJavascript(js) { v ->
                        if (cont.isActive) cont.resume(v?.trim('"') == "true")
                    }
                }
            } ?: false
        }

    /** evaluateJavascript 的返回值是 JSON 字符串字面量（含转义），用 Json 正确解码。 */
    private suspend fun evaluateJsString(wv: WebView, js: String): String? =
        withContext(Dispatchers.Main) {
            val raw = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<String?> { cont ->
                    wv.evaluateJavascript(js) { v ->
                        if (cont.isActive) cont.resume(v)
                    }
                }
            } ?: return@withContext null
            if (raw == "null") return@withContext null
            runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonPrimitive.content
            }.getOrNull()
        }
}
