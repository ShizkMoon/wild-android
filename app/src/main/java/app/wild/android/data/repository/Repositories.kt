package app.wild.android.data.repository

import app.wild.android.data.local.ReadingHistoryDao
import app.wild.android.data.local.ReadingHistoryEntity
import app.wild.android.data.local.SearchHistoryDao
import app.wild.android.data.local.SearchHistoryEntity
import app.wild.android.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow

/**
 * 书架/历史/搜索历史等本地状态的唯一数据源（单一数据源原则）。
 * 网络数据（书架远程列表、章节正文等）经 Repository 接口 + [app.wild.android.data.remote.Wenku8DataSource]
 * 在 Stage 4 接通；骨架期只落本地持久化面。
 */
class LibraryRepository(
    private val historyDao: ReadingHistoryDao,
    private val searchDao: SearchHistoryDao,
) {
    val readingHistory: Flow<List<ReadingHistoryEntity>> = historyDao.observeAll()
    val searchHistory: Flow<List<SearchHistoryEntity>> = searchDao.observeRecent()

    suspend fun recordReading(entry: ReadingHistoryEntity) {
        historyDao.upsert(entry)
        historyDao.trimTo(limit = 100)
    }

    suspend fun recordSearch(type: String, key: String) {
        searchDao.upsert(SearchHistoryEntity(type, key, System.currentTimeMillis()))
        searchDao.trimTo(limit = 100)
    }

    suspend fun clearHistory() = historyDao.clearAll()
    suspend fun clearSearchHistory() = searchDao.clearAll()
}

/** 登录态/会话（cookie 表驱动，spec F3）。 */
class SessionRepository(
    private val db: app.wild.android.data.local.WildDatabase,
) {
    suspend fun isLoggedIn(): Boolean = db.cookieDao().isLoggedIn()
    suspend fun signOut() = db.cookieDao().clearAll()
}
