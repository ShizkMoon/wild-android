package app.wild.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.components.NovelGrid
import app.wild.android.ui.vm.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 搜索页 `/search`（spec §2.13）：AppBar「搜索」+ actions SegmentedButton[书名|作者]；
 * 16 padding 搜索框；三态：历史 ListTile / 结果网格 / 「输入关键词开始搜索」。
 * Stage 4：真实 `search.php` 分页 + 搜索历史落库（recordSearch）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialType: String,
    initialKey: String,
    onBack: () -> Unit,
    onNovelClick: (Int) -> Unit,
    vm: SearchViewModel = koinViewModel(),
) {
    var searchType by rememberSaveable { mutableStateOf(if (initialType == "author") "author" else "articlename") }
    var input by rememberSaveable { mutableStateOf(initialKey) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val histories by vm.history.collectAsState()
    val paged by vm.results.collectAsState()

    // 深链带参直接搜
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialKey.isNotBlank()) {
            submitted = true
            vm.search(if (initialType == "author") "author" else "articlename", initialKey)
        }
    }

    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 6
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 4
        else -> 3
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    SingleChoiceSegmentedButtonRow {
                        listOf("书名" to "articlename", "作者" to "author").forEachIndexed { i, (label, value) ->
                            SegmentedButton(
                                selected = searchType == value,
                                onClick = { searchType = value; input = ""; submitted = false },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                icon = {},
                            ) { Text(label) }
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("搜索小说或作者") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (input.isNotBlank()) {
                        submitted = true
                        vm.search(searchType, input.trim())
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            when {
                // 1. 输入为空且有历史 → 历史列表
                !submitted && input.isBlank() && histories.isNotEmpty() -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        items(histories) { h ->
                            val isName = h.searchType == "articlename"
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        if (isName) Icons.Filled.Book else Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = if (isName) Color(0xFF2196F3) else Color(0xFF4CAF50),
                                    )
                                },
                                headlineContent = {
                                    Text(h.searchKey, color = if (isName) Color(0xFF2196F3) else Color(0xFF4CAF50))
                                },
                                supportingContent = {
                                    Text(if (isName) "书名搜索" else "作者搜索", fontSize = 12.sp)
                                },
                                modifier = Modifier.clickable {
                                    input = h.searchKey
                                    searchType = h.searchType
                                    submitted = true
                                    vm.search(h.searchType, h.searchKey)
                                },
                            )
                        }
                    }
                }
                // 2. 已提交 → 分页结果
                submitted -> {
                    when {
                        paged.loading && paged.items.isEmpty() -> LoadingBlock()
                        paged.error != null && paged.items.isEmpty() ->
                            ErrorBlock(paged.error!!, onRefresh = { vm.search(searchType, input.trim()) })
                        paged.items.isEmpty() -> EmptyBlock("没有找到相关小说")
                        else -> NovelGrid(
                            novels = paged.items,
                            onNovelClick = { onNovelClick(it.aid) },
                            columns = columns,
                            hasMore = !paged.endReached,
                            loadingMore = paged.loadingMore,
                            onLoadMore = { vm.loadMore() },
                        )
                    }
                }
                // 3. 默认态
                else -> EmptyBlock("输入关键词开始搜索")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun SearchPreview() {
    app.wild.android.ui.theme.WildTheme { Text("预览需要 Koin 环境，见真机截图") }
}
