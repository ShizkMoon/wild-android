package app.wild.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.components.NovelGrid
import app.wild.android.ui.vm.HomeViewModel
import app.wild.android.ui.vm.Paged
import app.wild.android.data.remote.NovelCover
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** 排行榜 sort 标签↔值（audit_pages_general.md:204）。 */
private val TOPLIST_SORTS = listOf(
    "更新" to "lastupdate", "发布" to "postdate", "总访问" to "allvisit",
    "总推荐" to "allvote", "总收藏" to "goodnum", "日访问" to "dayvisit",
    "日推荐" to "dayvote", "月访问" to "monthvisit", "月推荐" to "monthvote",
    "周访问" to "weekvisit", "周推荐" to "weekvote", "字数" to "size", "动画" to "anime",
)

/**
 * 首页框架页 `/home` tab0（spec §2.4）：AppBar「轻小说文库」+ 搜索 icon + TabBar(4)。
 * 宽屏（width ≥ medium）：浏览网格 + 小说详情组成 ListDetailPaneScaffold 双栏
 * （canonical layout）；窄屏维持原 App 单栏 + 路由跳详情。
 * Stage 4：四 tab 全部接真实数据源（推荐/分类/排行/完结），错误块统一下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onNovelClick: (Int) -> Unit,
    onCategoryClick: (String) -> Unit,
    onReviews: (Int) -> Unit = {},
    onDownloadSelect: (Int) -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onChapterClick: (Int, Int) -> Unit = { _, _ -> },
    vm: HomeViewModel = koinViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val wide = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("轻小说文库") },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("推荐", "分类", "排行", "完结").forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (wide) {
            // 宽屏：列表-详情双栏（Material 3 adaptive canonical layout）
            val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
            val scope = rememberCoroutineScope()
            androidx.activity.compose.BackHandler(
                enabled = navigator.canNavigateBack(),
            ) { scope.launch { navigator.navigateBack() } }
            NavigableListDetailPaneScaffold(
                navigator = navigator,
                modifier = Modifier.padding(padding),
                listPane = {
                    AnimatedPane {
                        HomeTabContent(
                            tab = tab,
                            vm = vm,
                            onNovelClick = { aid ->
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, aid)
                                }
                            },
                            onCategoryClick = onCategoryClick,
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        val aid = navigator.currentDestination?.contentKey
                        if (aid == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "从左侧选择一本小说",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            NovelInfoScreen(
                                aid = aid,
                                showBackButton = false,
                                onBack = { scope.launch { navigator.navigateBack() } },
                                onReviews = { onReviews(aid) },
                                onDownload = { onDownloadSelect(aid) },
                                onAuthorClick = onAuthorClick,
                                onTagClick = onCategoryClick,
                                onChapterClick = { cid -> onChapterClick(aid, cid) },
                            )
                        }
                    }
                },
            )
        } else {
            Box(Modifier.padding(padding)) {
                HomeTabContent(tab = tab, vm = vm, onNovelClick = onNovelClick, onCategoryClick = onCategoryClick)
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    tab: Int,
    vm: HomeViewModel,
    onNovelClick: (Int) -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 6
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 4
        else -> 3
    }
    when (tab) {
        0 -> RecommendTab(vm, onNovelClick, columns)
        1 -> CategoryTab(vm, onNovelClick, onCategoryClick, columns)
        2 -> ToplistTab(vm, onNovelClick, columns)
        3 -> FinishedTab(vm, onNovelClick, columns)
    }
}

/** 分页网格的通用呈现：loading → 菊花；error → 错误块；空 → 空态；否则 NovelGrid。 */
@Composable
private fun PagedGrid(
    paged: Paged<NovelCover>,
    columns: Int,
    onNovelClick: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    emptyText: String = "暂无内容",
) {
    when {
        paged.loading && paged.items.isEmpty() -> LoadingBlock()
        paged.error != null && paged.items.isEmpty() ->
            ErrorBlock(paged.error, onRefresh = onRefresh)
        paged.items.isEmpty() -> EmptyBlock(emptyText)
        else -> NovelGrid(
            novels = paged.items,
            onNovelClick = { onNovelClick(it.aid) },
            columns = columns,
            hasMore = !paged.endReached,
            loadingMore = paged.loadingMore,
            onLoadMore = onLoadMore,
        )
    }
}

/** 推荐 tab（spec §2.4）：区块标题(titleLarge bold, 16/16/16/8) + 3 列封面网格。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendTab(vm: HomeViewModel, onNovelClick: (Int) -> Unit, columns: Int) {
    val state by vm.recommend.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadRecommend() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            vm.loadRecommend(force = true)
            refreshing = false
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading && state.data == null -> LoadingBlock()
            state.error != null && state.data == null ->
                ErrorBlock(state.error!!, onRefresh = { vm.loadRecommend(force = true) })
            state.data.isNullOrEmpty() ->
                EmptyBlock("站点公告：本站已正式关闭新书上架，推荐区块为空")
            else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                state.data!!.forEach { block ->
                    item {
                        Text(
                            block.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                    item {
                        // 每区块固定 3 列 × 2 行的静态网格（spec：shrinkWrap + NeverScrollable）
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            block.novels.take(6).chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { novel ->
                                        app.wild.android.ui.components.NovelCoverCard(
                                            novel,
                                            onClick = { onNovelClick(novel.aid) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类 tab（spec §2.4/§7）：SegmentedButton 4 档 + 分组 PopupMenu 标签选择器 + 网格。 */
@Composable
internal fun CategoryTab(
    vm: HomeViewModel,
    onNovelClick: (Int) -> Unit,
    onCategoryClick: (String) -> Unit,
    columns: Int,
    initialTag: String? = null,
) {
    var viewMode by rememberSaveable { mutableIntStateOf(0) }
    var selectedTag by rememberSaveable { mutableStateOf(initialTag?.ifEmpty { null }) }
    var menuOpen by remember { mutableStateOf(false) }

    val tagGroupsState by vm.tagGroups.collectAsState()
    val paged by vm.category.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadTagGroups()
        if (selectedTag != null) vm.selectCategory(selectedTag, viewMode)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                listOf("更新", "热门", "完结", "动画").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = viewMode == i,
                        onClick = {
                            viewMode = i
                            if (selectedTag != null) vm.selectCategory(selectedTag, i)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                    ) { Text(label) }
                }
            }
            Box {
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selectedTag ?: "分类",
                        color = if (selectedTag != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selectedTag != null) FontWeight.Bold else FontWeight.Normal,
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val groups = tagGroupsState.data.orEmpty()
                    if (groups.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (tagGroupsState.error != null) "分类加载失败" else "加载中…") },
                            onClick = {},
                            enabled = false,
                        )
                    }
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            onClick = {},
                            enabled = false,
                        )
                        group.tags.forEach { tag ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        tag,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = if (tag == selectedTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (tag == selectedTag) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    selectedTag = tag
                                    menuOpen = false
                                    vm.selectCategory(tag, viewMode)
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        if (selectedTag == null) {
            EmptyBlock("请选择分类")
        } else {
            PagedGrid(
                paged = paged,
                columns = columns,
                onNovelClick = onNovelClick,
                onRefresh = { vm.selectCategory(selectedTag, viewMode) },
                onLoadMore = { vm.loadMoreCategory() },
            )
        }
    }
}

/** 排行 tab（spec §8）：横滑 FilterChip×13 + 网格。 */
@Composable
private fun ToplistTab(vm: HomeViewModel, onNovelClick: (Int) -> Unit, columns: Int) {
    var sort by rememberSaveable { mutableStateOf(TOPLIST_SORTS.first().second) }
    val paged by vm.toplist.collectAsState()
    LaunchedEffect(Unit) { vm.selectToplist(sort) }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TOPLIST_SORTS) { (label, value) ->
                FilterChip(
                    selected = sort == value,
                    onClick = { sort = value; vm.selectToplist(value) },
                    label = { Text(label) },
                )
            }
        }
        PagedGrid(
            paged = paged,
            columns = columns,
            onNovelClick = onNovelClick,
            onRefresh = { vm.selectToplist(sort) },
            onLoadMore = { vm.loadMoreToplist() },
        )
    }
}

/** 完结 tab（spec §9）：纯网格，无筛选条。 */
@Composable
private fun FinishedTab(vm: HomeViewModel, onNovelClick: (Int) -> Unit, columns: Int) {
    val paged by vm.finished.collectAsState()
    LaunchedEffect(Unit) { vm.loadFinished() }
    PagedGrid(
        paged = paged,
        columns = columns,
        onNovelClick = onNovelClick,
        onRefresh = { vm.loadFinished() },
        onLoadMore = { vm.loadMoreFinished() },
        emptyText = "完结列表为空（该页面需要登录态）",
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenPreview() {
    app.wild.android.ui.theme.WildTheme {
        Text("预览需要 Koin 环境，见真机截图")
    }
}
