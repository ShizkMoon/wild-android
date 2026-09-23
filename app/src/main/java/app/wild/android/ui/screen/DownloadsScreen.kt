package app.wild.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.data.local.NovelDownloadEntity
import app.wild.android.ui.components.CoverImage
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.components.contentColumnWidth
import app.wild.android.ui.vm.DownloadDetailViewModel
import app.wild.android.ui.vm.DownloadsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private data class DownloadStatusStyle(val label: String, val icon: ImageVector, val color: Color)

private fun statusStyle(status: Int): DownloadStatusStyle = when (status) {
    0 -> DownloadStatusStyle("等待下载", Icons.Outlined.Download, Color(0xFF2196F3))
    1 -> DownloadStatusStyle("下载完成", Icons.Outlined.CheckCircleOutline, Color(0xFF4CAF50))
    2 -> DownloadStatusStyle("下载失败", Icons.Outlined.ErrorOutline, Color(0xFFF44336))
    3 -> DownloadStatusStyle("正在删除", Icons.Outlined.DeleteOutline, Color(0xFFFF9800))
    else -> DownloadStatusStyle("未知", Icons.Outlined.HelpOutline, Color.Gray)
}

/**
 * 下载列表（spec §2.15）：「更多→下载」。列表项 = 封面 80×120 + 书名 + 作者
 * + 「x/y 章节」+ 状态文字与图标；AppBar 刷新 = 重置失败下载。
 * Stage 4：`novel_download` Flow 实时驱动；重置 = 失败章归队续传。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    vm: DownloadsViewModel = koinViewModel(),
) {
    val downloads by vm.downloads.collectAsState()
    val running by vm.running.collectAsState()
    val current by vm.current.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("下载")
                        if (running && current != null) {
                            Text(
                                "正在下载：$current",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = {
                        vm.resetAllFailed()
                        scope.launch { snackbar.showSnackbar("已重置所有失败的下载") }
                    }) {
                        Icon(Icons.Outlined.Refresh, "重置失败下载")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyBlock("暂无下载内容", Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(downloads) { d ->
                    val st = statusStyle(d.status)
                    Card(
                        modifier = Modifier
                            .contentColumnWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onOpenDetail(d.aid) },
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Surface(
                                modifier = Modifier
                                    .width(80.dp)
                                    .aspectRatio(80f / 120f)
                                    .clip(RoundedCornerShape(8.dp)),
                            ) { CoverImage(d.novelName, d.coverUrl) }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.novelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(d.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${d.doneChapters}/${d.totalChapters} 章节", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(st.icon, null, tint = st.color, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(st.label, style = MaterialTheme.typography.bodyMedium, color = st.color)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 下载详情（spec §2.15）：详情页同款布局 + 状态胶囊 + 删除钮；
 * 点章节直进普通阅读器（离线阅读入口）。
 * Stage 4：novel_download + download_chapter 实时状态；删除 = 状态 3 → 引擎清文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailScreen(
    aid: Int,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    vm: DownloadDetailViewModel = koinViewModel(parameters = { parametersOf(aid) }),
) {
    val download by vm.download.collectAsState()
    val info by vm.info.collectAsState()
    val volumes by vm.volumes.collectAsState()
    val chapters by vm.chapters.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(aid) { vm.load() }

    val st = statusStyle(download?.status ?: 0)
    val doneCids = chapters.filter { it.status == 1 }.map { it.cid }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .contentColumnWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(Modifier.padding(vertical = 16.dp)) {
                        Surface(
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(120f / 160f)
                                .clip(RoundedCornerShape(8.dp)),
                        ) { CoverImage(info?.title ?: download?.novelName ?: "", info?.coverUrl ?: download?.coverUrl) }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(info?.title ?: download?.novelName ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text("作者：${info?.author ?: download?.author ?: ""}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("状态：${info?.status ?: ""}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            // 下载状态胶囊
                            Surface(
                                color = st.color.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    st.label,
                                    color = st.color,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text("更新: ${info?.finUpdate ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "下载进度: ${download?.doneChapters ?: doneCids.size}/${download?.totalChapters ?: chapters.size}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("简介", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    HtmlLite(info?.introduceHtml ?: "")
                }
            }
            volumes.forEach { volume ->
                item {
                    Card(
                        modifier = Modifier
                            .contentColumnWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(volume.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 8.dp))
                            volume.chapters.forEach { ch ->
                                val downloaded = ch.cid in doneCids
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenChapter(ch.cid) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        ch.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (downloaded) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        if (downloaded) Icons.Outlined.CheckCircleOutline else Icons.Filled.ChevronRight,
                                        null,
                                        tint = if (downloaded) Color(0xFF4CAF50)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这本小说的下载内容吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.delete {
                        scope.launch { snackbar.showSnackbar("已删除下载") }
                        onBack()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DownloadsPreview() {
    app.wild.android.ui.theme.WildTheme { Text("预览需要 Koin 环境，见真机截图") }
}
