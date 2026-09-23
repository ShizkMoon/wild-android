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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
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
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.vm.ReaderViewModel
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 普通阅读器 `/novel/reader` reader_type=normal（spec §2.8）：
 * 背景 + 水平分页 PageView + 点击三区翻页 + 顶部控制栏（黑 0.7）+ 目录/设置弹层。
 * 分页算法见 [paginate]；进度 = 累计文本字数锚点（spec 决策 C）。
 * Stage 4：目录/正文来自 [ReaderViewModel]（下载 → 缓存 → 网络三级）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagedReaderScreen(
    aid: Int,
    cid: Int,
    onBack: () -> Unit,
    vm: ReaderViewModel = koinViewModel(parameters = { parametersOf(aid, cid) }),
) {
    LaunchedEffect(Unit) { vm.load() }
    val st by vm.state.collectAsState()
    val restore by vm.restoreProgress.collectAsState()
    val flat = st.flatChapters
    val currentIndex = st.currentIndex

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
            // 页码行高预留：n/m 计数器(10sp) + 上下间距，避免满页文字把它挤出屏幕（验收低 #10）
            val counterPx = with(density) { (10.sp.toPx() * 1.6f) + 12.dp.toPx() }
            val canvasHPx = hPx - topBarPx - bottomBarPx - counterPx

            val content = st.content ?: ""
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

            // 换章回到第一页（rememberPagerState 跨章保留旧页码会被钳到末页）
            LaunchedEffect(currentIndex) { pagerState.scrollToPage(0) }

            // 历史进度恢复：进入章节后按累计字数锚点跳到对应页（决策 C；消费一次后清零）
            LaunchedEffect(restore, content) {
                if (restore > 0 && content.isNotEmpty() && pages.isNotEmpty()) {
                    var acc = 0
                    var target = 0
                    for (i in pages.indices) {
                        acc += pages[i].content.length
                        if (acc >= restore) { target = i; break }
                        target = i
                    }
                    pagerState.scrollToPage(target)
                    vm.restoreProgress.value = 0
                }
            }

            // 进度持久化：页码变化时记 累计字数锚点 + 页索引
            LaunchedEffect(pagerState.currentPage, currentIndex, content) {
                if (st.content != null && pages.isNotEmpty()) {
                    var acc = 0
                    for (i in 0..pagerState.currentPage.coerceAtMost(pages.size - 1)) {
                        acc += pages[i].content.length
                    }
                    vm.record(currentIndex, content, progressAnchor = acc, page = pagerState.currentPage)
                }
            }

            // 音量键翻页（spec F23）：下=下一页/末页翻下一章，上=上一页/首页翻上一章
            DisposableEffect(pagerState, pages) {
                ReaderSettings.volumeKeyHandler = { dir ->
                    if (dir > 0) {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch { pagerState.scrollToPage(pagerState.currentPage + 1) }
                        } else if (currentIndex < flat.size - 1) {
                            vm.goTo(currentIndex + 1)
                        }
                    } else {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.scrollToPage(pagerState.currentPage - 1) }
                        } else if (currentIndex > 0) {
                            vm.goTo(currentIndex - 1)
                        }
                    }
                }
                onDispose { ReaderSettings.volumeKeyHandler = null }
            }

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
                                            if (currentIndex > 0) vm.goTo(currentIndex - 1)
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
                                        vm.goTo(currentIndex + 1)
                                    }
                                }
                                else -> showControls = !showControls
                            }
                        }
                    },
            ) {
                // 背景图层（spec §2.10 背景图占位）：底图缺席时以文字色渐变模拟纹理，透明度=设置滑杆
                if (ReaderSettings.backgroundOpacity > 0f) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    fg.copy(alpha = ReaderSettings.backgroundOpacity * 0.5f),
                                    Color.Transparent,
                                    fg.copy(alpha = ReaderSettings.backgroundOpacity * 0.5f),
                                ),
                            ),
                        )
                    }
                }
                // 宽屏：阅读列宽受限居中
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        st.loading || st.content == null && st.error == null ->
                            LoadingBlock()
                        st.error != null ->
                            ErrorBlock(message = st.error!!, onRefresh = { vm.goTo(currentIndex) })
                        else -> HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxSize(),
                            key = { it },
                        ) { pageIndex ->
                            val page = pages.getOrNull(pageIndex) ?: ReaderPage("", false)
                            if (page.isImage) {
                                ImagePage(
                                    imageUrl = page.content,
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
                        flat.getOrNull(currentIndex)?.second?.title ?: st.novelName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
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
            volumes = st.volumes,
            currentCid = flat.getOrNull(currentIndex)?.second?.cid ?: cid,
            heightFraction = 0.9f,
            currentHighlightColor = Color.Gray.copy(alpha = 0.3f),
            onDismiss = { showCatalog = false },
            onSelect = { sel ->
                showCatalog = false
                val idx = flat.indexOfFirst { it.second.cid == sel }
                if (idx >= 0) vm.goTo(idx)
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
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("$pageNum/$pageCount", fontSize = 10.sp, color = style.color, modifier = Modifier.alpha(0.3f))
        }
        Spacer(Modifier.height(with(density) { bottomPad.toDp() }))
    }
}

/** 插图页：`<!--image-->` 标记的 URL 经 Coil 加载（UA/Referer 已接），失败回落占位插画。 */
@Composable
private fun ImagePage(
    imageUrl: String,
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
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = "插图",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = { MockIllustration(seed = imageUrl) },
            )
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("$pageNum/$pageCount", fontSize = 10.sp, color = fg, modifier = Modifier.alpha(0.3f))
        }
        Spacer(Modifier.height(with(density) { bottomPad.toDp() }))
    }
}

/** 插图加载失败兜底：确定的渐变天空 + 山形剪影。 */
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
