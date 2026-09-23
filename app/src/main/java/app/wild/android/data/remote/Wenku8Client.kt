package app.wild.android.data.remote

import android.content.Context
import app.wild.android.data.local.CookieDao
import app.wild.android.data.local.CookieEntity
import app.wild.android.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Cloudflare 挑战：命中即触发 WebView 绕过（spec §3.4）。 */
class CfChallengeException(val url: String, message: String = "Cloudflare challenge") :
    Exception(message)

/** 站点返回的业务/HTTP 错误。 */
class Wenku8HttpException(val code: Int, message: String) : Exception(message)

/** 站点把 App UA 的请求 302 到 login.php —— 该页面需要登录态。 */
class NeedLoginException(val url: String) : Exception("需要登录")

/**
 * Wenku8 HTTP 内核（spec §3.2/§3.3）：
 * - OkHttp + Room 持久化 cookie（登录态 = `jieqiUserInfo` 存在）。
 * - 浏览器 UA 持久化于 SettingsStore（Dalvik UA 会被站点 302 到 login.php）；
 *   CF 检测（`Just a moment`/`_cf_chl_opt`/`cf_chl`）。
 * - GBK 解码响应、GBK URL 编码中文参数；写操作不跟随 302（302=成功语义）。
 * - CF 命中时抛 [CfChallengeException]，由 [CfBypass] 用隐藏 WebView 解出
 *   `cf_clearance` 后重试（clearance 与 UA/IP 绑定，故 WebView UA 已对齐本 UA）。
 */
class Wenku8Client(
    private val context: Context,
    private val cookieDao: CookieDao,
    private val webCacheDao: app.wild.android.data.local.WebCacheDao,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cookies = java.util.concurrent.ConcurrentHashMap<String, CookieEntity>()
    private val jarMutex = Mutex()

    private val _userAgent = MutableStateFlow("")
    val userAgent: StateFlow<String> = _userAgent

    /** 是否持有 cf_clearance（最近一次 WebView 绕过所得）。 */
    private val _hasClearance = MutableStateFlow(false)
    val hasClearance: StateFlow<Boolean> = _hasClearance

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.values.filter { domainMatches(it.domain, url.host) }
                .map { c ->
                    Cookie.Builder()
                        .name(c.name).value(c.value)
                        .domain(c.domain.removePrefix("."))
                        .path("/")
                        .build()
                }

        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            scope.launch {
                cookieList.forEach { c ->
                    val entity = CookieEntity(
                        domain = c.domain,
                        name = c.name,
                        value = c.value,
                        expiryEpochMs = c.expiresAt,
                    )
                    cookies["${entity.domain}|${entity.name}"] = entity
                    cookieDao.upsert(entity)
                    if (entity.name == "cf_clearance") _hasClearance.value = true
                }
            }
        }
    }

    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(okhttp3.Cache(java.io.File(context.cacheDir, "http"), 32L * 1024 * 1024))
        .build()

    /** 不跟随重定向（addbookcase/delid 的 302=成功语义）。 */
    private val noRedirectClient: OkHttpClient = plainClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Coil 图片加载专用 client：自带 UA + Referer（插图/封面域名防盗链）+ cookie jar + 磁盘缓存。 */
    fun imageClient(): OkHttpClient = plainClient.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", uaFor(chain.request().url.encodedPath))
                .header("Referer", SettingsStore.DEFAULT_API_HOST + "/")
                .build()
            chain.proceed(req)
        }
        .build()

    /** 原始 client（供需要自定拦截器的场景）。 */
    fun rawClient(): OkHttpClient = plainClient

    suspend fun init() = withContext(Dispatchers.IO) {
        // 恢复持久化 cookie
        cookieDao.all().forEach { c -> cookies["${c.domain}|${c.name}"] = c }
        _hasClearance.value = cookies.values.any { it.name == "cf_clearance" }
        // UA：站点按 UA 分流——App UA 可直连内容页（book/toc/chapter/reviews/checkcode），
        // 交互页（index/modules）被 302 到 login.php；浏览器 UA 一律吃 CF 挑战。
        // 故默认 App UA；CF 交互页另行用固定浏览器 UA（见 uaFor），与 CfBypass WebView 同 UA。
        val stored = settings.userAgent.first()
        if (stored.isNotBlank() && stored.startsWith("Dalvik")) {
            _userAgent.value = stored
        } else {
            val ua = "Dalvik/2.1.0 (Linux; U; Android ${10 + (0..5).random()}; " +
                "Pixel ${5 + (0..4).random()} Build/UQ1A.${(230000..250000).random()}.00${(1..9).random()})"
            _userAgent.value = ua
            settings.setUserAgent(ua)
        }
    }

    suspend fun apiHost(): String {
        val h = settings.apiHost.first().trim()
        return if (h.isEmpty()) SettingsStore.DEFAULT_API_HOST else h.trimEnd('/')
    }

    /** 预热会话：GET `/` + `/login.php` 种 session/clearance cookie（spec `init_session`）。
     *  每个请求独立容忍失败（`/` 会被 302→login 抛 NeedLoginException），
     *  保证 `/login.php` 一定执行到——它才是种 PHPSESSID 的关键。 */
    suspend fun initSession() {
        runCatching { get("/") }
        runCatching { get("/login.php") }
    }

    suspend fun isLoggedIn(): Boolean = cookieDao.isLoggedIn()

    /**
     * 带接口缓存的 GET（spec §3.5 `cache_first`）：
     * - 新鲜缓存（< ttlMs）直接返回；
     * - 过期缓存：先走网络，失败（非 CF 挑战）时降级返回旧缓存；
     * - CF 挑战一律上抛 [CfChallengeException]（不清缓存）。
     */
    suspend fun getCached(path: String, ttlMs: Long, referer: String? = null): String {
        val key = "GET $path"
        val cached = webCacheDao.get(key)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.cachedAtMs < ttlMs) return cached.contentJson
        return try {
            val fresh = get(path, referer)
            webCacheDao.put(
                app.wild.android.data.local.WebCacheEntity(key, fresh, now)
            )
            fresh
        } catch (e: CfChallengeException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) cached.contentJson else throw e
        }
    }

    suspend fun sessionCookieString(host: String): String =
        cookies.values.filter { domainMatches(it.domain, host) }
            .joinToString("; ") { "${it.name}=${it.value}" }

    /** 供 WebView 预种 cookie（保 session，避免 challenge 页被重定向到 login）。 */
    fun sessionCookiePairs(host: String): List<Pair<String, String>> =
        cookies.values.filter { domainMatches(it.domain, host) }
            .map { it.name to it.value }

    /** WebView 解出 cf_clearance 后写入 cookie 库（OkHttp 侧立即生效）。 */
    suspend fun importWebViewCookies(host: String, cookieHeader: String) {
        cookieHeader.split(";").forEach { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) return@forEach
            val name = pair.substring(0, i).trim()
            val value = pair.substring(i + 1).trim()
            val domain = host
            val entity = CookieEntity(domain, name, value)
            cookies["$domain|$name"] = entity
            cookieDao.upsert(entity)
            if (name == "cf_clearance") _hasClearance.value = true
        }
    }

    suspend fun clearCookies() {
        cookies.clear()
        cookieDao.clearAll()
        _hasClearance.value = false
    }

    /** 清 OkHttp 磁盘缓存 + 接口缓存（设置页「清除接口缓存」）。 */
    suspend fun evictHttpCache() {
        withContext(Dispatchers.IO) {
            plainClient.cache?.evictAll()
            webCacheDao.clearAll()
        }
    }

    // ---- 请求原语 ----

    private fun uaHeaders(builder: Request.Builder, referer: String? = null): Request.Builder {
        builder.header("User-Agent", uaFor(builder.build().url.encodedPath))
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        builder.header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
        builder.header("Referer", referer ?: "${SettingsStore.DEFAULT_API_HOST}/login.php")
        return builder
    }

    /**
     * 每路径 UA（实测站点按 UA 分流）：
     * - `/index.php`、`/modules/article/…` → CF 交互页，用固定浏览器 UA
     *   （与 CfBypass WebView 一致，cf_clearance 按 UA 绑定）。
     * - 其余（book/novel/reviews/checkcode/login/图片）→ App UA，站点直连放行。
     */
    fun uaFor(path: String): String =
        if (path.startsWith("/index.php") || path.startsWith("/modules/") || path == "/") {
            SettingsStore.DEFAULT_UA
        } else {
            _userAgent.value.ifBlank { "Dalvik/2.1.0" }
        }

    private suspend fun execute(url: String, referer: String? = null, noRedirect: Boolean = false): okhttp3.Response =
        withContext(Dispatchers.IO) {
            val req = uaHeaders(Request.Builder().url(url), referer).get().build()
            (if (noRedirect) noRedirectClient else plainClient).newCall(req).execute()
        }

    /** GET → GBK 文本；CF/HTTP 错误转异常。 */
    suspend fun get(path: String, referer: String? = null, noRedirect: Boolean = false): String {
        val url = if (path.startsWith("http")) path else apiHost() + path
        execute(url, referer, noRedirect).use { resp ->
            return handle(resp, url)
        }
    }

    /** GET → 原始字节（验证码/图片）。 */
    suspend fun getBytes(path: String, referer: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val url = if (path.startsWith("http")) path else apiHost() + path
            val req = uaHeaders(Request.Builder().url(url), referer).get().build()
            plainClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Wenku8HttpException(resp.code, "HTTP ${resp.code} for $url")
                resp.body.bytes()
            }
        }

    /** POST 表单 → GBK 文本。 */
    suspend fun post(path: String, form: Map<String, String>, referer: String? = null, noRedirect: Boolean = false): String {
        val url = if (path.startsWith("http")) path else apiHost() + path
        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val req = uaHeaders(Request.Builder().url(url), referer).post(body).build()
            (if (noRedirect) noRedirectClient else plainClient).newCall(req).execute().use { resp ->
                handle(resp, url)
            }
        }
    }

    /** POST 表单 → 响应（需要看状态码/头的写操作）。 */
    suspend fun postResponse(path: String, form: Map<String, String>, referer: String? = null): okhttp3.Response =
        withContext(Dispatchers.IO) {
            val url = if (path.startsWith("http")) path else apiHost() + path
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val req = uaHeaders(Request.Builder().url(url), referer).post(body).build()
            noRedirectClient.newCall(req).execute()
        }

    /** POST 多值表单（`checkid[]`×N 这类重复 key），不跟随重定向，返回原始响应。 */
    suspend fun postMultiValue(
        path: String,
        pairs: List<Pair<String, String>>,
        referer: String? = null,
    ): okhttp3.Response = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else apiHost() + path
        val body = FormBody.Builder().apply { pairs.forEach { (k, v) -> add(k, v) } }.build()
        val req = uaHeaders(Request.Builder().url(url), referer).post(body).build()
        noRedirectClient.newCall(req).execute()
    }

    private fun handle(resp: okhttp3.Response, url: String): String {
        val bytes = resp.body.bytes()
        val text = decodeGbk(bytes)
        android.util.Log.d("Wenku8Client", "GET $url → ${resp.code} len=${bytes.size} cf=${isCfChallenge(resp.code, text)}")
        // 站点 UA 分流：App UA 访问交互页会被 302 到 login.php（跟随重定向后终态 URL 即 login）。
        if (resp.request.url.encodedPath.contains("login.php") && !url.contains("login.php")) {
            throw NeedLoginException(url)
        }
        if (isCfChallenge(resp.code, text)) throw CfChallengeException(url)
        if (resp.code == 302 || resp.code == 301) return text // 写操作语义：302=成功
        if (!resp.isSuccessful) throw Wenku8HttpException(resp.code, "HTTP ${resp.code} for $url")
        return text
    }

    /** GET（写操作专用，返回 code+body 由调用方判 302/关键字）。 */
    suspend fun getResponse(path: String, referer: String? = null): okhttp3.Response =
        withContext(Dispatchers.IO) {
            val url = if (path.startsWith("http")) path else apiHost() + path
            val req = uaHeaders(Request.Builder().url(url), referer).get().build()
            noRedirectClient.newCall(req).execute()
        }

    companion object {
        private val GBK = charset("GBK")

        fun decodeGbk(bytes: ByteArray): String = String(bytes, GBK)

        fun gbkEncode(s: String): String = URLEncoder.encode(s, "GBK")

        fun domainMatches(cookieDomain: String, host: String): Boolean {
            val d = cookieDomain.removePrefix(".")
            return host == d || host.endsWith(".$d") || d.endsWith(".$host") || d == host
        }

        /** CF 判定（spec §3.3）：非 2xx/命中挑战标记即 CF 挑战。 */
        fun isCfChallenge(code: Int, body: String): Boolean {
            if (code == 403 || code == 503) {
                return body.contains("Just a moment") || body.contains("cf_chl") ||
                    body.contains("Attention Required") || body.contains("Enable JavaScript") ||
                    body.contains("_cf_chl_opt") || body.contains("challenge-platform")
            }
            return body.contains("_cf_chl_opt") ||
                (body.contains("Just a moment") && body.contains("challenge"))
        }
    }
}
