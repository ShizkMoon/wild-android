package app.wild.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CookieEntity::class,
        WebCacheEntity::class,
        ChapterCacheEntity::class,
        ImageCacheEntity::class,
        ReadingHistoryEntity::class,
        SearchHistoryEntity::class,
        SignLogEntity::class,
        NovelDownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class WildDatabase : RoomDatabase() {
    abstract fun cookieDao(): CookieDao
    abstract fun webCacheDao(): WebCacheDao
    abstract fun chapterCacheDao(): ChapterCacheDao
    abstract fun imageCacheDao(): ImageCacheDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun signLogDao(): SignLogDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun build(context: Context): WildDatabase =
            Room.databaseBuilder(context, WildDatabase::class.java, "wild.db").build()
    }
}
