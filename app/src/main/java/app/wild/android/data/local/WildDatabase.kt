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
        DownloadChapterEntity::class,
        BookshelfLocalEntity::class,
    ],
    version = 3,
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
    abstract fun bookshelfLocalDao(): BookshelfLocalDao

    companion object {
        /** v2→v3：cookie 表补 `path`/`hostOnly`（保留用户数据，不走 destructive）。 */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cookie ADD COLUMN path TEXT NOT NULL DEFAULT '/'")
                db.execSQL("ALTER TABLE cookie ADD COLUMN hostOnly INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): WildDatabase =
            Room.databaseBuilder(context, WildDatabase::class.java, "wild.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
