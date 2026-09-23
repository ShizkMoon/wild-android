package app.wild.android.data.repository

import android.content.Context
import app.wild.android.data.download.DownloadEngine
import app.wild.android.data.local.ChapterCacheDao
import app.wild.android.data.local.ChapterCacheEntity
import app.wild.android.data.remote.Wenku8DataSource
import java.io.File

/**
 * 阅读器内容源（spec §3.5 读取顺序）：
 * 下载文件（离线） → `chapter_cache`（7 天） → 网络抓取（写缓存）。
 */
class ReaderContentSource(
    private val context: Context,
    private val source: Wenku8DataSource,
    private val chapterCacheDao: ChapterCacheDao,
) {
    suspend fun chapterText(aid: Int, cid: Int): Result<String> {
        // 1. 下载文件
        val file = File(context.filesDir, "download/$aid/chapter_$cid.txt")
        if (file.exists()) {
            return runCatching { file.readText(Charsets.UTF_8) }
        }
        // 2. 章节缓存
        chapterCacheDao.get(aid, cid)?.let { return Result.success(it.content) }
        // 3. 网络
        return source.chapterText(aid, cid).onSuccess { text ->
            chapterCacheDao.put(
                ChapterCacheEntity(aid, cid, text, System.currentTimeMillis())
            )
        }
    }
}
