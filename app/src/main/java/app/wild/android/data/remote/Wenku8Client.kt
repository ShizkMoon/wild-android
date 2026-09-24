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

/** Cloudflare 挑战：命中即触发 WebView 绕过（spec §3.4）。
 *  [hardBlock] = `Attention Required` 级 IP 硬阻断，无 JS 挑战可解，
 *  隐藏求解注定失败，应直接降级可见验证页。 */
class CfChallengeException(
    val url: String,
    val hardBlock: Boolean = false,
    message: String = "Cloudflare challenge",
) : Exception(message)

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
 * - CF 命中时抛 [CfChallengeException]，由 [CfSession] 状态机处理
 *   （隐藏 WebView 解 → 可见验证页降级），clearance 与 UA/IP 绑定——
 *   故 wenku8 域内请求与求解器 WebView 一律同一浏览器 UA（见 [uaFor]）。
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
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val sendable = cookies.values
                .filter { domainMatches(it.domain, url.host) && !it.isExpired(now) }
            // 运行中自检：同域请求实际不再携带 cf_clearance（如 TTL 到期/被删）
            // 时复位标记，避免守卫误以为「有证」而跳过隐藏重解。
            if (isWenku8Host(url.host) || url.host.equals(apiHostName, ignoreCase = true)) {
                _hasClearance.value = sendable.any { it.name == "cf_clearance" }
            }
            return sendable
                .map { c ->
                    Cookie.Builder()
                        .name(c.name).value(c.value)
                        .domain(c.domain.removePrefix("."))
                        .path(c.path)
                        .build()
                }
        }

        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            // 内存先同步写（后续请求立即可见），Room 落库异步。
            val entities = mutableListOf<CookieEntity>()
            val expired = mutableListOf<CookieEntity>()
            cookieList.forEach { c ->
                val entity = CookieEntity(
                    domain = c.domain,
                    name = c.name,
                    value = c.value,
                    expiryEpochMs = c.expiresAt,
                    path = c.path,
                    hostOnly = !c.domain.startsWith("."),
                )
                if (entity.isExpired()) {
                    cookies.remove("${entity.domain}|${entity.name}")
                    expired += entity
                } else {
                    cookies["${entity.domain}|${entity.name}"] = entity
                    entities += entity
                    if (entity.name == "cf_clearance") _hasClearance.value = true
                }
            }
            scope.launch {
                entities.forEach { cookieDao.upsert(it) }
                expired.forEach { cookieDao.delete(it) }
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
                .header("User-Agent", uaFor(chain.request().url))
                .header("Referer", SettingsStore.DEFAULT_API_HOST + "/")
                .build()
            chain.proceed(req)
        }
        .build()

    /** 原始 client（供需要自定拦截器的场景）。 */
    fun rawClient(): OkHttpClient = plainClient

    private val initMutex = Mutex()
    private var initialized = false

    /** 幂等初始化：恢复持久化 cookie（剔除过期）+ UA。
     *  UA 策略（SZKM-66 方案 D-2）：wenku8.net 域内一律固定浏览器 UA——
     *  cf_clearance 按 UA+IP 绑定，按路径分流会让通行证对内容页无效；
     *  App UA（Dalvik）仅保留给站外/图片等不挂 CF 的请求。 */
    suspend fun init() = initMutex.withLock {
        if (initialized) return@withLock
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cookieDao.deleteExpired(now)
            cookieDao.all().forEach { c -> cookies["${c.domain}|${c.name}"] = c }
            _hasClearance.value = cookies.values.any { it.name == "cf_clearance" && !it.isExpired(now) }
            val stored = settings.userAgent.first()
            if (stored.isNotBlank() && stored.startsWith("Dalvik")) {
                _userAgent.value = stored
            } else {
                val ua = "Dalvik/2.1.0 (Linux; U; Android ${10 + (0..5).random()}; " +
                    "Pixel ${5 + (0..4).random()} Build/UQ1A.${(230000..250000).random()}.00${(1..9).random()})"
                _userAgent.value = ua
                settings.setUserAgent(ua)
            }
            initialized = true
        }
    }

    suspend fun apiHost(): String {
        val h = settings.apiHost.first().trim()
        val base = if (h.isEmpty()) SettingsStore.DEFAULT_API_HOST else h.trimEnd('/')
        apiHostName = base.substringAfter("://").substringBefore('/')
        return base
    }

    /** apiHost 的 host（最近一次 apiHost() 读取时刷新），uaFor 判域用。 */
    @Volatile private var apiHostName: String = "www.wenku8.net"

    /** 预热会话：GET `/` + `/login.php` 种 session/clearance cookie（spec `init_session`）。
     *  CF 挑战一律上抛（交给外层 guard 求解后重试）；其余错误逐个容忍，
     *  保证 `/login.php` 一定执行到——它才是种 PHPSESSID 的关键。 */
    suspend fun initSession() {
        runCatching { get("/") }.onFailure { if (it is CfChallengeException) throw it }
        runCatching { get("/login.php") }.onFailure { if (it is CfChallengeException) throw it }
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

    /** 供 WebView 预种 cookie（保 session，避免 challenge 页被重定向到 login）。 */
    fun sessionCookiePairs(host: String): List<Pair<String, String>> =
        cookies.values.filter { domainMatches(it.domain, host) }
            .map { it.name to it.value }

    /** WebView 解出 cf_clearance 后写入 cookie 库（OkHttp 侧立即生效）。
     *  CookieManager 不暴露 domain 属性，以抓取时的 host 记（hostOnly）；
     *  真实 expiry 也拿不到——cf_clearance 写保守 TTL 兜底，避免作废旧证
     *  永久躺在库里让守卫误以为「有证不用重解」。 */
    suspend fun importWebViewCookies(host: String, cookieHeader: String) {
        val now = System.currentTimeMillis()
        cookieHeader.split(";").forEach { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) return@forEach
            val name = pair.substring(0, i).trim()
            val value = pair.substring(i + 1).trim()
            if (name.isEmpty()) return@forEach
            val entity = CookieEntity(
                domain = host,
                name = name,
                value = value,
                expiryEpochMs = if (name == "cf_clearance") now + CF_CLEARANCE_TTL_MS else 0L,
                path = "/",
                hostOnly = true,
            )
            if (entity.isExpired(now)) return@forEach
            cookies["$host|$name"] = entity
            cookieDao.upsert(entity)
            if (name == "cf_clearance") _hasClearance.value = true
        }
    }

    /** 退出登录：清 Room + 内存 + WebView CookieManager（防跨账号泄漏与旧 clearance 污染）。 */
    suspend fun clearCookies() {
        cookies.clear()
        cookieDao.clearAll()
        _hasClearance.value = false
        withContext(Dispatchers.Main) {
            runCatching {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
            }
        }
    }

    /**
     * 丢弃 cf_clearance（服务端作废后重解前置）：内存 + Room + WebView
     * CookieManager 三处同清，并复位 hasClearance——不清 WebView 侧会让
     * solveHidden 的 baseline 判新被旧值干扰（旧值=baseline，永远等不到「新值」）。
     * [url] 用于定位 host；空串回退 apiHost。
     */
    suspend fun invalidateClearance(url: String = "") {
        val host = url.substringAfter("://").substringBefore('/')
            .ifEmpty { apiHost().substringAfter("://").substringBefore('/') }
        val root = host.split('.').takeLast(2).joinToString(".")
        cookies.keys.filter { it.substringAfter('|') == "cf_clearance" }
            .forEach { cookies.remove(it) }
        cookieDao.deleteClearance()
        _hasClearance.value = false
        withContext(Dispatchers.Main) {
            runCatching {
                val cm = android.webkit.CookieManager.getInstance()
                // 过期写空值覆盖两处可能的存储位置（host / 根域）
                cm.setCookie("https://$host/", "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                cm.setCookie("https://$root/", "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                cm.flush()
            }
        }
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
        builder.header("User-Agent", uaFor(builder.build().url))
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        builder.header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
        builder.header("Referer", referer ?: "${SettingsStore.DEFAULT_API_HOST}/login.php")
        return builder
    }

    /**
     * 域名级 UA（SZKM-66 方案 D-2）：`*.wenku8.net` 一律固定浏览器 UA——
     * cf_clearance 按 UA 绑定，按路径分流会让通行证对内容页立即失效；
     * WebView 求解侧也固定同一 UA（见 CfSession.attach）。
     * 其余主机（图片/外部 API，不挂 CF）用 App Dalvik UA。
     */
    fun uaFor(url: HttpUrl): String =
        if (isWenku8Host(url.host) || url.host.equals(apiHostName, ignoreCase = true)) {
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
                if (!resp.isSuccessful) {
                    val body = runCatching { decodeGbk(resp.body.bytes()) }.getOrDefault("")
                    if (isCfChallenge(resp.code, body)) {
                        throw CfChallengeException(url, hardBlock = isCfHardBlock(resp.code, body))
                    }
                    throw Wenku8HttpException(resp.code, "HTTP ${resp.code} for $url")
                }
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
        // 判定顺序：302→login 先于 CF/成功（302 本身是写操作成功语义，
        // 但目标 login.php 的那一种 = 「未登录被踢」，必须识别成 NeedLogin）。
        if (isLoginRedirect(resp, url)) throw NeedLoginException(url)
        if (isCfChallenge(resp.code, text)) {
            throw CfChallengeException(url, hardBlock = isCfHardBlock(resp.code, text))
        }
        if (resp.code in 301..303) return text // 写操作语义：302=成功
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

        /** cf_clearance 收割 TTL：CookieManager 不给真实 expiry，写保守值兜底自清。 */
        private const val CF_CLEARANCE_TTL_MS = 90 * 60 * 1000L

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

        /** IP/指纹级硬阻断：无 JS 挑战对象，隐藏 WebView 无解，只能走可见验证页。 */
        fun isCfHardBlock(code: Int, body: String): Boolean =
            code == 403 && body.contains("Attention Required") &&
                !body.contains("cf_chl") && !body.contains("_cf_chl_opt")

        /**
         * 「被踢去登录」判定（SZKM-66 RC-4）：跟随重定向后终态是 login.php，
         * 或 noRedirect 响应的 Location 指向 login.php——二者都是 NeedLogin，
         * 绝不能当成写操作成功。
         */
        fun isLoginRedirect(resp: okhttp3.Response, originalUrl: String): Boolean {
            if (resp.code in 301..303 || resp.code in 307..308) {
                resp.header("Location")?.let { if (it.contains("login.php")) return true }
            }
            val finalPath = resp.request.url.encodedPath
            return finalPath.contains("login.php") && !originalUrl.contains("login.php")
        }

        /** 站点域判定：`wenku8.net` 及其子域（镜像设置项也兼容其它二级域）。 */
        fun isWenku8Host(host: String): Boolean =
            host == "wenku8.net" || host.endsWith(".wenku8.net")
    }
}
