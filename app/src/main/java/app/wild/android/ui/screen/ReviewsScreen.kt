package app.wild.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.contentColumnWidth
import app.wild.android.ui.vm.ReviewsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 评论页 `/novel/reviews`（spec §2.12）：AppBar「{书名} - 评论」；
 * 卡片列表（头像占位 + 用户名 + 时间 + 内容），下拉刷新 + 到底自动加载（末项菊花）。
 * Stage 4：`reviews.php` 真实分页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    aid: Int,
    onBack: () -> Unit,
    vm: ReviewsViewModel = koinViewModel(parameters = { parametersOf(aid) }),
) {
    val title by vm.title.collectAsState()
    val paged by vm.reviews.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var refreshing = paged.loading && paged.items.isNotEmpty()

    LaunchedEffect(aid) { vm.load() }

    // 到底自动加载（spec：末项菊花 + microtask；Compose 用快照流判距底）
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1
        }.collect { atEnd ->
            if (atEnd) vm.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title - 评论") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        when {
            paged.loading && paged.items.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            paged.error != null && paged.items.isEmpty() ->
                ErrorBlock(paged.error!!, onRefresh = { vm.refresh() })
            paged.items.isEmpty() -> EmptyBlock("暂无评论", Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(paged.items) { _, r ->
                        Card(
                            modifier = Modifier
                                .contentColumnWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.size(8.dp))
                                    Column {
                                        Text(r.userName, style = MaterialTheme.typography.titleMedium)
                                        Text(r.time, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(r.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (!paged.endReached) {
                        item {
                            Box(
                                modifier = Modifier.contentColumnWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun ReviewsPreview() {
    app.wild.android.ui.theme.WildTheme { Text("预览需要 Koin 环境，见真机截图") }
}
