package app.wild.android.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CookieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cookie: CookieEntity)

    @Query("SELECT * FROM cookie WHERE domain = :domain OR domain LIKE '%' || :domain")
    suspend fun forDomain(domain: String): List<CookieEntity>

    @Query("SELECT * FROM cookie")
    suspend fun all(): List<CookieEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM cookie WHERE name = 'jieqiUserInfo')")
    suspend fun isLoggedIn(): Boolean

    @Query("DELETE FROM cookie")
    suspend fun clearAll()
}

@Dao
interface WebCacheDao {
    @Query("SELECT * FROM web_cache WHERE `key` = :key")
    suspend fun get(key: String): WebCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: WebCacheEntity)

    @Query("DELETE FROM web_cache")
    suspend fun clearAll()
}

@Dao
interface ChapterCacheDao {
    @Query("SELECT * FROM chapter_cache WHERE aid = :aid AND cid = :cid")
    suspend fun get(aid: Int, cid: Int): ChapterCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: ChapterCacheEntity)

    @Query("DELETE FROM chapter_cache WHERE cachedAtMs < :beforeMs")
    suspend fun evictBefore(beforeMs: Long)
}

@Dao
interface ImageCacheDao {
    @Query("SELECT * FROM image_cache WHERE url = :url")
    suspend fun get(url: String): ImageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: ImageCacheEntity)

    @Query("DELETE FROM image_cache WHERE cachedAtMs < :beforeMs")
    suspend fun evictBefore(beforeMs: Long)
}

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY lastReadAtMs DESC")
    fun observeAll(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history WHERE novelId = :novelId")
    suspend fun get(novelId: Int): ReadingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ReadingHistoryEntity)

    @Delete
    suspend fun delete(entry: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()

    /** 保留最近 [limit] 条，超出删最旧（spec §3.5）。 */
    @Query(
        "DELETE FROM reading_history WHERE novelId NOT IN " +
            "(SELECT novelId FROM reading_history ORDER BY lastReadAtMs DESC LIMIT :limit)"
    )
    suspend fun trimTo(limit: Int = 100)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    /** 保留最近 [limit] 条（spec F13：上限 100，无单条删除）。 */
    @Query(
        "DELETE FROM search_history WHERE (searchType || '|' || searchKey) NOT IN " +
            "(SELECT searchType || '|' || searchKey FROM search_history ORDER BY searchedAtMs DESC LIMIT :limit)"
    )
    suspend fun trimTo(limit: Int = 100)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface SignLogDao {
    @Query("SELECT EXISTS(SELECT 1 FROM sign_log WHERE day = :day)")
    suspend fun signedToday(day: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun record(entry: SignLogEntity)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM novel_download ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<NovelDownloadEntity>>

    @Query("SELECT * FROM novel_download WHERE aid = :aid")
    suspend fun get(aid: Int): NovelDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NovelDownloadEntity)

    @Query("UPDATE novel_download SET status = :status, updatedAtMs = :nowMs WHERE aid = :aid")
    suspend fun setStatus(aid: Int, status: Int, nowMs: Long)

    @Query("UPDATE novel_download SET doneChapters = :done, status = :status, updatedAtMs = :nowMs WHERE aid = :aid")
    suspend fun setProgress(aid: Int, done: Int, status: Int, nowMs: Long)

    @Query("DELETE FROM novel_download WHERE aid = :aid")
    suspend fun delete(aid: Int)

    // ---- 章节队列 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(entry: DownloadChapterEntity)

    @Query("SELECT * FROM download_chapter WHERE aid = :aid")
    suspend fun chapters(aid: Int): List<DownloadChapterEntity>

    @Query("SELECT * FROM download_chapter WHERE aid = :aid")
    fun observeChapters(aid: Int): Flow<List<DownloadChapterEntity>>

    @Query("SELECT * FROM download_chapter WHERE aid = :aid AND status = 0 ORDER BY cid")
    suspend fun pendingChapters(aid: Int): List<DownloadChapterEntity>

    @Query("SELECT DISTINCT aid FROM download_chapter WHERE status = 0")
    suspend fun aidsWithPending(): List<Int>

    @Query("SELECT aid FROM novel_download WHERE status = :status")
    suspend fun aidsWithStatus(status: Int): List<Int>

    @Query("UPDATE download_chapter SET status = :status, attempts = attempts + 1 WHERE aid = :aid AND cid = :cid")
    suspend fun setChapterStatus(aid: Int, cid: Int, status: Int)

    @Query("SELECT cid FROM download_chapter WHERE aid = :aid AND status = 1")
    suspend fun downloadedCids(aid: Int): List<Int>

    @Query("SELECT COUNT(*) FROM download_chapter WHERE aid = :aid AND status = 1")
    suspend fun doneCount(aid: Int): Int

    @Query("UPDATE download_chapter SET status = 0 WHERE aid = :aid AND status = 2")
    suspend fun resetFailed(aid: Int)

    @Query("UPDATE download_chapter SET status = 0, attempts = 0 WHERE status = 2")
    suspend fun resetAllFailed()

    @Query("DELETE FROM download_chapter WHERE aid = :aid")
    suspend fun deleteChapters(aid: Int)
}

@Dao
interface BookshelfLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: BookshelfLocalEntity)

    @Query("DELETE FROM bookshelf_local WHERE aid = :aid")
    suspend fun remove(aid: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookshelf_local WHERE aid = :aid)")
    suspend fun contains(aid: Int): Boolean
}
