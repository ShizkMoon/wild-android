package app.wild.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.data.mock.WildMock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class ParsedBlock {
    data class Text(val content: String) : ParsedBlock()
    data class Image(val marker: String) : ParsedBlock()
}

private fun parseBlocks(content: String): List<ParsedBlock> =
    content.split("\n")
        .filter { it.isNotBlank() }
        .map { raw ->
            val m = IMAGE_MARK_REGEX.find(raw)
            if (m != null) ParsedBlock.Image(m.groupValues[1]) else ParsedBlock.Text(raw)
        }

/**
 * HTML 阅读器 `/novel/reader` reader_type=html（spec §2.9）：
 * extendBodyBehindAppBar 半透明 AppBar + 整章 ListView 滚动 + 图片 4:3 contain
 * + 尾部「上一章/下一章」+ 点正文切全屏 + 自动滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollReaderScreen(aid: Int, cid: Int, onBack: () -> Unit) {
    val novel = remember(aid) { WildMock.novelInfo(aid) }
    val volumes = remember(aid) { WildMock.volumes(aid) }
    val flat = remember(volumes) { volumes.flatMap { v -> v.chapters.map { v.title to it } } }
    var currentIndex by rememberSaveable { mutableIntStateOf(flat.indexOfFirst { it.second.cid == cid }.coerceAtLeast(0)) }

    val dark = when (ReaderSettings.themeMode) {
        ReaderThemeMode.AUTO -> isSystemInDarkTheme()
        ReaderThemeMode.LIGHT -> false
        ReaderThemeMode.DARK -> true
    }
    val bg = if (dark) ReaderSettings.darkBackgroundColor else ReaderSettings.lightBackgroundColor
    val fg = if (dark) ReaderSettings.darkTextColor else ReaderSettings.lightTextColor

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 换章回到章首（listState 跨章保留旧滚动位置）
    LaunchedEffect(currentIndex) { listState.scrollToItem(0) }

    // 音量键滚动（spec F23）：HTML 阅读器 = 滚 0.8 屏；到章边界翻章
    DisposableEffect(listState) {
        ReaderSettings.volumeKeyHandler = { dir ->
            val info = listState.layoutInfo
            val page = (info.viewportEndOffset - info.viewportStartOffset) * 0.8f
            scope.launch {
                listState.scroll { scrollBy(dir * page) }
                val atStart = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                val atEnd = last != null && last.index >= listState.layoutInfo.totalItemsCount - 1
                if (dir > 0 && atEnd && currentIndex < flat.size - 1) currentIndex++
                if (dir < 0 && atStart && currentIndex > 0) currentIndex--
            }
        }
        onDispose { ReaderSettings.volumeKeyHandler = null }
    }

    // 滚屏时常亮（spec F24，默认开）
    val view = LocalView.current
    DisposableEffect(autoScroll, ReaderSettings.keepOnScroll) {
        if (autoScroll && ReaderSettings.keepOnScroll) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 自动滚动：每 interval ms 前进 speed px，到底自停（spec F22）
    LaunchedEffect(autoScroll, ReaderSettings.autoScrollSpeed, ReaderSettings.autoScrollInterval) {
        while (autoScroll) {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val canScroll = last != null && (last.index < info.totalItemsCount - 1 || last.offset + last.size > info.viewportEndOffset)
            if (!canScroll) { autoScroll = false; fullscreen = false; break }
            listState.scroll { scrollBy(ReaderSettings.autoScrollSpeed) }
            delay(ReaderSettings.autoScrollInterval.toLong())
        }
    }

    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val contentMaxWidth = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 640.dp
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 560.dp
        else -> Int.MAX_VALUE.dp
    }

    val content = remember(currentIndex) { WildMock.chapterContent(flat[currentIndex].second.title) }
    val blocks = remember(content) { parseBlocks(content) }

    Scaffold(
        containerColor = bg,
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(flat[currentIndex].second.title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    },
                    actions = {
                        IconButton(onClick = {
                            autoScroll = !autoScroll
                            if (autoScroll) fullscreen = true // 开启即强制全屏（spec）
                            else fullscreen = false
                        }) {
                            Icon(if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "自动滚动")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, "设置")
                        }
                        IconButton(onClick = { showCatalog = true }) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, "目录")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = bg.copy(alpha = 0.8f),
                        titleContentColor = fg,
                        navigationIconContentColor = fg,
                        actionIconContentColor = fg,
                    ),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { fullscreen = !fullscreen },
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentMaxWidth)
                    .align(Alignment.Center),
                contentPadding = PaddingValues(
                    start = ReaderSettings.leftPadding.dp,
                    end = ReaderSettings.rightPadding.dp,
                    top = padding.calculateTopPadding() + ReaderSettings.topBarHeight.dp,
                    bottom = ReaderSettings.bottomBarHeight.dp,
                ),
            ) {
                itemsIndexed(blocks) { i, block ->
                    when (block) {
                        is ParsedBlock.Text -> Text(
                            block.content,
                            style = TextStyle(
                                fontSize = (ReaderSettings.fontSize + if (i == 0) 2f else 0f).sp,
                                fontWeight = if (i == 0) FontWeight.Bold else null,
                                lineHeight = ReaderSettings.lineHeight.em,
                                letterSpacing = 0.5.sp,
                                color = fg,
                            ),
                            modifier = Modifier.padding(bottom = ReaderSettings.paragraphSpacing.dp),
                        )
                        is ParsedBlock.Image -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .padding(vertical = ReaderSettings.paragraphSpacing.dp),
                            contentAlignment = Alignment.Center,
                        ) { MockIllustration(seed = block.marker) }
                    }
                }
                item {
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                        ) { Text("上一章", color = fg) }
                        TextButton(
                            onClick = { if (currentIndex < flat.size - 1) currentIndex++ },
                            enabled = currentIndex < flat.size - 1,
                        ) { Text("下一章", color = fg) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showCatalog) {
        ChapterCatalogSheet(
            volumes = volumes,
            currentCid = flat[currentIndex].second.cid,
            heightFraction = 0.8f,
            currentHighlightColor = Color.Transparent, // HTML 阅读器：当前章 primary 色（spec §2.11）
            onDismiss = { showCatalog = false },
            onSelect = { sel ->
                showCatalog = false
                val idx = flat.indexOfFirst { it.second.cid == sel }
                if (idx >= 0) currentIndex = idx
            },
        )
    }
    if (showSettings) {
        ReaderSettingsSheet(html = true, onDismiss = { showSettings = false })
    }
}
