package app.wild.android.data.remote

/**
 * Wenku8 远程数据源接口 —— 混合数据通道的边界（spec §3.1 / §4 决策 A）。
 *
 * 列表/书架/详情等继续走 `https://www.wenku8.net` 桌面网页 HTML 抓取（复刻现状）；
 * 章节正文优先走官方客户端 XML API（`app.wenku8.com` / `wenku8-relay`，`do=text`），
 * 因为该通道保留 `<!--image-->URL<!--image-->` 插图标记——原 App 抓网页丢图是线上
 * 缺陷（spec §4 观察点 1），复刻版经此接口修复。
 *
 * Stage 4 在这里落地两个实现：
 *   - `Wenku8HtmlSource`：OkHttp + Jsoup，GBK 编解码，CF 检测（`Just a moment` 等）。
 *   - `Wenku8XmlApiSource`：POST `request=Base64(action=…&do=…)`，正文取 `do=text`。
 * Repository 依赖本接口，对通道选型无感知。
 */
interface Wenku8DataSource {

    /** 首页推荐区块（`index.php` → `div.main` 分块）。 */
    suspend fun homeIndex(): Result<List<HomeBlock>>

    /** 分类标签分组（`tags.php` → `ul.ultops`）。 */
    suspend fun tagGroups(): Result<List<TagGroup>>

    /** 分类/标签分页列表（`tags.php?t=…&v=…&page=…`，GBK URL 编码）。 */
    suspend fun tagPage(tag: String, sort: Int, page: Int): Result<NovelPage>

    /** 排行榜（`toplist.php?sort=…&page=…`）。 */
    suspend fun toplist(sort: String, page: Int): Result<NovelPage>

    /** 完结列表（`articlelist.php?fullflag=1&page=…`）。 */
    suspend fun articleList(page: Int): Result<NovelPage>

    /** 搜索（`search.php?searchtype=articlename|author&searchkey=…`）。 */
    suspend fun search(searchType: String, keyword: String, page: Int): Result<NovelPage>

    /** 书籍详情（`articleinfo.php?id=…`）。 */
    suspend fun novelInfo(aid: Int): Result<NovelInfo>

    /** 卷-章节目录（`reader.php?aid=…`）。 */
    suspend fun novelToc(aid: Int): Result<List<Volume>>

    /**
     * 章节正文 —— 决策 A：优先 XML API `do=text`，返回保留 `<!--image-->` 标记的文本；
     * API 通道失败时回退 HTML 抓取并在抓取层补提取 `#content img[src]` 重建标记。
     */
    suspend fun chapterText(aid: Int, cid: Int): Result<String>

    /** 书架分类列表（`bookcase.php` 的 `select[name=classlist]`）。 */
    suspend fun bookshelfClasses(): Result<List<BookshelfClass>>

    /** 某分类下的书架内容 + 容量提示。 */
    suspend fun bookshelf(classId: Int): Result<BookshelfPage>

    /** 加入书架（`addbookcase.php?bid=…`，302 语义 + CF 兜底）。 */
    suspend fun bookshelfAdd(aid: Int): Result<Unit>

    /** 移出书架（`bookcase.php?delid=…`，302 语义 + CF 兜底）。 */
    suspend fun bookshelfRemove(bid: Int): Result<Unit>

    /** 移动/删除（`move_bookcase` POST；删除 = `newclassid = -1`，spec §1.4 F30）。 */
    suspend fun bookshelfMove(bids: List<Int>, targetClassId: Int): Result<Unit>
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
)

data class Volume(val volumeId: Int, val name: String, val chapters: List<Chapter>)
data class Chapter(val cid: Int, val title: String)

data class BookshelfClass(val classId: Int, val name: String)
data class BookshelfPage(val items: List<NovelCover>, val capacityTip: String?)
