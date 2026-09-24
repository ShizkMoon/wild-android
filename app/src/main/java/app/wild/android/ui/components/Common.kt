package app.wild.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.data.remote.NovelCover
import coil3.compose.SubcomposeAsyncImage
import kotlin.math.abs

/** 封面网格统一纵横比（spec：207/307 ≈ 0.674；书架页用 0.7）。 */
const val COVER_ASPECT = 207f / 307f
const val BOOKSHELF_ASPECT = 0.7f

/**
 * MD3 大屏可读性规范（m3.material.io/foundations/layout）：正文/表单/列表类内容
 * 在宽屏下限定最大栏宽，避免长行拉伸。统一三档：
 * expanded(≥840dp) → 720dp，medium(≥600dp) → 600dp，compact 不限。
 */
@Composable
fun contentColumnMaxWidth(): Dp {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 720.dp
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 600.dp
        else -> Dp.Unspecified
    }
}

/** 配合 LazyColumn 的 `horizontalAlignment = CenterHorizontally` 使用：限宽并铺满列内宽。 */
@Composable
fun Modifier.contentColumnWidth(): Modifier =
    contentColumnMaxWidth().let { if (it == Dp.Unspecified) fillMaxWidth() else widthIn(max = it).fillMaxWidth() }

/** 封面网格列数随窗口宽度伸缩（列表/网格类页密度规范）：compact 3 / medium 4 / expanded 6。 */
@Composable
fun adaptiveGridColumns(): Int {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 6
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 4
        else -> 3
    }
}

/**
 * 封面图：[url] 非空走 Coil（OkHttp 磁盘缓存 + UA/Referer），
 * loading/error 回落到书名 hash 渐变占位（原 Stage 3 行为，兼作无 URL 兜底）。
 */
@Composable
fun CoverImage(title: String, url: String? = null, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { CoverPlaceholder(title, Modifier.fillMaxSize()) },
            error = { CoverPlaceholder(title, Modifier.fillMaxSize()) },
        )
    } else {
        CoverPlaceholder(title, modifier)
    }
}

/** 书名 hash → 确定的渐变底色 + 书名首字（占位图）。 */
@Composable
private fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val palette = remember(title) { coverPalette(title) }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(palette[0], palette[1])))
            clipRect {
                // 斜向装饰块，模拟轻小说封面的插画色块感
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, h)
                    lineTo(w * 0.55f, h * 0.45f)
                    lineTo(w, h * 0.45f)
                    lineTo(w, h)
                    close()
                }
                drawPath(path, palette[2].copy(alpha = 0.35f))
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                title.take(1),
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

private val COVER_PALETTES = listOf(
    listOf(Color(0xFF5B7EC4), Color(0xFF8FA8D8), Color(0xFF2C4A8A)),
    listOf(Color(0xFF6B5B95), Color(0xFF9C89B8), Color(0xFF3D2C5F)),
    listOf(Color(0xFF4E9A94), Color(0xFF7FC4BE), Color(0xFF1F5A55)),
    listOf(Color(0xFFC46A5B), Color(0xFFD8978A), Color(0xFF7E3327)),
    listOf(Color(0xFFB08A3E), Color(0xFFD3B06A), Color(0xFF6B4F16)),
    listOf(Color(0xFF7A5BC4), Color(0xFFA48AD8), Color(0xFF4A2C8A)),
    listOf(Color(0xFF4E7E9A), Color(0xFF7EAEC4), Color(0xFF1F4A5A)),
    listOf(Color(0xFFC45B8A), Color(0xFFD88AAF), Color(0xFF7E2C50)),
)

private fun coverPalette(title: String): List<Color> =
    COVER_PALETTES[abs(title.hashCode()) % COVER_PALETTES.size]

/**
 * `_NovelCoverCard` 复刻（spec §5）：Card(elevation .5, radius 4)
 * + Expanded 封面 + Padding(4) 标题 12sp w500 单行省略。
 */
@Composable
fun NovelCoverCard(
    novel: NovelCover,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = COVER_ASPECT,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
    ) {
        Column {
            CoverImage(
                novel.title,
                novel.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect),
            )
            Text(
                novel.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.W500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/**
 * 统一错误块（spec 附录 F.1）：RefreshIndicator + 居中大图标(48,grey)
 * + 「{title}（下拉刷新）」+ 消息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorBlock(
    message: String,
    title: String = "加载失败",
    onRefresh: () -> Unit = {},
) {
    // CF 验证失败类错误给「打开站点验证」直达入口（SZKM-68：不再死胡同）。
    val showVerifyAction = message.contains("验证") || message.contains("Cloudflare")
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxHeight(0.9f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("$title (下拉刷新)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        if (showVerifyAction) {
                            Spacer(Modifier.height(16.dp))
                            val cfSession = org.koin.compose.koinInject<app.wild.android.data.remote.CfSession>()
                            androidx.compose.material3.OutlinedButton(
                                onClick = { cfSession.requestUserVerify() },
                            ) { Text("打开站点验证") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyBlock(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 通用封面网格（spec F12）：padding 8 / spacing 8 / 207:307。
 * [hasMore]+[loadingMore]+[onLoadMore] = 真实分页无限滚动（距底 ≤6 格触发），
 * 末行放 loading 格；[columns] 由调用方按窗口宽度给出（宽屏 >3 列）。
 */
@Composable
fun NovelGrid(
    novels: List<NovelCover>,
    onNovelClick: (NovelCover) -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = COVER_ASPECT,
    columns: Int = 3,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val gridState: LazyGridState = rememberLazyGridState()

    // 距底 ≤6 格触发下一页
    LaunchedEffect(gridState, novels.size, hasMore) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !loadingMore) onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(novels.size) { i ->
            NovelCoverCard(novels[i], onClick = { onNovelClick(novels[i]) }, aspect = aspect)
        }
        if (hasMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
        }
    }
}

/** 列表项标题 + 值行（账户页用）：80dp 灰标签 + w500 值。 */
@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(80.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            value.ifEmpty { "未设置" },
            fontWeight = FontWeight.W500,
            modifier = Modifier.weight(1f),
        )
    }
}
