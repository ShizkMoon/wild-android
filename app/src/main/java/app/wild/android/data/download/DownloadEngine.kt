package app.wild.android.data.download

import android.content.Context
import app.wild.android.data.local.DownloadChapterEntity
import app.wild.android.data.local.DownloadDao
import app.wild.android.data.local.NovelDownloadEntity
import app.wild.android.data.remote.TocChapter
import app.wild.android.data.remote.Wenku8Client
import app.wild.android.data.remote.Wenku8DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/**
 * 下载引擎（spec §1.5/§3.5）：单发顺序下载，章级状态表驱动。
 *
 * - 任务行 `novel_download`（0 等待/1 完成/2 失败/3 删除中）+
 *   章行 `download_chapter`（0 等待/1 成功/2 失败 + attempts）。
 * - [start] 由 Application onCreate 调一次：扫 pending 续传（断点续传=章级粒度）。
 * - 文件落 `filesDir/download/{aid}/`：`cover`（无扩展名）、`chapter_{cid}.txt`、
 *   `img/{md5(url)}`；正文的 `<!--image-->URL<!--image-->` 保持远端 URL，
 *   阅读器经 Coil 命中 OkHttp 磁盘缓存即可离线看（下载时已预热）。
 * - 失败章指数退避（attempts≤3 内循环），整本跑完仍有失败 → 状态 2；
 *   `resetFailed*` 把失败章归 0 触发续跑。
 */
class DownloadEngine(
    private val context: Context,
    private val source: Wenku8DataSource,
    private val client: Wenku8Client,
    private val dao: DownloadDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private val wakeMutex = Mutex()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** 当前正在下载的章（UI 进度指示用）。 */
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current

    val downloads = dao.observeAll()
    fun chaptersFlow(aid: Int) = dao.observeChapters(aid)

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { loop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** 入队：整本（null）或选中章（cids）。已存在的章行保留状态（续传）。 */
    suspend fun enqueue(
        aid: Int,
        novelName: String,
        author: String,
        coverUrl: String,
        chapters: List<TocChapter>,
        cids: Set<Int>?,
    ) {
        val picked = if (cids == null) chapters else chapters.filter { it.cid in cids }
        if (picked.isEmpty()) return
        dao.upsert(
            NovelDownloadEntity(
                aid = aid,
                novelName = novelName,
                author = author,
                coverUrl = coverUrl,
                totalChapters = picked.size,
                doneChapters = dao.doneCount(aid),
                status = 0,
                updatedAtMs = System.currentTimeMillis(),
            )
        )
        val existing = dao.chapters(aid).map { it.cid }.toSet()
        for (ch in picked) {
            if (ch.cid !in existing) {
                dao.upsertChapter(
                    DownloadChapterEntity(aid, ch.cid, ch.title, status = 0)
                )
            }
        }
        wake()
    }

    /** 删除整本下载（状态 3 → 清文件 → 清行）。 */
    suspend fun delete(aid: Int) {
        dao.setStatus(aid, 3, System.currentTimeMillis())
        wake()
    }

    suspend fun resetFailed(aid: Int) {
        dao.resetFailed(aid)
        dao.setStatus(aid, 0, System.currentTimeMillis())
        wake()
    }

    suspend fun resetAllFailed() {
        dao.resetAllFailed()
        wake()
    }

    fun wake() {
        scope.launch { wakeMutex.withLock { /* 唤醒信号：loop 每轮自查 pending */ } }
        // loop 是 while(true) 轮询 + delay，wake 只需保证 loop 活着
        if (loopJob?.isActive != true) start()
    }

    private suspend fun loop() {
        _running.value = true
        try {
            while (true) {
                // 1. 删除队列
                val deleting = dao.aidsWithStatus(3)
                deleting.forEach { aid -> purgeNovel(aid) }

                // 2. 找下一个有待下的本
                val aid = dao.aidsWithPending().firstOrNull()
                if (aid == null) {
                    _current.value = null
                    delay(2000)
                    continue
                }
                downloadNovel(aid)
                delay(300) // 章间节流，避免打爆站点
            }
        } finally {
            _running.value = false
        }
    }

    private suspend fun downloadNovel(aid: Int) {
        val novel = dao.get(aid) ?: return
        val dir = File(context.filesDir, "download/$aid").apply { mkdirs() }

        // 封面
        if (novel.coverUrl.isNotBlank() && !File(dir, "cover").exists()) {
            runCatching {
                client.getBytes(novel.coverUrl, referer = client.apiHost() + "/")
            }.onSuccess { File(dir, "cover").writeBytes(it) }
        }

        var failed = 0
        val pending = dao.pendingChapters(aid)
        for (ch in pending) {
            // 中途被标删除 → 立即停
            if (dao.get(aid)?.status == 3) break
            _current.value = ch.chapterTitle
            val ok = downloadChapter(aid, ch, dir)
            if (!ok) failed++
            val done = dao.doneCount(aid)
            dao.setProgress(aid, done, 0, System.currentTimeMillis())
        }

        val current = dao.get(aid) ?: return
        if (current.status == 3) return // 删除流程接管
        val done = dao.doneCount(aid)
        val hasPending = dao.pendingChapters(aid).isNotEmpty()
        val status = when {
            failed > 0 || hasPending && dao.chapters(aid).any { it.status == 2 } -> 2
            !hasPending && dao.chapters(aid).isNotEmpty() -> 1
            else -> 0
        }
        dao.setProgress(aid, done, status, System.currentTimeMillis())
    }

    private suspend fun downloadChapter(aid: Int, ch: DownloadChapterEntity, dir: File): Boolean {
        val file = File(dir, "chapter_${ch.cid}.txt")
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val text = source.chapterText(aid, ch.cid).getOrThrow()
                // 预热插图到 OkHttp 磁盘缓存（离线时 Coil 直接命中）
                IMAGE_URL_REGEX.findAll(text).forEach { m ->
                    val url = m.groupValues[1]
                    runCatching { client.getBytes(url, referer = client.apiHost() + "/") }
                }
                file.writeText(text, Charsets.UTF_8)
                dao.setChapterStatus(aid, ch.cid, 1)
                return true
            } catch (e: Exception) {
                dao.setChapterStatus(aid, ch.cid, if (attempt + 1 >= MAX_ATTEMPTS) 2 else 0)
                if (attempt + 1 < MAX_ATTEMPTS) delay(1000L shl attempt)
            }
        }
        return false
    }

    private suspend fun purgeNovel(aid: Int) {
        runCatching {
            File(context.filesDir, "download/$aid").deleteRecursively()
        }
        dao.deleteChapters(aid)
        dao.delete(aid)
    }

    fun chapterFile(aid: Int, cid: Int): File =
        File(context.filesDir, "download/$aid/chapter_$cid.txt")

    suspend fun isChapterDownloaded(aid: Int, cid: Int): Boolean =
        chapterFile(aid, cid).exists() &&
            dao.chapters(aid).firstOrNull { it.cid == cid }?.status == 1

    companion object {
        private const val MAX_ATTEMPTS = 3
        val IMAGE_URL_REGEX = Regex("<!--image-->([^<]+)<!--image-->")

        fun md5(s: String): String =
            MessageDigest.getInstance("MD5").digest(s.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
