package app.wild.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wild.android.data.download.DownloadEngine
import app.wild.android.data.local.ReadingHistoryEntity
import app.wild.android.data.local.SearchHistoryEntity
import app.wild.android.data.remote.BookshelfClass
import app.wild.android.data.remote.BookshelfItem
import app.wild.android.data.remote.BookshelfPage
import app.wild.android.data.remote.CfChallengeException
import app.wild.android.data.remote.HomeBlock
import app.wild.android.data.remote.NovelCover
import app.wild.android.data.remote.NovelInfo
import app.wild.android.data.remote.NovelPage
import app.wild.android.data.remote.Review
import app.wild.android.data.remote.TagGroup
import app.wild.android.data.remote.Volume
import app.wild.android.data.remote.Wenku8DataSource
import app.wild.android.data.remote.Wenku8HttpException
import app.wild.android.data.repository.LibraryRepository
import app.wild.android.data.repository.ReaderContentSource
import app.wild.android.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** 统一错误文案（spec 附录 F.1 的错误块由 UI 呈现）。 */
fun friendlyError(t: Throwable): String = when (t) {
    is CfChallengeException ->
        if (t.hardBlock) "站点防护验证未通过（Cloudflare 硬拦截）——打开站点验证手动过一次，或切换网络后重试"
        else "站点防护验证未通过（Cloudflare）——打开站点验证手动过一次，或下拉重试"
    is app.wild.android.data.remote.NeedLoginException -> "该页面需要登录后访问"
    is Wenku8HttpException -> t.message ?: "请求失败（HTTP ${t.code}）"
    is IOException -> "网络连接失败，请检查网络后下拉重试"
    else -> t.message ?: "未知错误"
}

/** 通用异步块状态。 */
data class UiState<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** 通用分页列表状态（排行/分类/完结/搜索/书评共用无限滚动模型）。 */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val maxPage: Int = Int.MAX_VALUE,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
)

/** 分页加载助手：page=0 首刷，>0 追加；错误保留已有数据。 */
internal suspend fun <T> loadPage(
    state: MutableStateFlow<Paged<T>>,
    page: Int,
    fetch: suspend (Int) -> Result<NovelPage>,
    map: (NovelCover) -> T,
) {
    val cur = state.value
    if (page == 0) state.value = cur.copy(loading = true, error = null)
    else state.value = cur.copy(loadingMore = true, error = null)
    fetch(page + 1).fold(
        onSuccess = { p ->
            val items = if (page == 0) p.items.map(map) else cur.items + p.items.map(map)
            state.value = Paged(
                items = items,
                page = p.currentPage,
                maxPage = p.maxPage,
                endReached = p.currentPage >= p.maxPage || p.items.isEmpty(),
            )
        },
        onFailure = { e ->
            state.value = cur.copy(loading = false, loadingMore = false, error = friendlyError(e))
        },
    )
}

// ===================== 首页四 tab =====================

class HomeViewModel(private val source: Wenku8DataSource) : ViewModel() {

    val recommend = MutableStateFlow(UiState<List<HomeBlock>>())
    val tagGroups = MutableStateFlow(UiState<List<TagGroup>>())
    val category = MutableStateFlow(Paged<NovelCover>())
    val toplist = MutableStateFlow(Paged<NovelCover>())
    val finished = MutableStateFlow(Paged<NovelCover>())

    var categoryTag: String? = null
        private set
    var categorySort: Int = 0
        private set
    var toplistSort: String = "lastupdate"
        private set

    private var recommendLoaded = false
    private var tagsLoaded = false
    private var finishedLoaded = false

    fun loadRecommend(force: Boolean = false) {
        if (!force && recommendLoaded) return
        recommendLoaded = true
        viewModelScope.launch {
            recommend.value = UiState(loading = true)
            source.homeIndex().fold(
                onSuccess = { recommend.value = UiState(data = it, loading = false) },
                onFailure = { recommend.value = UiState(loading = false, error = friendlyError(it)) },
            )
        }
    }

    fun loadTagGroups() {
        if (tagsLoaded) return
        tagsLoaded = true
        viewModelScope.launch {
            tagGroups.value = UiState(loading = true)
            source.tagGroups().fold(
                onSuccess = { tagGroups.value = UiState(data = it, loading = false) },
                onFailure = { tagGroups.value = UiState(loading = false, error = friendlyError(it)) },
            )
        }
    }

    fun selectCategory(tag: String?, sort: Int) {
        categoryTag = tag
        categorySort = sort
        if (tag == null) {
            category.value = Paged()
            return
        }
        viewModelScope.launch {
            loadPage(category, 0, { p -> source.tagPage(tag, sort, p) }, { it })
        }
    }

    fun loadMoreCategory() {
        val t = categoryTag ?: return
        val cur = category.value
        if (cur.loadingMore || cur.endReached) return
        viewModelScope.launch {
            loadPage(category, cur.page, { p -> source.tagPage(t, categorySort, p) }, { it })
        }
    }

    fun selectToplist(sort: String) {
        if (sort == toplistSort && toplist.value.items.isNotEmpty()) return
        toplistSort = sort
        viewModelScope.launch {
            loadPage(toplist, 0, { p -> source.toplist(sort, p) }, { it })
        }
    }

    fun loadMoreToplist() {
        val cur = toplist.value
        if (cur.loadingMore || cur.endReached) return
        viewModelScope.launch {
            loadPage(toplist, cur.page, { p -> source.toplist(toplistSort, p) }, { it })
        }
    }

    fun loadFinished() {
        if (finishedLoaded) return
        finishedLoaded = true
        viewModelScope.launch {
            loadPage(finished, 0, { p -> source.articleList(p) }, { it })
        }
    }

    fun loadMoreFinished() {
        val cur = finished.value
        if (cur.loadingMore || cur.endReached) return
        viewModelScope.launch {
            loadPage(finished, cur.page, { p -> source.articleList(p) }, { it })
        }
    }
}

// ===================== 小说详情 =====================

data class NovelDetailState(
    val info: NovelInfo? = null,
    val volumes: List<Volume> = emptyList(),
    val history: ReadingHistoryEntity? = null,
    val inBookshelf: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val shelfBusy: Boolean = false,
)

class NovelInfoViewModel(
    private val aid: Int,
    private val source: Wenku8DataSource,
    private val library: LibraryRepository,
) : ViewModel() {

    val state = MutableStateFlow(NovelDetailState())

    fun load() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null)
            val infoR = source.novelInfo(aid)
            val tocR = source.novelToc(aid)
            val history = runCatching {
                library.readingHistory.first().firstOrNull { it.novelId == aid }
            }.getOrNull()
            val inShelf = runCatching { library.inLocalShelf(aid) }.getOrDefault(false)
            if (infoR.isFailure) {
                state.value = state.value.copy(
                    loading = false,
                    error = friendlyError(infoR.exceptionOrNull()!!),
                )
            } else {
                state.value = NovelDetailState(
                    info = infoR.getOrNull(),
                    volumes = tocR.getOrDefault(emptyList()),
                    history = history,
                    inBookshelf = inShelf,
                    loading = false,
                    error = tocR.exceptionOrNull()?.let { friendlyError(it) },
                )
            }
        }
    }

    /** 书签两态：加入=addbookcase；移出=扫书架分类找 bid 再 delid。 */
    fun toggleBookshelf(onMessage: (String) -> Unit) {
        val st = state.value
        if (st.shelfBusy) return
        viewModelScope.launch {
            state.value = st.copy(shelfBusy = true)
            if (!st.inBookshelf) {
                source.bookshelfAdd(aid).fold(
                    onSuccess = {
                        library.markShelf(aid)
                        state.value = state.value.copy(inBookshelf = true, shelfBusy = false)
                        onMessage("已加入书架")
                    },
                    onFailure = {
                        state.value = state.value.copy(shelfBusy = false)
                        onMessage(friendlyError(it))
                    },
                )
            } else {
                val bid = findBid(aid)
                if (bid == null) {
                    library.unmarkShelf(aid) // 远程找不到，仅清本地标记
                    state.value = state.value.copy(inBookshelf = false, shelfBusy = false)
                    onMessage("已从书架移除")
                } else {
                    source.bookshelfRemove(bid).fold(
                        onSuccess = {
                            library.unmarkShelf(aid)
                            state.value = state.value.copy(inBookshelf = false, shelfBusy = false)
                            onMessage("已从书架移除")
                        },
                        onFailure = {
                            state.value = state.value.copy(shelfBusy = false)
                            onMessage(friendlyError(it))
                        },
                    )
                }
            }
        }
    }

    private suspend fun findBid(aid: Int): Int? {
        val classes = source.bookshelfClasses().getOrNull() ?: return null
        for (c in classes) {
            val page = source.bookshelf(c.classId).getOrNull() ?: continue
            page.items.firstOrNull { it.aid == aid }?.let { return it.bid }
        }
        return null
    }

    /** 「继续阅读」目标 cid：历史记的 chapterId；无记录回退首章。 */
    fun continueCid(): Int {
        val st = state.value
        st.history?.chapterId?.takeIf { it > 0 }?.let { return it }
        return st.volumes.firstOrNull()?.chapters?.firstOrNull()?.cid ?: 0
    }
}

// ===================== 搜索 =====================

class SearchViewModel(
    private val source: Wenku8DataSource,
    private val library: LibraryRepository,
) : ViewModel() {

    val results = MutableStateFlow(Paged<NovelCover>())
    val history: StateFlow<List<SearchHistoryEntity>> =
        library.searchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var queryType: String = "articlename"
        private set
    var queryKey: String = ""
        private set

    fun search(type: String, key: String) {
        if (key.isBlank()) {
            results.value = Paged()
            return
        }
        queryType = type
        queryKey = key
        viewModelScope.launch {
            library.recordSearch(type, key)
            loadPage(results, 0, { p -> source.search(type, key, p) }, { it })
        }
    }

    fun loadMore() {
        val cur = results.value
        if (cur.loadingMore || cur.endReached || queryKey.isBlank()) return
        viewModelScope.launch {
            loadPage(results, cur.page, { p -> source.search(queryType, queryKey, p) }, { it })
        }
    }

    /** P2：搜索历史管理入口——清空。 */
    fun clearHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }
}

// ===================== 书架 =====================

data class BookshelfState(
    val classes: List<BookshelfClass> = emptyList(),
    val classIndex: Int = 0,
    val page: BookshelfPage? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val needLogin: Boolean = false,
)

class BookshelfViewModel(
    private val source: Wenku8DataSource,
    private val session: SessionRepository,
) : ViewModel() {

    val state = MutableStateFlow(BookshelfState())

    fun load() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null)
            if (!session.isLoggedIn()) {
                state.value = BookshelfState(loading = false, needLogin = true)
                return@launch
            }
            source.bookshelfClasses().fold(
                onSuccess = { classes ->
                    if (classes.isEmpty()) {
                        state.value = BookshelfState(loading = false, error = "未能读取书架分类")
                    } else {
                        state.value = state.value.copy(classes = classes)
                        loadClass(0)
                    }
                },
                onFailure = {
                    state.value = state.value.copy(loading = false, error = friendlyError(it))
                },
            )
        }
    }

    fun loadClass(index: Int) {
        val classes = state.value.classes
        val c = classes.getOrNull(index) ?: return
        viewModelScope.launch {
            state.value = state.value.copy(classIndex = index, loading = true, error = null)
            source.bookshelf(c.classId).fold(
                onSuccess = { page ->
                    state.value = state.value.copy(page = page, loading = false)
                },
                onFailure = {
                    state.value = state.value.copy(loading = false, error = friendlyError(it))
                },
            )
        }
    }

    /** 多选移动（target=-1 = 删除）。 */
    fun moveSelected(bids: List<Int>, targetClassId: Int, onDone: (String) -> Unit) {
        val sourceId = state.value.classes.getOrNull(state.value.classIndex)?.classId ?: return
        viewModelScope.launch {
            source.bookshelfMove(bids, sourceId, targetClassId).fold(
                onSuccess = {
                    onDone(if (targetClassId < 0) "已删除" else "已移动")
                    loadClass(state.value.classIndex)
                },
                onFailure = { onDone(friendlyError(it)) },
            )
        }
    }
}

// ===================== 历史 =====================

class HistoryViewModel(private val library: LibraryRepository) : ViewModel() {
    val history: StateFlow<List<ReadingHistoryEntity>> =
        library.readingHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** P0-2：下拉刷新绑定真实重查（历史为本地库，重查 DAO 拿到最新快照）。 */
    val refreshing = MutableStateFlow(false)

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            runCatching { library.readingHistory.first() }
            refreshing.value = false
        }
    }

    fun delete(entry: ReadingHistoryEntity) {
        viewModelScope.launch { library.removeReading(entry) }
    }

    fun clearAll() {
        viewModelScope.launch { library.clearHistory() }
    }
}

// ===================== 书评 =====================

class ReviewsViewModel(
    private val aid: Int,
    private val source: Wenku8DataSource,
) : ViewModel() {

    val reviews = MutableStateFlow(Paged<Review>())
    val title = MutableStateFlow("小说")

    fun load() {
        viewModelScope.launch {
            source.novelInfo(aid).onSuccess { title.value = it.title }
            loadReviews(0)
        }
    }

    fun refresh() = loadReviews(0)

    fun loadMore() {
        val cur = reviews.value
        if (cur.loadingMore || cur.endReached) return
        loadReviews(cur.page)
    }

    private fun loadReviews(page: Int) {
        viewModelScope.launch {
            val cur = reviews.value
            if (page == 0) reviews.value = cur.copy(loading = true, error = null)
            else reviews.value = cur.copy(loadingMore = true)
            source.reviews(aid, page + 1).fold(
                onSuccess = { p ->
                    reviews.value = Paged(
                        items = if (page == 0) p.items else cur.items + p.items,
                        page = p.currentPage,
                        maxPage = p.maxPage,
                        endReached = p.currentPage >= p.maxPage || p.items.isEmpty(),
                    )
                },
                onFailure = {
                    reviews.value = cur.copy(
                        loading = false, loadingMore = false, error = friendlyError(it),
                    )
                },
            )
        }
    }
}

// ===================== 下载 =====================

class DownloadSelectViewModel(
    private val aid: Int,
    private val source: Wenku8DataSource,
    private val engine: DownloadEngine,
) : ViewModel() {

    val info = MutableStateFlow<NovelInfo?>(null)
    val volumes = MutableStateFlow<List<Volume>>(emptyList())
    val downloadedCids = MutableStateFlow<Set<Int>>(emptySet())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)

    fun load() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            source.novelInfo(aid).onSuccess { info.value = it }
            val toc = source.novelToc(aid)
            toc.onSuccess { volumes.value = it }
            toc.onFailure { error.value = friendlyError(it) }
            engine // 已下载 cid（含文件存在）
            volumes.value.flatMap { it.chapters }.forEach { ch ->
                if (engine.isChapterDownloaded(aid, ch.cid)) {
                    downloadedCids.value += ch.cid
                }
            }
            loading.value = false
        }
    }

    fun enqueue(cids: Set<Int>, onDone: () -> Unit) {
        val i = info.value ?: return
        viewModelScope.launch {
            engine.enqueue(
                aid = aid,
                novelName = i.title,
                author = i.author,
                coverUrl = i.coverUrl,
                chapters = volumes.value.flatMap { it.chapters },
                cids = cids,
            )
            onDone()
        }
    }
}

class DownloadsViewModel(private val engine: DownloadEngine) : ViewModel() {
    val downloads = engine.downloads.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val running = engine.running
    val current = engine.current

    fun resetAllFailed() {
        viewModelScope.launch { engine.resetAllFailed() }
    }
}

class DownloadDetailViewModel(
    private val aid: Int,
    private val source: Wenku8DataSource,
    private val engine: DownloadEngine,
) : ViewModel() {
    val download = MutableStateFlow<app.wild.android.data.local.NovelDownloadEntity?>(null)
    val info = MutableStateFlow<NovelInfo?>(null)
    val volumes = MutableStateFlow<List<Volume>>(emptyList())
    val chapters = engine.chaptersFlow(aid).stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )

    fun load() {
        viewModelScope.launch {
            engine.downloads.collect { list ->
                download.value = list.firstOrNull { it.aid == aid }
            }
        }
        viewModelScope.launch {
            source.novelInfo(aid).onSuccess { info.value = it }
            source.novelToc(aid).onSuccess { volumes.value = it }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            engine.delete(aid)
            onDone()
        }
    }
}

// ===================== 阅读器 =====================

data class ReaderState(
    val volumes: List<Volume> = emptyList(),
    val currentIndex: Int = 0,
    val content: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val novelName: String = "",
    val author: String = "",
    val coverUrl: String = "",
) {
    val flatChapters: List<Pair<String, app.wild.android.data.remote.TocChapter>>
        get() = volumes.flatMap { v -> v.chapters.map { v.name to it } }
}

class ReaderViewModel(
    private val aid: Int,
    private val initialCid: Int,
    private val source: Wenku8DataSource,
    private val contentSource: ReaderContentSource,
    private val library: LibraryRepository,
    private val downloadDao: app.wild.android.data.local.DownloadDao,
) : ViewModel() {

    val state = MutableStateFlow(ReaderState())
    /** 恢复锚点：历史 progress（累计字数）；阅读器排版完成后跳页用。 */
    val restoreProgress = MutableStateFlow(0)

    fun load() {
        viewModelScope.launch {
            val toc = source.novelToc(aid)
            val info = source.novelInfo(aid).getOrNull()
            val history = runCatching {
                library.readingHistory.first().firstOrNull { it.novelId == aid }
            }.getOrNull()
            toc.fold(
                onSuccess = { volumes ->
                    val flat = volumes.flatMap { v -> v.chapters.map { v.name to it } }
                    var idx = flat.indexOfFirst { it.second.cid == initialCid }
                    var restore = 0
                    if (idx < 0 && history != null) {
                        idx = flat.indexOfFirst { it.second.cid == history.chapterId }
                        if (idx >= 0) restore = history.progress
                    }
                    if (idx < 0) idx = 0
                    state.value = state.value.copy(
                        volumes = volumes,
                        currentIndex = idx,
                        novelName = info?.title ?: history?.novelName ?: "",
                        author = info?.author ?: history?.author ?: "",
                        coverUrl = info?.coverUrl ?: history?.coverUrl ?: "",
                    )
                    restoreProgress.value = restore
                    loadChapter(idx)
                },
                onFailure = { e ->
                    // 离线容错：目录拉不下来时，用已下载章表构造单卷目录（离线可读）
                    val dl = runCatching {
                        downloadDao.chapters(aid).filter { it.status == 1 }
                    }.getOrDefault(emptyList())
                    if (dl.isNotEmpty()) {
                        val volumes = listOf(
                            Volume(0, "已下载章节", dl.map {
                                app.wild.android.data.remote.TocChapter(it.cid, it.chapterTitle)
                            })
                        )
                        val flat = volumes.flatMap { v -> v.chapters.map { v.name to it } }
                        var idx = flat.indexOfFirst { it.second.cid == initialCid }
                        var restore = 0
                        if (idx < 0 && history != null) {
                            idx = flat.indexOfFirst { it.second.cid == history.chapterId }
                            if (idx >= 0) restore = history.progress
                        }
                        if (idx < 0) idx = 0
                        state.value = state.value.copy(
                            volumes = volumes,
                            currentIndex = idx,
                            novelName = info?.title ?: history?.novelName ?: "",
                            author = info?.author ?: history?.author ?: "",
                            coverUrl = info?.coverUrl ?: history?.coverUrl ?: "",
                        )
                        restoreProgress.value = restore
                        loadChapter(idx)
                    } else {
                        state.value = state.value.copy(loading = false, error = friendlyError(e))
                    }
                },
            )
        }
    }

    fun goTo(index: Int) {
        val flat = state.value.flatChapters
        if (index !in flat.indices) return
        state.value = state.value.copy(currentIndex = index)
        loadChapter(index)
    }

    private fun loadChapter(index: Int) {
        val flat = state.value.flatChapters
        val ch = flat.getOrNull(index) ?: return
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, content = null)
            contentSource.chapterText(aid, ch.second.cid).fold(
                onSuccess = { text ->
                    state.value = state.value.copy(content = text, loading = false)
                    record(index, text)
                },
                onFailure = {
                    state.value = state.value.copy(loading = false, error = friendlyError(it))
                },
            )
        }
    }

    /** 进度持久化（spec 决策 C：progress=累计文本字数锚点；progressPage 仅展示）。 */
    fun record(index: Int, content: String, progressAnchor: Int = 0, page: Int = 0) {
        val flat = state.value.flatChapters
        val (vname, ch) = flat.getOrNull(index) ?: return
        val vid = state.value.volumes.indexOfFirst { it.name == vname }.let {
            state.value.volumes.getOrNull(it)?.volumeId ?: 0
        }
        viewModelScope.launch {
            library.recordReading(
                ReadingHistoryEntity(
                    novelId = aid,
                    novelName = state.value.novelName,
                    author = state.value.author,
                    coverUrl = state.value.coverUrl,
                    volumeId = vid,
                    volumeName = vname,
                    chapterId = ch.cid,
                    chapterTitle = ch.title,
                    progress = progressAnchor,
                    progressPage = page,
                    lastReadAtMs = System.currentTimeMillis(),
                )
            )
        }
    }
}

// ===================== 登录 / 启动 / 账户 =====================

class SessionViewModel(
    private val source: Wenku8DataSource,
    private val session: SessionRepository,
    private val client: app.wild.android.data.remote.Wenku8Client,
) : ViewModel() {

    val captcha = MutableStateFlow<ByteArray?>(null)
    val captchaLoading = MutableStateFlow(false)
    val loggingIn = MutableStateFlow(false)

    /** initSession + 登录态分流：true=已登录直接进主页，false=先去登录页。 */
    val initDone = MutableStateFlow<Boolean?>(null)
    val initError = MutableStateFlow<String?>(null)

    fun init() {
        viewModelScope.launch {
            runCatching {
                client.init()
                source.initSession().getOrThrow()
            }.onSuccess {
                initDone.value = session.isLoggedIn()
            }.onFailure {
                // 网络失败不阻塞进 App（浏览类页面可下拉重试）
                initDone.value = session.isLoggedIn()
                initError.value = friendlyError(it)
            }
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            captchaLoading.value = true
            source.checkcodeImage().onSuccess { captcha.value = it }
            captchaLoading.value = false
        }
    }

    fun login(username: String, password: String, checkcode: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            loggingIn.value = true
            val r = session.login(username, password, checkcode)
            loggingIn.value = false
            if (r.isFailure) refreshCaptcha() // 失败后换一张验证码
            onDone(r)
        }
    }
}

class AccountViewModel(
    private val source: Wenku8DataSource,
    private val session: SessionRepository,
) : ViewModel() {

    val detail = MutableStateFlow(UiState<Map<String, String>>())
    val loggedIn = MutableStateFlow(true)
    val signResult = MutableStateFlow<String?>(null)
    val signedToday = MutableStateFlow(false)

    fun load() {
        viewModelScope.launch {
            if (!session.isLoggedIn()) {
                loggedIn.value = false
                detail.value = UiState(loading = false)
                return@launch
            }
            signedToday.value = session.signedToday()
            detail.value = UiState(loading = true)
            source.userDetail().fold(
                onSuccess = { detail.value = UiState(data = it, loading = false) },
                onFailure = { detail.value = UiState(loading = false, error = friendlyError(it)) },
            )
        }
    }

    fun sign() {
        viewModelScope.launch {
            signResult.value = null
            session.sign().fold(
                onSuccess = {
                    signResult.value = it
                    signedToday.value = session.signedToday()
                },
                onFailure = { signResult.value = friendlyError(it) },
            )
        }
    }
}
