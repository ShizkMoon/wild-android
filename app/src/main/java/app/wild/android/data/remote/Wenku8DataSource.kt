package app.wild.android.data.remote

/**
 * Wenku8 远程数据源接口 —— 数据通道边界（spec §3.1 / §4 决策 A 修订）。
 *
 * 实测结论（Stage 4 探站）：官方 XML API（`app.wenku8.com/api.php`、
 * `wenku8-relay.mewx.org`）已随站点关闭失效——一切请求只回 `0`/`Bad request`。
 * 因此全部数据统一走 `https://www.wenku8.net` 桌面网页 HTML 抓取（GBK）：
 *   - 直连可达：`/`、`/login.php`、`/checkcode.php`、`/book/{aid}.htm`、
 *     `/novel/{aid/1000}/{aid}/{cid}.htm`（正文 + 目录 `index.htm`）、封面/插图域名。
 *   - Cloudflare 拦截：`/modules/article/…`（tags/toplist/articlelist/search/
 *     reviews/bookcase/userdetail/addbookcase）—— 命中时抛 [CfChallengeException]，
 *     由 `CfSession` 状态机处理：隐藏 WebView 解 `cf_clearance` → 透明重试；
 *     解不动/硬阻断 → 可见验证页（NeedsUser）放行后重试。
 *   - 插图：章节 HTML 的 `#content img[src]` 在抓取层重建 `<!--image-->URL<!--image-->`。
 * Repository 依赖本接口，对通道选型无感知。
 */
interface Wenku8DataSource {

    /** 预热会话：GET `/` + `/login.php` 种 session/clearance cookie。 */
    suspend fun initSession(): Result<Unit>

    /** 登录（POST `/login.php`；checkcode 服务端不校验也照发）。 */
    suspend fun login(username: String, password: String, checkcode: String): Result<Unit>

    /** 验证码图片 PNG 字节（`/checkcode.php`；失败容忍，UI 可点按刷新）。 */
    suspend fun checkcodeImage(): Result<ByteArray>

    /** 登录态账户详情（`userdetail.php`，键值对）。 */
    suspend fun userDetail(): Result<Map<String, String>>

    /** 每日签到（XML API `action=block&do=sign`；API 已死，保留失败容忍）。 */
    suspend fun sign(): Result<String>

    /** 首页推荐区块（`index.php` → `#centers` + `div.main` 分块）。 */
    suspend fun homeIndex(): Result<List<HomeBlock>>

    /** 分类标签分组（`tags.php` → `ul.ultops`）。 */
    suspend fun tagGroups(): Result<List<TagGroup>>

    /** 分类/标签分页列表（`tags.php?t=…&v=…&page=…`；v: 0更新/1热门/2完结/3动画化）。 */
    suspend fun tagPage(tag: String, sort: Int, page: Int): Result<NovelPage>

    /** 排行榜（`toplist.php?sort=…&page=…`）。 */
    suspend fun toplist(sort: String, page: Int): Result<NovelPage>

    /** 完结列表（`articlelist.php?fullflag=1&page=…`；匿名访问被重定向到登录页）。 */
    suspend fun articleList(page: Int): Result<NovelPage>

    /** 搜索（`search.php?searchtype=articlename|author&searchkey=…`，GBK URL 编码）。 */
    suspend fun search(searchType: String, keyword: String, page: Int): Result<NovelPage>

    /** 书籍详情（`/book/{aid}.htm` —— 自含详情且不走 CF，比 `articleinfo.php` 可靠）。 */
    suspend fun novelInfo(aid: Int): Result<NovelInfo>

    /** 卷-章节目录（`/novel/{aid/1000}/{aid}/index.htm`，cid 取 `{cid}.htm` 文件名）。 */
    suspend fun novelToc(aid: Int): Result<List<Volume>>

    /** 章节正文：`#content` 文本 + `#content img[src]` → `<!--image-->` 标记。 */
    suspend fun chapterText(aid: Int, cid: Int): Result<String>

    /** 书评分页（`reviews.php?aid=…&page=…`）。 */
    suspend fun reviews(aid: Int, page: Int): Result<ReviewPage>

    /** 书架分类列表（`bookcase.php` 的 `select[name=classlist]`；需登录 + clearance）。 */
    suspend fun bookshelfClasses(): Result<List<BookshelfClass>>

    /** 某分类下的书架内容 + 容量提示（条目含 aid/bid/最近阅读章节）。 */
    suspend fun bookshelf(classId: Int): Result<BookshelfPage>

    /** 加入书架（`addbookcase.php?bid=…`，302=成功语义）。 */
    suspend fun bookshelfAdd(aid: Int): Result<Unit>

    /** 移出书架（`bookcase.php?delid={bid}`，302=成功语义）。 */
    suspend fun bookshelfRemove(bid: Int): Result<Unit>

    /**
     * 批量移动/删除（POST `bookcase.php`，`checkid[]`×N + `classlist`=源分类 +
     * `newclassid`=目标；删除 = `newclassid = -1`，spec §1.4 F30）。
     */
    suspend fun bookshelfMove(bids: List<Int>, sourceClassId: Int, targetClassId: Int): Result<Unit>
}

// ---- DTO（层间模型，与 UI/DB 解耦）----

data class HomeBlock(val title: String, val novels: List<NovelCover>)
data class TagGroup(val name: String, val tags: List<String>)
data class NovelCover(val aid: Int, val title: String, val coverUrl: String)
data class NovelPage(val items: List<NovelCover>, val currentPage: Int, val maxPage: Int)

data class NovelInfo(
    val aid: Int,
    val title: String,
    val author: String,
    val status: String,
    val finUpdate: String,
    val coverUrl: String,
    val introduceHtml: String,
    val tags: List<String>,
    val isAnimated: Boolean,
    /** 「最近章节」链接（/book 页独有；目录入口的直达提示）。 */
    val lastChapterTitle: String = "",
    val lastChapterCid: Int = 0,
    val libraryCategory: String = "",
)

data class Volume(val volumeId: Int, val name: String, val chapters: List<TocChapter>)
data class TocChapter(val cid: Int, val title: String)

data class BookshelfClass(val classId: Int, val name: String)

/** 书架条目：多选/移动/删除都认 bid；cid/chapterName 是服务器记录的最近阅读位置。 */
data class BookshelfItem(
    val aid: Int,
    val bid: Int,
    val title: String,
    val author: String,
    val coverUrl: String,
    val lastReadCid: Int,
    val lastReadChapterName: String,
)

data class BookshelfPage(val items: List<BookshelfItem>, val capacityTip: String?)

data class Review(
    val rid: Int,
    val content: String,
    val replyCount: Int,
    val uid: Int,
    val userName: String,
    val time: String,
)
data class ReviewPage(val items: List<Review>, val currentPage: Int, val maxPage: Int)
