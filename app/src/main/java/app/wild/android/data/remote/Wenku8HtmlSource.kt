package app.wild.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URLEncoder
import java.util.Base64

/**
 * `Wenku8DataSource` 的 HTML 抓取实现（spec §4 决策 A 修订版）。
 *
 * 解析选择器逐条移植自 Flutter 原 App `rust/src/wenku8/client.rs`：
 *   parse_index / parse_tags / parse_books / parse_page_stats / parse_reader /
 *   c_content(extract_content_text) / parse_novel_info（改为 /book/{aid}.htm）/
 *   parse_bookcase_list / parse_book_in_case / parse_reviews / parse_userdetail /
 *   add_bookshelf（302 语义）/ move_bookcase / delete_bookcase / sign。
 *
 * 已知修订：
 *  - novelInfo 改走 `/book/{aid}.htm`（articleinfo.php 被 CF 拦；/book 直连可达）。
 *  - 目录 cid 取 `{cid}.htm` 文件名（reader.php 的 query 解析方式不适用 index.htm）。
 *  - XML API 已死（api.php 恒回 0），chapterText 只能 HTML 抓取 + 自行重建插图标记。
 */
class Wenku8HtmlSource(
    private val client: Wenku8Client,
    private val cf: CfSession? = null,
) : Wenku8DataSource {

    /** CF 守卫：CfSession 未注入（测试场景）时直接执行。
     *  注意必须在 IO 上下文跑：guard 内 `withContext(Main)` 需要主线程空闲，
     *  若在 Main 上调用会自我死锁（主线程等 IO、IO 等主线程）。 */
    private suspend fun <T> guard(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            if (cf == null) block() else cf.guard(block)
        }

    /** 接口缓存读（TTL：index 10min、其余 1h；spec §3.5）。
     *  CF 重试仍被拦时降级「可见 WebView 直接取 HTML」兜底（方案 §8 第三级）。 */
    private suspend fun cached(path: String, ttlMs: Long): String =
        try {
            guard { client.getCached(path, ttlMs) }
        } catch (e: CfChallengeException) {
            cf?.fetchHtmlViaWebView(path) ?: throw e
        }

    private suspend fun fetch(path: String, referer: String? = null): String =
        try {
            guard { client.get(path, referer) }
        } catch (e: CfChallengeException) {
            cf?.fetchHtmlViaWebView(path) ?: throw e
        }

    companion object {
        private const val TTL_INDEX = 10L * 60 * 1000
        private const val TTL_DEFAULT = 60L * 60 * 1000

        /** 排行榜 sort 合法值（audit_pages_general.md:204）。 */
        val TOPLIST_SORTS = listOf(
            "lastupdate", "postdate", "allvisit", "allvote", "goodnum",
            "dayvisit", "dayvote", "monthvisit", "monthvote",
            "weekvisit", "weekvote", "size", "anime",
        )
    }

    // ===================== 会话 / 账户 =====================

    override suspend fun initSession(): Result<Unit> = runCatching {
        // 经 cfSession 预热：`/` 与 `/login.php` 命中挑战也能先解再种（RC-3 修复）。
        if (cf == null) client.initSession() else cf.ensureWarmed()
    }

    override suspend fun login(
        username: String,
        password: String,
        checkcode: String,
    ): Result<Unit> = runCatching {
        val body = guard {
            client.post(
                "/login.php",
                mapOf(
                    "username" to username,
                    "password" to password,
                    "checkcode" to checkcode,
                    "usecookie" to "315360000",
                    "action" to "login",
                ),
            )
        }
        if (!body.contains("登录成功")) {
            // 服务端把失败原因打在 .blockcontent / 正文里，提取一段友好文案
            val doc = Jsoup.parse(body)
            val msg = doc.select(".blockcontent").first()?.text()?.trim()
                ?: doc.select("div.block").first()?.text()?.trim()
                ?: body.take(300)
            throw Wenku8HttpException(200, "登录失败：$msg")
        }
    }

    override suspend fun checkcodeImage(): Result<ByteArray> = runCatching {
        guard { client.getBytes("/checkcode.php?random=${System.currentTimeMillis()}") }
    }

    override suspend fun userDetail(): Result<Map<String, String>> = runCatching {
        val html = fetch("/userdetail.php?charset=gbk")
        parseUserDetail(html)
    }

    override suspend fun sign(): Result<String> = runCatching {
        // XML API 已死（api.php 恒回 "0"）；保留调用以维持语义，失败容忍由上层处理。
        val request = Base64.getEncoder()
            .encodeToString("action=block&do=sign".toByteArray(Charsets.UTF_8))
        guard {
            client.post(
                "https://app.wenku8.com/api.php",
                mapOf(
                    "request" to request,
                    "appver" to "1.21",
                    "timestamp" to (System.currentTimeMillis() / 1000).toString(),
                ),
            )
        }
    }

    // ===================== 浏览 =====================

    override suspend fun homeIndex(): Result<List<HomeBlock>> = runCatching {
        val html = cached("/index.php?charset=gbk", TTL_INDEX)
        parseIndex(html)
    }

    override suspend fun tagGroups(): Result<List<TagGroup>> = runCatching {
        val html = cached("/modules/article/tags.php?charset=gbk", TTL_DEFAULT)
        parseTags(html)
    }

    override suspend fun tagPage(tag: String, sort: Int, page: Int): Result<NovelPage> =
        runCatching {
            val html = cached(
                "/modules/article/tags.php?t=${Wenku8Client.gbkEncode(tag)}&v=$sort&page=$page&charset=gbk",
                TTL_DEFAULT,
            )
            parseBookPage(html)
        }

    override suspend fun toplist(sort: String, page: Int): Result<NovelPage> = runCatching {
        val html = cached(
            "/modules/article/toplist.php?sort=$sort&page=$page&charset=gbk",
            TTL_DEFAULT,
        )
        parseBookPage(html)
    }

    override suspend fun articleList(page: Int): Result<NovelPage> = runCatching {
        val html = cached(
            "/modules/article/articlelist.php?fullflag=1&page=$page&charset=gbk",
            TTL_DEFAULT,
        )
        parseBookPage(html)
    }

    override suspend fun search(
        searchType: String,
        keyword: String,
        page: Int,
    ): Result<NovelPage> = runCatching {
        val html = fetch(
            "/modules/article/search.php?searchtype=$searchType" +
                "&searchkey=${Wenku8Client.gbkEncode(keyword)}&page=$page&charset=gbk"
        )
        parseBookPage(html)
    }

    override suspend fun novelInfo(aid: Int): Result<NovelInfo> = runCatching {
        val html = cached("/book/$aid.htm", TTL_DEFAULT)
        parseNovelInfo(aid, html)
    }

    override suspend fun novelToc(aid: Int): Result<List<Volume>> = runCatching {
        val html = cached("/novel/${aid / 1000}/$aid/index.htm", TTL_DEFAULT)
        parseToc(html)
    }

    override suspend fun chapterText(aid: Int, cid: Int): Result<String> = runCatching {
        val html = fetch("/novel/${aid / 1000}/$aid/$cid.htm")
        parseChapter(html)
    }

    override suspend fun reviews(aid: Int, page: Int): Result<ReviewPage> = runCatching {
        val html = fetch(
            "/modules/article/reviews.php?aid=$aid&page=$page&charset=gbk"
        )
        parseReviews(html)
    }

    // ===================== 书架 =====================

    override suspend fun bookshelfClasses(): Result<List<BookshelfClass>> = runCatching {
        if (cf != null) cf.ensureWarmed() else client.initSession() // spec：先种 session cookie 提高通过率
        val html = fetch("/modules/article/bookcase.php?charset=gbk")
        Jsoup.parse(html).select("select[name=classlist] option").map { opt ->
            BookshelfClass(
                classId = opt.attr("value").toIntOrNull() ?: 0,
                name = opt.text(),
            )
        }
    }

    override suspend fun bookshelf(classId: Int): Result<BookshelfPage> = runCatching {
        val html = fetch(
            "/modules/article/bookcase.php?classid=$classId&charset=gbk",
            referer = client.apiHost() + "/modules/article/bookcase.php",
        )
        parseBookInCase(html)
    }

    override suspend fun bookshelfAdd(aid: Int): Result<Unit> = runCatching {
        guard {
            client.getResponse(
                "/modules/article/addbookcase.php?bid=$aid&charset=gbk",
                referer = client.apiHost() + "/book/$aid.htm",
            ).use { r ->
                when {
                    Wenku8Client.isLoginRedirect(r, "/modules/article/addbookcase.php") ->
                        throw NeedLoginException("/modules/article/addbookcase.php")
                    r.code in 301..303 -> Unit // 真成功 → 重定向到书架页
                    !r.isSuccessful -> {
                        val text = Wenku8Client.decodeGbk(r.body.bytes())
                        if (Wenku8Client.isCfChallenge(r.code, text)) {
                            throw CfChallengeException(
                                "/modules/article/addbookcase.php",
                                hardBlock = Wenku8Client.isCfHardBlock(r.code, text),
                            )
                        }
                        throw Wenku8HttpException(r.code, "加入书架失败 HTTP ${r.code}")
                    }
                    else -> {
                        val text = Wenku8Client.decodeGbk(r.body.bytes())
                        if (Wenku8Client.isCfChallenge(r.code, text)) {
                            throw CfChallengeException(
                                "/modules/article/addbookcase.php",
                                hardBlock = Wenku8Client.isCfHardBlock(r.code, text),
                            )
                        }
                        if (!text.contains("处理成功") && !text.contains("已经在您的书架")) {
                            val doc = Jsoup.parse(text)
                            val msg = doc.select(".blockcontent").first()?.text()?.trim()
                                ?: text.take(300)
                            throw Wenku8HttpException(200, "加入书架失败：$msg")
                        }
                    }
                }
            }
        }
    }

    override suspend fun bookshelfRemove(bid: Int): Result<Unit> = runCatching {
        guard {
            client.getResponse(
                "/modules/article/bookcase.php?delid=$bid&charset=gbk",
                referer = client.apiHost() + "/modules/article/bookcase.php",
            ).use { r ->
                when {
                    Wenku8Client.isLoginRedirect(r, "/modules/article/bookcase.php") ->
                        throw NeedLoginException("/modules/article/bookcase.php")
                    r.code in 301..303 -> Unit
                    !r.isSuccessful -> {
                        val text = Wenku8Client.decodeGbk(r.body.bytes())
                        if (Wenku8Client.isCfChallenge(r.code, text)) {
                            throw CfChallengeException(
                                "/modules/article/bookcase.php",
                                hardBlock = Wenku8Client.isCfHardBlock(r.code, text),
                            )
                        }
                        throw Wenku8HttpException(r.code, "移出书架失败 HTTP ${r.code}")
                    }
                }
            }
        }
    }

    override suspend fun bookshelfMove(
        bids: List<Int>,
        sourceClassId: Int,
        targetClassId: Int,
    ): Result<Unit> = runCatching {
        val pairs = buildList {
            bids.forEach { add("checkid[]" to it.toString()) }
            add("classlist" to sourceClassId.toString())
            add("checkall" to "checkall")
            add("newclassid" to targetClassId.toString())
            add("classid" to sourceClassId.toString())
        }
        guard {
            client.postMultiValue(
                "/modules/article/bookcase.php",
                pairs,
                referer = client.apiHost() + "/modules/article/bookcase.php",
            ).use { r ->
                when {
                    Wenku8Client.isLoginRedirect(r, "/modules/article/bookcase.php") ->
                        throw NeedLoginException("/modules/article/bookcase.php")
                    r.code in 301..303 || r.isSuccessful -> {
                        // 200 也可能是挑战页/错误页：粗查 body 再判成功
                        val text = Wenku8Client.decodeGbk(r.body.bytes())
                        if (Wenku8Client.isCfChallenge(r.code, text)) {
                            throw CfChallengeException(
                                "/modules/article/bookcase.php",
                                hardBlock = Wenku8Client.isCfHardBlock(r.code, text),
                            )
                        }
                    }
                    else -> throw Wenku8HttpException(r.code, "书架操作失败 HTTP ${r.code}")
                }
            }
        }
    }

    // ===================== 解析（移植 client.rs） =====================

    /** parse_index：`#centers` 前 3 块 + `div.main` skip(5) take(2) 内的 .block。 */
    private fun parseIndex(html: String): List<HomeBlock> {
        val doc = Jsoup.parse(html)
        val blocks = mutableListOf<HomeBlock>()

        // 上半区：#centers 内 .block skip(1) take(3)，条目 .blockcontent>div>div
        doc.select("#centers").first()?.let { center ->
            center.select(".block").drop(1).take(3).forEach { block ->
                val title = block.select(".blocktitle").first()?.text() ?: return@forEach
                val novels = block.select(".blockcontent>div>div").mapNotNull { j ->
                    val a0 = j.select("a").first() ?: return@mapNotNull null
                    val a1 = j.select("a").getOrNull(1) ?: return@mapNotNull null
                    val href = a0.attr("href")
                    NovelCover(
                        aid = href.substringAfterLast('/').removeSuffix(".htm").toIntOrNull()
                            ?: return@mapNotNull null,
                        title = a1.text().trim(),
                        coverUrl = j.select("img").first()?.attr("src").orEmpty(),
                    )
                }
                if (novels.isNotEmpty()) blocks.add(HomeBlock(title, novels))
            }
        }

        // 下半区：div.main skip(5) take(2) 内 .block；跳过 span.txt 首子节点与公告/TG 块
        doc.select("div.main").drop(5).take(2).forEach { main ->
            main.select(".block").forEach { block ->
                val titleEl = block.select(".blocktitle").first() ?: return@forEach
                val first = titleEl.firstChild()
                if (first is Element && first.hasClass("txt")) return@forEach
                val title = titleEl.text().trim()
                if (title == "文库Telegram群组" || title.startsWith("轻小说文库公告")) return@forEach
                val novels = block.select("div>a>img").mapNotNull { img ->
                    val a = img.parent() ?: return@mapNotNull null
                    if (a.tagName() != "a") return@mapNotNull null
                    val href = a.attr("href")
                    NovelCover(
                        aid = href.substringAfterLast('/').removeSuffix(".htm").toIntOrNull()
                            ?: return@mapNotNull null,
                        title = a.attr("title").ifEmpty { a.text().trim() },
                        coverUrl = img.attr("src"),
                    )
                }
                if (novels.isNotEmpty()) blocks.add(HomeBlock(title, novels))
            }
        }
        return blocks
    }

    /** parse_tags：`ul.ultops li` —— innerHtml 以 "Tags：" 结尾=开新组。 */
    private fun parseTags(html: String): List<TagGroup> {
        val doc = Jsoup.parse(html)
        val groups = mutableListOf<TagGroup>()
        var groupName = ""
        var tags = mutableListOf<String>()
        for (ul in doc.select("ul.ultops")) {
            for (li in ul.select("li")) {
                val inner = li.html()
                if (inner.trimEnd().endsWith("Tags：")) {
                    if (groupName.isNotEmpty()) {
                        groups.add(TagGroup(groupName, tags.toList()))
                    }
                    groupName = inner.replace("Tags：", "")
                        .replace("系", "").replace("属性", "").replace("类", "").trim()
                    tags = mutableListOf()
                } else {
                    li.select("a").forEach { tags.add(it.text().trim()) }
                }
            }
        }
        if (groupName.isNotEmpty() && tags.isNotEmpty()) groups.add(TagGroup(groupName, tags))
        return groups
    }

    /** parse_books + parse_page_stats：`table.grid tr td>div` 内 `div>a>img` + `em#pagestats`。 */
    private fun parseBookPage(html: String): NovelPage {
        val doc = Jsoup.parse(html)
        val novels = mutableListOf<NovelCover>()
        for (block in doc.select("table.grid tr td>div")) {
            for (img in block.select("div>a>img")) {
                val a = img.parent() ?: continue
                if (a.tagName() != "a") continue
                val href = a.attr("href")
                val aid = href.substringAfterLast('/').removeSuffix(".htm").toIntOrNull()
                    ?: continue
                novels.add(
                    NovelCover(
                        aid = aid,
                        title = a.attr("title").ifEmpty { a.text().trim() },
                        coverUrl = img.attr("src"),
                    )
                )
            }
        }
        var current = 0
        var max = 0
        doc.select("em#pagestats").first()?.text()?.split("/")?.let { parts ->
            current = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            max = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        }
        return NovelPage(novels, current, max)
    }

    /** parse_reader（改 index.htm）：`table.css tr` → `td.vcss[vid]` 卷行 / `td.ccss>a` 章行，cid 取文件名。 */
    private fun parseToc(html: String): List<Volume> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table.css").first()
            ?: throw Wenku8HttpException(200, "目录页缺少 table.css")
        val volumes = mutableListOf<Volume>()
        var vid = -1
        var vtitle = ""
        var chapters = mutableListOf<TocChapter>()
        fun flush() {
            if (vid >= 0) volumes.add(Volume(vid, vtitle, chapters.toList()))
        }
        for (tr in table.select("tr")) {
            val vtd = tr.select("td.vcss").first()
            if (vtd != null) {
                flush()
                vid = vtd.attr("vid").toIntOrNull() ?: volumes.size + 1
                vtitle = vtd.text().trim()
                chapters = mutableListOf()
            } else {
                for (a in tr.select("td.ccss>a")) {
                    val href = a.attr("href")
                    val cid = href.substringAfterLast('/').removeSuffix(".htm").toIntOrNull()
                        ?: continue
                    chapters.add(TocChapter(cid, a.text().trim()))
                }
            }
        }
        flush()
        return volumes
    }

    /**
     * c_content：`#content` 递归取文本；`ul`=水印跳过；`br`→换行；`nbsp`→空格；
     * `img[src]` → `<!--image-->absURL<!--image-->`（补回线上丢图缺陷）。
     */
    private fun parseChapter(html: String): String {
        val doc = Jsoup.parse(html)
        val content = doc.select("#content").first()
            ?: throw Wenku8HttpException(200, "正文页缺少 #content")
        val sb = StringBuilder()
        extractContent(content, sb)
        return sb.toString().trim()
    }

    private fun extractContent(el: Element, sb: StringBuilder) {
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.text().replace(' ', ' '))
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "ul" -> Unit // 水印块跳过
                        "br" -> sb.append('\n')
                        "img" -> {
                            val src = node.attr("src")
                            if (src.isNotBlank()) {
                                sb.append("<!--image-->").append(node.absUrl("src").ifEmpty { src })
                                    .append("<!--image-->")
                            }
                        }
                        else -> extractContent(node, sb)
                    }
                }
                else -> Unit
            }
        }
    }

    /**
     * parse_novel_info —— 改走 `/book/{aid}.htm`（articleinfo.php 被 CF 拦）。
     *
     * 实测结构（/book/2304.htm）：
     *  - 首表 tr[0] 是 `colspan=5` 的标题行，**内部还嵌套一张子表**（书名 b +
     *    「推一下!」+ 右侧「举报/报错」链接）；tr[1] 才是 5 个 `标签：值` td。
     *    按行序下标取 td 会命中嵌套表的行（作者被取成「[举报/报错]」），
     *    因此元数据一律按键名前缀在 td 文本里找。
     *  - 含封面/标签/简介的信息表以「内容简介」span 为判据定位；其布局是
     *    `标签span → br → 内容span`（或内容 a），值要跨 br 向后找兄弟元素。
     */
    private fun parseNovelInfo(aid: Int, html: String): NovelInfo {
        val doc = Jsoup.parse(html)
        val content = doc.select("#content").first()
            ?: throw Wenku8HttpException(200, "详情页缺少 #content")
        val tables = content.select("table")
        val firstTable = tables.first()
            ?: throw Wenku8HttpException(200, "详情页缺少 table")

        val title = firstTable.select("span b").first()?.text()?.trim()
            ?: firstTable.select("b").first()?.text()?.trim()
            ?: throw Wenku8HttpException(200, "详情页缺少标题")

        // 元数据 td 键名：文库分类/小说作者/文章状态/最后更新/全文长度。
        // 嵌套标题表的 td（书名、[举报/报错]）不以这些前缀开头，天然被过滤。
        fun metaValue(key: String): String =
            firstTable.select("td").firstOrNull {
                it.text().trim().startsWith(key)
            }?.text()?.substringAfter('：')?.trim().orEmpty()
        val library = metaValue("文库分类：")
        val author = metaValue("小说作者：")
        val status = metaValue("文章状态：")
        val finUpdate = metaValue("最后更新：")

        // 信息表 = 含「内容简介」span 的那张（封面 img + 作品Tags + 最近章节 + 简介）
        var cover = ""
        var tags: List<String> = emptyList()
        var introduceHtml = ""
        var lastChapterTitle = ""
        var lastChapterCid = 0
        val infoTable = tables.firstOrNull { t ->
            t.select("span").any { it.text().startsWith("内容简介") }
        }
        if (infoTable != null) {
            cover = infoTable.select("img").first()?.attr("src").orEmpty()
            for (span in infoTable.select("span")) {
                val text = span.text()
                when {
                    text.startsWith("作品Tags") -> {
                        tags = text.substringAfter('：')
                            .split(' ', '　')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }
                    text.startsWith("最近章节") -> {
                        val a = span.nextElementSiblings()
                            .select("a[href*=\"/novel/\"]").first()
                            ?: span.parent()?.select("a[href*=\"/novel/\"]")?.first()
                        if (a != null) {
                            lastChapterTitle = a.text().trim()
                            lastChapterCid = a.attr("href")
                                .substringAfterLast('/').removeSuffix(".htm")
                                .toIntOrNull() ?: 0
                        }
                    }
                    text.startsWith("内容简介") -> {
                        introduceHtml = span.nextElementSiblings()
                            .firstOrNull { it.tagName() == "span" }?.html()
                            ?: span.parent()?.select("span")?.last()?.html().orEmpty()
                    }
                }
            }
        }
        if (cover.isEmpty()) {
            cover = "https://img.wenku8.com/image/${aid / 1000}/$aid/${aid}s.jpg"
        }
        return NovelInfo(
            aid = aid,
            title = title,
            author = author,
            status = status,
            finUpdate = finUpdate,
            coverUrl = cover,
            introduceHtml = introduceHtml,
            tags = tags,
            isAnimated = false, // /book 页无动画化标记；articleinfo 通道被 CF 拦
            lastChapterTitle = lastChapterTitle,
            lastChapterCid = lastChapterCid,
            libraryCategory = library,
        )
    }

    /** parse_book_in_case：`td.odd>input[type=checkbox]` 行 → 书名/作者/最近阅读各 a。 */
    private fun parseBookInCase(html: String): BookshelfPage {
        val doc = Jsoup.parse(html)
        val items = mutableListOf<BookshelfItem>()
        for (checkbox in doc.select("td.odd>input[type=checkbox]")) {
            val td0 = checkbox.parent() ?: continue
            val nameTd = td0.nextElementSibling() ?: continue
            val authorTd = nameTd.nextElementSibling() ?: continue
            val chapterTd = authorTd.nextElementSibling() ?: continue

            val nameA = nameTd.select("a").first() ?: continue
            val href = nameA.attr("href") // readbookcase.php?aid=..&bid=..
            val q = href.substringAfter('?', "")
            val params = q.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
            }.toMap()
            val aid = params["aid"]?.toIntOrNull() ?: continue
            val bid = params["bid"]?.toIntOrNull()
                ?: checkbox.attr("value").toIntOrNull() ?: 0
            val chapterA = chapterTd.select("a").first()
            val cid = chapterA?.attr("href")
                ?.let { Regex("cid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            items.add(
                BookshelfItem(
                    aid = aid,
                    bid = bid,
                    title = nameA.text().trim(),
                    author = authorTd.select("a").first()?.text()?.trim()
                        ?: authorTd.text().trim(),
                    coverUrl = "https://img.wenku8.com/image/${aid / 1000}/$aid/${aid}s.jpg",
                    lastReadCid = cid,
                    lastReadChapterName = chapterA?.text()?.trim().orEmpty(),
                )
            )
        }
        val tip = Regex("您的书架可收藏 \\d+ 本，已收藏 \\d+ 本").find(html)?.value
        return BookshelfPage(items, tip)
    }

    /** parse_reviews：`#content table.grid` 首表 tr skip(2)，td[0]=标题 a、td[1]=回复数、td[2]=作者 a、td[3]=时间。 */
    private fun parseReviews(html: String): ReviewPage {
        val doc = Jsoup.parse(html)
        val reviews = mutableListOf<Review>()
        val table = doc.select("#content table.grid").first()
        if (table != null) {
            for (tr in table.select("tr").drop(2)) {
                val tds = tr.select("td")
                if (tds.size < 4) continue
                val titleA = tds[0].select("a").first()
                val rid = titleA?.attr("href")?.substringAfterLast('=')?.toIntOrNull() ?: 0
                val replyCount = Regex("(\\d+)/").find(tds[1].html())
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val userA = tds[2].select("a").first()
                reviews.add(
                    Review(
                        rid = rid,
                        content = titleA?.html()?.trim().orEmpty(),
                        replyCount = replyCount,
                        uid = userA?.attr("href")?.substringAfterLast('=')?.toIntOrNull() ?: 0,
                        userName = userA?.html()?.trim().orEmpty(),
                        time = tds[3].html().replace("<!---->", "").trim(),
                    )
                )
            }
        }
        var current = 0
        var max = 0
        doc.select("em#pagestats").first()?.text()?.split("/")?.let { parts ->
            current = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            max = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        }
        return ReviewPage(reviews, current, max)
    }

    /** parse_userdetail：`tr[align=left]` 内 `td.odd`=键、`td.even`=值。 */
    private fun parseUserDetail(html: String): Map<String, String> {
        val doc = Jsoup.parse(html)
        val map = linkedMapOf<String, String>()
        for (tr in doc.select("tr[align=left]")) {
            val key = tr.select("td.odd").first()?.text()?.trim()
            val value = tr.select("td.even").first()?.text()?.trim()
            if (!key.isNullOrEmpty() && value != null) map[key] = value
        }
        // 兜底：有的模板 td 无 odd/even，退化为相邻两列键值
        if (map.isEmpty()) {
            for (tr in doc.select("#content tr")) {
                val tds = tr.select("td")
                if (tds.size >= 2) map[tds[0].text().trim()] = tds[1].text().trim()
            }
        }
        return map
    }
}
