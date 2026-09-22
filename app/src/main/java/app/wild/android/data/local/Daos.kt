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

    @Query("SELECT * FROM cookie WHERE domain = :domain")
    suspend fun forDomain(domain: String): List<CookieEntity>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NovelDownloadEntity)

    @Query("UPDATE novel_download SET status = :status, updatedAtMs = :nowMs WHERE aid = :aid")
    suspend fun setStatus(aid: Int, status: Int, nowMs: Long)
}
