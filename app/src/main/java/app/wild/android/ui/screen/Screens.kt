package app.wild.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.wild.android.data.local.ReadingHistoryEntity
import app.wild.android.data.remote.NovelCover
import app.wild.android.ui.components.BOOKSHELF_ASPECT
import app.wild.android.ui.components.CoverImage
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.components.NovelCoverCard
import app.wild.android.ui.vm.BookshelfViewModel
import app.wild.android.ui.vm.HistoryViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 书架 tab（spec §2.5）：AppBar「我的书架」+ [容量 info, 多选] / 多选态 [删除, 移动, 关闭]，
 * bottom=书架 FilterChip 横滑条；3 列网格 _BookCard（0.7 比）+ 多选圆勾。
 * Stage 4：`bookcase.php` 真实数据（需登录+clearance）；多选以 bid 操作；
 * 未登录态给明确提示而不是空白。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onNovelClick: (Int) -> Unit,
    vm: BookshelfViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) } // bid 集合
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }

    val items = state.page?.items.orEmpty()
    val itemByBid = remember(items) { items.associateBy { it.bid } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("我的书架") },
                    actions = {
                        if (selecting) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                enabled = selected.isNotEmpty(),
                            ) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                            IconButton(
                                onClick = { showMoveDialog = true },
                                enabled = selected.isNotEmpty(),
                            ) { Icon(Icons.Outlined.MoveToInbox, "移动") }
                            IconButton(onClick = { selecting = false; selected = emptySet() }) {
                                Icon(Icons.Outlined.Close, "关闭")
                            }
                        } else {
                            IconButton(onClick = { showTip = true }) {
                                Icon(Icons.Outlined.Info, "书架容量")
                            }
                            IconButton(onClick = { selecting = true }, enabled = items.isNotEmpty()) {
                                Icon(Icons.Outlined.SelectAll, "多选")
                            }
                        }
                    },
                )
                // 书架分类 FilterChip 横滑条（spec §2.5 bottom）
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.classes.size) { i ->
                        val c = state.classes[i]
                        FilterChip(
                            selected = state.classIndex == i,
                            onClick = { vm.loadClass(i) },
                            label = { Text(c.name) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingBlock(Modifier.padding(padding))
            state.needLogin -> EmptyBlock("书架需要登录后使用", Modifier.padding(padding))
            state.error != null && items.isEmpty() ->
                ErrorBlock(state.error!!, onRefresh = { vm.load() })
            items.isEmpty() -> EmptyBlock("书架为空", Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { vm.loadClass(state.classIndex) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items.size) { i ->
                        val book = items[i]
                        Box {
                            NovelCoverCard(
                                NovelCover(book.aid, book.title, book.coverUrl),
                                aspect = BOOKSHELF_ASPECT,
                                onClick = {
                                    if (selecting) {
                                        selected =
                                            if (book.bid in selected) selected - book.bid
                                            else selected + book.bid
                                    } else {
                                        onNovelClick(book.aid)
                                    }
                                },
                            )
                            if (selecting) {
                                val isSelected = book.bid in selected
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            selected =
                                                if (isSelected) selected - book.bid
                                                else selected + book.bid
                                        },
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    border = if (isSelected) null else
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.Gray),
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(4.dp).size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTip) {
        AlertDialog(
            onDismissRequest = { showTip = false },
            title = { Text("书架容量") },
            text = { Text(state.page?.capacityTip ?: "您的书架可收藏 100 本") },
            confirmButton = { TextButton(onClick = { showTip = false }) { Text("确定") } },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selected.size} 本书籍吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    selecting = false
                    vm.moveSelected(selected.toList(), -1) { msg ->
                        selected = emptySet()
                        scope.launch { snackbar.showSnackbar(msg) }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
        )
    }
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动到书架") },
            text = {
                Column {
                    state.classes.filterIndexed { i, _ -> i != state.classIndex }.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            modifier = Modifier.clickable {
                                showMoveDialog = false
                                selecting = false
                                vm.moveSelected(selected.toList(), c.classId) { msg ->
                                    selected = emptySet()
                                    scope.launch { snackbar.showSnackbar(msg) }
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/**
 * 历史 tab（spec §2.6）：AppBar「阅读历史」+ 清空钮；列表卡
 * （封面 80×120 + 书名/作者/最后阅读时间 + 「继续阅读」按钮条）。
 * Stage 4：`reading_history` Flow 驱动；单删=长按弹确认；清空走 DAO。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNovelClick: (Int) -> Unit,
    onContinueRead: (ReadingHistoryEntity) -> Unit,
    vm: HistoryViewModel = koinViewModel(),
) {
    val histories by vm.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ReadingHistoryEntity?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读历史") },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Outlined.DeleteOutline, "清空")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (histories.isEmpty()) {
            EmptyBlock("暂无阅读历史", Modifier.padding(padding))
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshing = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(histories) { h ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = { onNovelClick(h.novelId) },
                                        onLongClick = { deleteTarget = h },
                                    )
                                    .padding(12.dp),
                            ) {
                                Row {
                                    Surface(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .aspectRatio(80f / 120f)
                                            .clip(RoundedCornerShape(4.dp)),
                                    ) { CoverImage(h.novelName, h.coverUrl) }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(h.novelName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        Text("作者：${h.author}", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "最后阅读：${fmt.format(Date(h.lastReadAtMs))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                // 「继续阅读 - {章名}」按钮条（primaryContainer@30%，spec §2.6）
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        .clickable { onContinueRead(h) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "继续阅读 - ${h.chapterTitle}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史") },
            text = { Text("确定要清空所有阅读历史吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    vm.clearAll()
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } },
        )
    }
    deleteTarget?.let { h ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除历史记录") },
            text = { Text("确定要删除《${h.novelName}》的阅读历史吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(h)
                    deleteTarget = null
                    scope.launch { snackbar.showSnackbar("已删除阅读历史") }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/**
 * 更多 tab（spec §2.16）：AppBar「更多」+ 4 ListTile（下载/账户/设置/关于）。
 * 「关于」trailing 带「新版本」pill（无更新时隐藏）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onDownloads: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("更多") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                headlineContent = { Text("下载") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.clickable(onClick = onDownloads),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Person, null) },
                headlineContent = { Text("账户") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.clickable(onClick = onAccount),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Settings, null) },
                headlineContent = { Text("设置") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.clickable(onClick = onSettings),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                headlineContent = { Text("关于") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                },
                modifier = Modifier.clickable(onClick = onAbout),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun BookshelfPreview() {
    app.wild.android.ui.theme.WildTheme { Text("预览需要 Koin 环境，见真机截图") }
}
