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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.wild.android.data.mock.WildMock
import app.wild.android.ui.components.CoverImage
import kotlinx.coroutines.launch

/**
 * 下载选章页 `/novel/downloading`（spec §2.14）：AppBar「选择下载章节」+ 全选 + 下载钮；
 * 信息卡（封面 80×120 + 标题/作者/状态/已下载 x/y）；卷 Card + 章 ListTile
 * （已下载=绿勾不可选，未下载=Checkbox）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSelectScreen(aid: Int, onBack: () -> Unit) {
    val info = remember(aid) { WildMock.novelInfo(aid) }
    val volumes = remember(aid) { WildMock.volumes(aid) }
    val allCids = remember(volumes) { volumes.flatMap { it.chapters.map { c -> c.cid } } }
    // mock：前两章已下载（绿勾不可选），其余未下载
    val downloaded = remember(aid) { setOf(aid * 1000 + 1, aid * 1000 + 2) }
    var selected by remember { mutableStateOf(allCids.filter { it !in downloaded }.take(5).toSet()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

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
                    IconButton(onClick = {
                        selected = if (allSelected) emptySet() else selectable.toSet()
                    }) {
                        Icon(
                            if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            "全选",
                        )
                    }
                    IconButton(onClick = {
                        if (selected.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("请选择要下载的章节") }
                        } else {
                            scope.launch { snackbar.showSnackbar("开始下载") }
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.Download, "下载")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Surface(
                            modifier = Modifier
                                .width(80.dp)
                                .aspectRatio(80f / 120f)
                                .clip(RoundedCornerShape(4.dp)),
                        ) { CoverImage(info.title) }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("作者：${info.author}", style = MaterialTheme.typography.bodyMedium)
                            Text("状态：${info.status}", style = MaterialTheme.typography.bodyMedium)
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
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                volume.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp),
                            )
                            HorizontalDivider()
                            volume.chapters.forEach { ch ->
                                val isDownloaded = ch.cid in downloaded
                                ListItem(
                                    headlineContent = { Text(ch.title) },
                                    trailingContent = {
                                        if (isDownloaded) {
                                            Icon(Icons.Filled.CheckCircle, "已下载", tint = Color(0xFF4CAF50))
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

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DownloadSelectPreview() {
    app.wild.android.ui.theme.WildTheme { DownloadSelectScreen(aid = 2, onBack = {}) }
}
