package app.wild.android.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.data.mock.MockNovelInfo
import app.wild.android.data.mock.MockVolume
import app.wild.android.data.mock.WildMock
import app.wild.android.ui.components.CoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 小说详情 `/novel/info`（spec §2.7）：
 * AppBar「小说详情」+ [下载钮, 书架书签钮]；头图区 120×160 + 标题/作者(可点)/状态/动画化；
 * 统计行 spaceEvenly（更新/评论）；标签 chips；「继续阅读」通栏钮；简介 + 卷-章 Card。
 * 宽屏限定内容栏宽并居中（MD3 大屏可读性规范）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelInfoScreen(
    aid: Int,
    onBack: () -> Unit,
    onReviews: () -> Unit,
    onDownload: () -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onChapterClick: (Int) -> Unit,
    showBackButton: Boolean = true,
) {
    var loading by remember { mutableStateOf(true) }
    var inBookshelf by remember { mutableStateOf(aid % 4 == 0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(aid) {
        delay(350) // mock 加载
        loading = false
    }

    val info = remember(aid) { WildMock.novelInfo(aid) }
    val volumes = remember(aid) { WildMock.volumes(aid) }
    val history = remember(aid) { WildMock.histories.firstOrNull { it.novelId == aid } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小说详情") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    if (!loading) {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Outlined.Download, "下载")
                        }
                        IconButton(onClick = { inBookshelf = !inBookshelf }) {
                            Icon(
                                if (inBookshelf) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "书架",
                                tint = if (inBookshelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
            val maxWidth = when {
                sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 720.dp
                sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 600.dp
                else -> Int.MAX_VALUE.dp
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
                        NovelHeader(info, onAuthorClick)
                        StatRow(info, onReviews)
                        TagWrap(info, onTagClick)
                        if (history != null) {
                            ContinueReadButton(
                                chapterTitle = history.chapterTitle,
                                onClick = { onChapterClick(aid * 1000 + 1) },
                            )
                        }
                        NovelDescription(info)
                    }
                }
                volumes.forEach { volume ->
                    item {
                        Box(
                            modifier = Modifier
                                .widthIn(max = maxWidth)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            VolumeCard(volume, onChapterClick)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NovelHeader(info: MockNovelInfo, onAuthorClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(120f / 160f)
                .clip(RoundedCornerShape(8.dp)),
        ) { CoverImage(info.title) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                "作者：${info.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onAuthorClick(info.author) },
            )
            Spacer(Modifier.height(4.dp))
            Text("状态：${info.status}", style = MaterialTheme.typography.bodyMedium)
            if (info.isAnimated) {
                Spacer(Modifier.height(4.dp))
                Text("动画化", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatRow(info: MockNovelInfo, onReviews: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(icon = { Icon(Icons.Outlined.Update, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }, text = "更新: ${info.finUpdate}")
        StatItem(
            icon = { Icon(Icons.AutoMirrored.Outlined.Comment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
            text = "评论",
            onClick = onReviews,
        )
    }
}

@Composable
private fun StatItem(icon: @Composable () -> Unit, text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagWrap(info: MockNovelInfo, onTagClick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        info.tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable { onTagClick(tag) },
            ) {
                Text(
                    tag,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ContinueReadButton(chapterTitle: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
            .height(48.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("继续阅读 - $chapterTitle", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NovelDescription(info: MockNovelInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text("简介", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        // spec：flutter_html 渲染，body 14sp onSurface；mock 阶段纯文本 + 段落间距
        HtmlLite(info.introduce)
    }
}

/** 简介 HTML 的最小渲染：<p>/<br> 分段，14sp。真实 HTML 渲染在 Stage 4 评估。 */
@Composable
fun HtmlLite(html: String, modifier: Modifier = Modifier) {
    val paragraphs = remember(html) {
        html.replace(Regex("<br\\s*/?>"), "\n")
            .split(Regex("</?p[^>]*>"))
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotEmpty() }
    }
    Column(modifier) {
        paragraphs.forEach { p ->
            Text(
                p,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun VolumeCard(volume: MockVolume, onChapterClick: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(volume.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(top = 8.dp))
            volume.chapters.forEach { ch ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterClick(ch.cid) }
                        .padding(horizontal = 0.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ch.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun NovelInfoPreview() {
    app.wild.android.ui.theme.WildTheme {
        NovelInfoScreen(aid = 2, onBack = {}, onReviews = {}, onDownload = {}, onAuthorClick = {}, onTagClick = {}, onChapterClick = {})
    }
}
