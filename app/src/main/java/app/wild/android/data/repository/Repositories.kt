package app.wild.android.data.repository

import app.wild.android.data.local.BookshelfLocalDao
import app.wild.android.data.local.BookshelfLocalEntity
import app.wild.android.data.local.ChapterCacheDao
import app.wild.android.data.local.ChapterCacheEntity
import app.wild.android.data.local.ReadingHistoryDao
import app.wild.android.data.local.ReadingHistoryEntity
import app.wild.android.data.local.SearchHistoryDao
import app.wild.android.data.local.SearchHistoryEntity
import app.wild.android.data.local.SignLogDao
import app.wild.android.data.local.SignLogEntity
import app.wild.android.data.local.WildDatabase
import app.wild.android.data.local.DownloadDao
import app.wild.android.data.local.DownloadChapterEntity
import app.wild.android.data.local.NovelDownloadEntity
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.remote.Wenku8Client
import app.wild.android.data.remote.Wenku8DataSource
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 书架/历史/搜索历史等本地状态的唯一数据源（单一数据源原则）。
 * 网络数据（书架远程列表、章节正文等）经 Repository 接口 + [app.wild.android.data.remote.Wenku8DataSource]
 * 在 Stage 4 接通；骨架期只落本地持久化面。
 */
class LibraryRepository(
    private val historyDao: ReadingHistoryDao,
    private val searchDao: SearchHistoryDao,
    private val bookshelfLocalDao: BookshelfLocalDao,
    private val webCacheDao: app.wild.android.data.local.WebCacheDao,
    private val chapterCacheDao: ChapterCacheDao,
    private val imageCacheDao: app.wild.android.data.local.ImageCacheDao,
) {
    val readingHistory: Flow<List<ReadingHistoryEntity>> = historyDao.observeAll()
    val searchHistory: Flow<List<SearchHistoryEntity>> = searchDao.observeRecent()

    suspend fun recordReading(entry: ReadingHistoryEntity) {
        historyDao.upsert(entry)
        historyDao.trimTo(limit = 100)
    }

    suspend fun removeReading(entry: ReadingHistoryEntity) = historyDao.delete(entry)

    suspend fun recordSearch(type: String, key: String) {
        searchDao.upsert(SearchHistoryEntity(type, key, System.currentTimeMillis()))
        searchDao.trimTo(limit = 100)
    }

    suspend fun clearHistory() = historyDao.clearAll()
    suspend fun clearSearchHistory() = searchDao.clearAll()

    // ---- 本地「已在书架」标记（详情页书签两态；远程操作成功后同步更新） ----

    suspend fun inLocalShelf(aid: Int): Boolean = bookshelfLocalDao.contains(aid)
    suspend fun markShelf(aid: Int) =
        bookshelfLocalDao.add(BookshelfLocalEntity(aid, System.currentTimeMillis()))
    suspend fun unmarkShelf(aid: Int) = bookshelfLocalDao.remove(aid)

    /** 清缓存 = 接口缓存 + 章节缓存 + 图片索引（不清 cookie/历史/下载文件）。 */
    suspend fun clearCaches() {
        webCacheDao.clearAll()
        imageCacheDao.evictBefore(Long.MAX_VALUE)
        chapterCacheDao.evictBefore(Long.MAX_VALUE)
    }
}

/** 登录态/会话（cookie 表驱动，spec F3）。 */
class SessionRepository(
    private val db: WildDatabase,
    private val client: Wenku8Client,
    private val source: Wenku8DataSource,
    private val signLogDao: SignLogDao,
    private val cfSession: app.wild.android.data.remote.CfSession,
) {
    suspend fun isLoggedIn(): Boolean = db.cookieDao().isLoggedIn()

    suspend fun login(username: String, password: String, checkcode: String): Result<Unit> =
        source.login(username, password, checkcode)

    suspend fun checkcodeImage(): Result<ByteArray> = source.checkcodeImage()

    suspend fun userDetail(): Result<Map<String, String>> = source.userDetail()

    suspend fun signedToday(): Boolean {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return signLogDao.signedToday(day)
    }

    /**
     * 每日签到：本地判重（一天一条）→ 调 API。
     * 注意：wenku8 官方 API 已停服（api.php 恒回 "0"），签到对站点已无实际效果；
     * 返回原文响应供 UI 展示，非空且非 "0" 视为成功才写判重。
     */
    suspend fun sign(): Result<String> {
        if (signedToday()) return Result.success("今天已签到")
        return source.sign().map { resp ->
            if (resp.isNotBlank() && resp.trim() != "0") {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                signLogDao.record(SignLogEntity(day))
            }
            resp.ifBlank { "签到接口无响应（站点 API 已停服）" }
        }
    }

    /** 退出登录 = 清空 cookie（含 cf_clearance 与 WebView 侧；本地历史/下载不动）。 */
    suspend fun signOut() {
        client.clearCookies()
        cfSession.reset()
    }
}
