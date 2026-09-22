package app.wild.android.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.data.mock.WildMock
import kotlinx.coroutines.launch

/**
 * 普通阅读器 `/novel/reader` reader_type=normal（spec §2.8）：
 * 背景 + 水平分页 PageView + 点击三区翻页 + 顶部控制栏（黑 0.7）+ 目录/设置弹层。
 * 分页算法见 [paginate]；进度 = 累计文本字数锚点（spec 决策 C）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagedReaderScreen(aid: Int, cid: Int, onBack: () -> Unit) {
    val novel = remember(aid) { WildMock.novelInfo(aid) }
    val volumes = remember(aid) { WildMock.volumes(aid) }
    val flat = remember(volumes) { volumes.flatMap { v -> v.chapters.map { v.title to it } } }
    var currentIndex by rememberSaveable { mutableIntStateOf(flat.indexOfFirst { it.second.cid == cid }.coerceAtLeast(0)) }

    // 阅读器主题配色（reader_theme_mode → 明/暗配色对）
    val dark = when (ReaderSettings.themeMode) {
        ReaderThemeMode.AUTO -> isSystemInDarkTheme()
        ReaderThemeMode.LIGHT -> false
        ReaderThemeMode.DARK -> true
    }
    val bg = if (dark) ReaderSettings.darkBackgroundColor else ReaderSettings.lightBackgroundColor
    val fg = if (dark) ReaderSettings.darkTextColor else ReaderSettings.lightTextColor

    // 打开阅读器时保持亮屏（spec F24，默认关）
    val view = LocalView.current
    DisposableEffect(ReaderSettings.keepOnReading) {
        if (ReaderSettings.keepOnReading) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var showControls by rememberSaveable { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var lastPrevTapAt by remember { mutableLongStateOf(0L) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 宽屏限制内容栏宽（MD3 大屏可读性：阅读正文 ≤ ~640dp）
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val contentMaxWidth = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 640.dp
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 560.dp
        else -> Int.MAX_VALUE.dp
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    Scaffold(
        containerColor = bg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val leftPadPx = with(density) { ReaderSettings.leftPadding.dp.toPx() }
            val rightPadPx = with(density) { ReaderSettings.rightPadding.dp.toPx() }
            val topBarPx = with(density) { ReaderSettings.topBarHeight.dp.toPx() }
            val bottomBarPx = with(density) { ReaderSettings.bottomBarHeight.dp.toPx() }
            val spacingPx = with(density) { ReaderSettings.paragraphSpacing.dp.toPx() }
            val fontSize = ReaderSettings.fontSize
            val lineHeight = ReaderSettings.lineHeight

            val canvasWPx = wPx - leftPadPx - rightPadPx
            val canvasHPx = hPx - topBarPx - bottomBarPx

            val content = remember(currentIndex) { WildMock.chapterContent(flat[currentIndex].second.title) }
            val textStyle = remember(fontSize, lineHeight, fg) {
                TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = lineHeight.em,
                    letterSpacing = 0.5.sp,
                    color = fg,
                )
            }

            val pages = remember(content, canvasWPx, canvasHPx, textStyle, spacingPx) {
                paginate(content, canvasHPx) { text ->
                    val layout = measurer.measure(
                        AnnotatedString(text),
                        textStyle,
                        constraints = Constraints(maxWidth = canvasWPx.toInt().coerceAtLeast(1)),
                    )
                    ParagraphMeasure(
                        heightPx = layout.size.height.toFloat(),
                        paragraphSpacingPx = spacingPx,
                    ) { h ->
                        runCatching { layout.getOffsetForPosition(Offset(0f, h)) }.getOrDefault(0)
                    }
                }
            }

            val pagerState = rememberPagerState(initialPage = 0) { pages.size }

            // 点击三区：x<30% 或 y<30% = 上一页；x>70% 或 y>70% = 下一页；其余切控制栏
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pagerState.pageCount) {
                        detectTapGestures { offset ->
                            val x = offset.x / wPx
                            val y = offset.y / hPx
                            when {
                                x < 0.3f || y < 0.3f -> {
                                    if (pagerState.currentPage > 0) {
                                        scope.launch { pagerState.scrollToPage(pagerState.currentPage - 1) }
                                    } else {
                                        val now = System.currentTimeMillis()
                                        if (now - lastPrevTapAt < 2000) {
                                            if (currentIndex > 0) currentIndex--
                                        } else {
                                            lastPrevTapAt = now
                                            scope.launch { snackbar.showSnackbar("再次点击加载上一章") }
                                        }
                                    }
                                }
                                x > 0.7f || y > 0.7f -> {
                                    if (pagerState.currentPage < pages.size - 1) {
                                        scope.launch { pagerState.scrollToPage(pagerState.currentPage + 1) }
                                    } else if (currentIndex < flat.size - 1) {
                                        currentIndex++
                                    }
                                }
                                else -> showControls = !showControls
                            }
                        }
                    },
            ) {
                // 宽屏：阅读列宽受限居中
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxSize(),
                        key = { it },
                    ) { pageIndex ->
                        val page = pages.getOrNull(pageIndex) ?: ReaderPage("", false)
                        if (page.isImage) {
                            ImagePage(
                                marker = page.content,
                                pageNum = pageIndex + 1,
                                pageCount = pages.size,
                                topPad = topBarPx, bottomPad = bottomBarPx,
                                leftPad = leftPadPx, rightPad = rightPadPx,
                                density = density, fg = fg,
                            )
                        } else {
                            TextPage(
                                text = page.content,
                                style = textStyle,
                                spacingPx = spacingPx,
                                pageNum = pageIndex + 1,
                                pageCount = pages.size,
                                topPad = topBarPx, bottomPad = bottomBarPx,
                                leftPad = leftPadPx, rightPad = rightPadPx,
                                density = density,
                            )
                        }
                    }
                }
            }

            // 顶部控制栏（黑 0.7，无底部栏）
            if (showControls) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White)
                    }
                    Text(
                        flat[currentIndex].second.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showCatalog = true }) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, "目录", tint = Color.White)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, "设置", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showCatalog) {
        ChapterCatalogSheet(
            volumes = volumes,
            currentCid = flat[currentIndex].second.cid,
            heightFraction = 0.9f,
            currentHighlightColor = Color.Gray.copy(alpha = 0.3f),
            onDismiss = { showCatalog = false },
            onSelect = { sel ->
                showCatalog = false
                val idx = flat.indexOfFirst { it.second.cid == sel }
                if (idx >= 0) currentIndex = idx
            },
        )
    }
    if (showSettings) {
        ReaderSettingsSheet(html = false, onDismiss = { showSettings = false })
    }
}

@Composable
private fun TextPage(
    text: String,
    style: TextStyle,
    spacingPx: Float,
    pageNum: Int,
    pageCount: Int,
    topPad: Float,
    bottomPad: Float,
    leftPad: Float,
    rightPad: Float,
    density: androidx.compose.ui.unit.Density,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(with(density) { topPad.toDp() }))
        val paragraphs = text.split("\n")
        Column(
            modifier = Modifier
                .padding(horizontal = with(density) { leftPad.toDp() }),
        ) {
            paragraphs.forEachIndexed { i, p ->
                val isTitle = pageNum == 1 && i == 0 // 原 App：每章正文首行显示章节标题
                Text(
                    p,
                    style = style.copy(
                        fontSize = if (isTitle) (style.fontSize.value + 2f).sp else style.fontSize,
                        fontWeight = if (isTitle) FontWeight.Bold else style.fontWeight,
                    ),
                )
                if (i < paragraphs.lastIndex) {
                    Spacer(Modifier.height(with(density) { spacingPx.toDp() }))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("$pageNum/$pageCount", fontSize = 10.sp, color = style.color, modifier = Modifier.alpha(0.3f))
        }
        Spacer(Modifier.height(with(density) { bottomPad.toDp() }))
    }
}

@Composable
private fun ImagePage(
    marker: String,
    pageNum: Int,
    pageCount: Int,
    topPad: Float,
    bottomPad: Float,
    leftPad: Float,
    rightPad: Float,
    density: androidx.compose.ui.unit.Density,
    fg: Color,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(with(density) { topPad.toDp() }))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = with(density) { leftPad.toDp() }),
            contentAlignment = Alignment.Center,
        ) {
            MockIllustration(seed = marker)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("$pageNum/$pageCount", fontSize = 10.sp, color = fg, modifier = Modifier.alpha(0.3f))
        }
        Spacer(Modifier.height(with(density) { bottomPad.toDp() }))
    }
}

/** 插图占位（mock）：确定的渐变天空 + 山形剪影，模拟轻小说插画。 */
@Composable
fun MockIllustration(seed: String, modifier: Modifier = Modifier) {
    val p = remember(seed) { seed.hashCode().let { if (it < 0) -it else it } }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFF1B2440), RoundedCornerShape(8.dp)),
    ) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF2B3A67), Color(0xFF8E9CC0)))
        )
        drawCircle(Color(0xFFFFE8B0), radius = size.minDimension * 0.12f, center = Offset(size.width * 0.72f, size.height * 0.28f))
        // 山形
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width * 0.3f, size.height * 0.55f)
            lineTo(size.width * 0.5f, size.height * 0.8f)
            lineTo(size.width * 0.75f, size.height * 0.5f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Color(0xFF1A2332))
    }
}
