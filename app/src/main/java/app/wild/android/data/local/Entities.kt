package app.wild.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 会话 Cookie（登录态 = `jieqiUserInfo` 存在）。spec §3.5。 */
@Entity(tableName = "cookie", primaryKeys = ["domain", "name"])
data class CookieEntity(
    val domain: String,
    val name: String,
    val value: String,
    /** 过期时刻（epoch ms）；0 / Long.MAX_VALUE = 会话期 cookie 不过期。 */
    val expiryEpochMs: Long = 0L,
    /** cookie path（站点基本全是 `/`）。 */
    val path: String = "/",
    /** true=仅原 host；false=站点域名可跨子域。 */
    val hostOnly: Boolean = true,
) {
    /** 是否已过期（0/MAX 视为不过期）。 */
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiryEpochMs > 0L && expiryEpochMs < Long.MAX_VALUE && expiryEpochMs <= nowMs
}

/** 接口缓存：`cache_first(key, ttl)` —— index 10min、其余 1h。 */
@Entity(tableName = "web_cache")
data class WebCacheEntity(
    @PrimaryKey val key: String,
    val contentJson: String,
    val cachedAtMs: Long,
)

/** 章节正文缓存，7 天。 */
@Entity(tableName = "chapter_cache", primaryKeys = ["aid", "cid"])
data class ChapterCacheEntity(
    val aid: Int,
    val cid: Int,
    val content: String,
    val cachedAtMs: Long,
)

/** 图片缓存索引（文件落在 `image_cache/{md5(url)}`），7 天。 */
@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey val url: String,
    val md5: String,
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0L,
    val cachedAtMs: Long,
)

/**
 * 阅读历史 / 进度。
 * 决策 C（spec §4）：恢复锚点用 `progress`（累计文本字数），`progressPage` 只作展示，
 * 不再作为恢复依据——页索引随排版设置漂移。
 */
@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val novelId: Int,
    val novelName: String,
    val author: String,
    val coverUrl: String,
    val volumeId: Int,
    val volumeName: String,
    val chapterId: Int,
    val chapterTitle: String,
    /** 累计文本字数锚点（图片页不计）。 */
    val progress: Int,
    /** 页索引，仅展示；恢复进度一律用 [progress]。 */
    val progressPage: Int,
    val lastReadAtMs: Long,
)

/** 搜索历史，上限 100 条，无单条删除（spec §1.3 F13）。 */
@Entity(tableName = "search_history", primaryKeys = ["searchType", "searchKey"])
data class SearchHistoryEntity(
    val searchType: String, // "articlename" | "author"
    val searchKey: String,
    val searchedAtMs: Long,
)

/** 每日签到判重（一天一条）。 */
@Entity(tableName = "sign_log")
data class SignLogEntity(
    @PrimaryKey val day: String, // yyyy-MM-dd
)

/** 下载任务：一本一行，状态见 [status]。 */
@Entity(tableName = "novel_download")
data class NovelDownloadEntity(
    @PrimaryKey val aid: Int,
    val novelName: String,
    val author: String,
    val coverUrl: String,
    val totalChapters: Int,
    val doneChapters: Int,
    /** 0 等待 / 1 完成 / 2 失败 / 3 删除中（spec §1.5）。 */
    val status: Int,
    val updatedAtMs: Long,
)

/** 下载队列的章节行：哪几章被选中、各自进度。 */
@Entity(tableName = "download_chapter", primaryKeys = ["aid", "cid"])
data class DownloadChapterEntity(
    val aid: Int,
    val cid: Int,
    val chapterTitle: String,
    /** 0 等待 / 1 成功 / 2 失败。 */
    val status: Int,
    val attempts: Int = 0,
)

/** 书评（web_cache 之外的轻展示模型不落库，仅 DTO；见 remote/Models）。 */
@Entity(tableName = "bookshelf_local")
data class BookshelfLocalEntity(
    /** 本地「已在书架」标记，详情页书签两态用（远程书架经 CF 页也可更新）。 */
    @PrimaryKey val aid: Int,
    val addedAtMs: Long,
)
