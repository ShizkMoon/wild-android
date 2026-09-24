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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.wild.android.ui.components.CoverImage
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.components.contentColumnWidth
import app.wild.android.ui.vm.DownloadSelectViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 下载选章页 `/novel/downloading`（spec §2.14）：AppBar「选择下载章节」+ 全选 + 下载钮；
 * 信息卡（封面 80×120 + 标题/作者/状态/已下载 x/y）；卷 Card + 章 ListTile
 * （已下载=绿勾不可选，未下载=Checkbox）。
 * Stage 4：真实目录 + 已下载章（download_chapter status=1）+ 入队 DownloadEngine。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSelectScreen(
    aid: Int,
    onBack: () -> Unit,
    vm: DownloadSelectViewModel = koinViewModel(parameters = { parametersOf(aid) }),
) {
    val info by vm.info.collectAsState()
    val volumes by vm.volumes.collectAsState()
    val downloaded by vm.downloadedCids.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var selected by remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(aid) { vm.load() }

    val allCids = volumes.flatMap { it.chapters.map { c -> c.cid } }
    val selectable = allCids - downloaded
    val allSelected = selectable.isNotEmpty() && selected.containsAll(selectable)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择下载章节") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    // AppBar 只留全选；下载动作下沉底部操作条（§2.14 选中计数条）
                    IconButton(onClick = {
                        selected = if (allSelected) emptySet() else selectable.toSet()
                    }) {
                        Icon(
                            if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            "全选",
                        )
                    }
                },
            )
        },
        bottomBar = {
            // 底部操作条：surfaceContainer + 顶缘细线，已选计数 + 下载钮
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "已选 ${selected.size} 章",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                if (selected.isEmpty()) {
                                    scope.launch { snackbar.showSnackbar("请选择要下载的章节") }
                                } else {
                                    vm.enqueue(selected) {
                                        scope.launch { snackbar.showSnackbar("已加入下载队列") }
                                    }
                                    onBack()
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.padding(end = 4.dp))
                            Text("下载")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            loading -> LoadingBlock(Modifier.padding(padding))
            error != null && volumes.isEmpty() ->
                ErrorBlock(error!!, title = "目录加载失败", onRefresh = { vm.load() }, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .contentColumnWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        border = app.wild.android.ui.theme.CardOutline,
                    ) {
                        Row(Modifier.padding(16.dp)) {
                            Surface(
                                modifier = Modifier
                                    .width(80.dp)
                                    .aspectRatio(80f / 120f)
                                    .clip(RoundedCornerShape(4.dp)),
                            ) { CoverImage(info?.title ?: "", info?.coverUrl) }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(info?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("作者：${info?.author ?: ""}", style = MaterialTheme.typography.bodyMedium)
                                Text("状态：${info?.status ?: ""}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "已下载：${downloaded.size}/${allCids.size}章",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                volumes.forEach { volume ->
                    item(key = "v-${volume.volumeId}") {
                        Card(
                            modifier = Modifier
                                .contentColumnWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .animateItem(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            border = app.wild.android.ui.theme.CardOutline,
                        ) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    volume.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(16.dp),
                                )
                                HorizontalDivider()
                                volume.chapters.forEach { ch ->
                                    val isDownloaded = ch.cid in downloaded
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                ch.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                // 已下载项降层：内容不可再下载
                                                color = if (isDownloaded) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else Color.Unspecified,
                                            )
                                        },
                                        trailingContent = {
                                            if (isDownloaded) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    "已下载",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                Checkbox(
                                                    checked = ch.cid in selected,
                                                    onCheckedChange = { checked ->
                                                        selected = if (checked) selected + ch.cid else selected - ch.cid
                                                    },
                                                )
                                            }
                                        },
                                        modifier = if (!isDownloaded) Modifier.clickable {
                                            selected = if (ch.cid in selected) selected - ch.cid else selected + ch.cid
                                        } else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DownloadSelectPreview() {
    app.wild.android.ui.theme.WildTheme { Text("预览需要 Koin 环境，见真机截图") }
}
