package app.wild.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wild.android.data.remote.Volume
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * 目录弹层（spec §2.11）：普通阅读器 0.9 高可拖拽；HTML 0.8。
 * 卷名分组 + 章 ListTile，当前章高亮并滚到视口中央。
 * [currentHighlightColor]：非 null 时选中章铺底色（普通阅读器）；null 时选中章用
 * primary 字色（HTML 阅读器，spec §2.11）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterCatalogSheet(
    volumes: List<Volume>,
    currentCid: Int,
    heightFraction: Float,
    currentHighlightColor: Color?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    // G-18/skipPartiallyExpanded 显式化：内容定高，不允许停在半展开锚点
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(heightFraction).navigationBarsPadding()) {
            // 头部：menu_book + 「目录」+ 关闭
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text("目录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("关闭")
                }
            }
            HorizontalDivider()
            val listState = rememberLazyListState()
            val itemPx = with(LocalDensity.current) { 56.dp.toPx() }
            // 打开时把当前章滚到视口中央（等首帧布局拿到 viewport 再动）
            LaunchedEffect(Unit) {
                var index = 0
                var found = -1
                volumes.forEach { v ->
                    index++
                    v.chapters.forEach { c ->
                        if (c.cid == currentCid) found = index
                        index++
                    }
                }
                if (found < 0) return@LaunchedEffect
                snapshotFlow { listState.layoutInfo.viewportSize.height }
                    .first { it > 0 }
                listState.scrollToItem(found)
                listState.animateScrollToItem(
                    found,
                    scrollOffset = -(listState.layoutInfo.viewportSize.height / 2 - itemPx / 2).toInt(),
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                volumes.forEach { volume ->
                    item(key = "v-${volume.name}") {
                        Text(
                            volume.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    volume.chapters.forEach { ch ->
                        item(key = "c-${ch.cid}") {
                            val selected = ch.cid == currentCid
                            ListItem(
                                headlineContent = {
                                    Text(
                                        ch.title,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentHighlightColor == null && selected)
                                            MaterialTheme.colorScheme.primary
                                        else Color.Unspecified,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier
                                    .clickable { onSelect(ch.cid) }
                                    .then(
                                        if (selected && currentHighlightColor != null)
                                            Modifier.background(currentHighlightColor)
                                        else Modifier,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 阅读设置弹层（spec §2.10，两阅读器共用结构，高=2/3 屏，上圆角 16）。
 * [html] = HTML 阅读器差异项（段距 16-32 步2 + 自动滚动设置）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(html: Boolean, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(2f / 3f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp)
                // G-18：sheet 内容让出导航条 inset
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))

            // 1. 阅读器类型
            SettingsLabel("阅读器类型")
            var pendingTypeHint by remember { mutableStateOf(false) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = ReaderSettings.readerType == ReaderType.NORMAL,
                    onClick = {
                        if (ReaderSettings.readerType != ReaderType.NORMAL) pendingTypeHint = true
                        ReaderSettings.readerType = ReaderType.NORMAL
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Outlined.Book, null, Modifier.size(18.dp)) },
                ) { Text("普通阅读器") }
                SegmentedButton(
                    selected = ReaderSettings.readerType == ReaderType.HTML,
                    onClick = {
                        if (ReaderSettings.readerType != ReaderType.HTML) pendingTypeHint = true
                        ReaderSettings.readerType = ReaderType.HTML
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.Html, null, Modifier.size(18.dp)) },
                ) { Text("HTML阅读器") }
            }
            // M-9：切换对当前已打开的阅读器无即时效果，给明确反馈
            if (pendingTypeHint) {
                Text(
                    "已切换，下次进入章节生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 2. 字体大小 14-24 步1
            SliderRow("字体大小", ReaderSettings.fontSize, 14f..24f, step = 1f, { "${it.toInt()}" }) {
                ReaderSettings.fontSize = it
            }
            // 3. 段落间距：普通 2/17/32 三档；HTML 16-32 步2
            if (html) {
                SliderRow("段落间距", ReaderSettings.paragraphSpacing, 16f..32f, step = 2f, { "${it.toInt()}" }) {
                    ReaderSettings.paragraphSpacing = it
                }
            } else {
                // 三档吸附：持久值不在刻度上时显示就近档，拖动落在 2/17/32
                val snapped = snapToSteps(ReaderSettings.paragraphSpacing, 2f..32f, 15f)
                SliderRow(
                    "段落间距", snapped, 2f..32f, step = 15f,
                    { "${it.toInt()}" },
                ) { ReaderSettings.paragraphSpacing = it }
            }
            // 4. 行高 1.0-2.0 步0.05
            SliderRow("行高", ReaderSettings.lineHeight, 1f..2f, step = 0.05f, { String.format("%.2f", it) }) {
                ReaderSettings.lineHeight = it
            }
            // 5-8. 四边距（步4：默认值 56/16 都在刻度上）
            SliderRow("顶部边距", ReaderSettings.topBarHeight, 0f..100f, step = 4f, { "${it.toInt()}" }) {
                ReaderSettings.topBarHeight = it
            }
            SliderRow("底部边距", ReaderSettings.bottomBarHeight, 0f..100f, step = 4f, { "${it.toInt()}" }) {
                ReaderSettings.bottomBarHeight = it
            }
            SliderRow("左边距", ReaderSettings.leftPadding, 0f..50f, step = 2f, { "${it.toInt()}" }) {
                ReaderSettings.leftPadding = it
            }
            SliderRow("右边距", ReaderSettings.rightPadding, 0f..50f, step = 2f, { "${it.toInt()}" }) {
                ReaderSettings.rightPadding = it
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 9. 主题模式
            SettingsLabel("主题模式")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("自动" to ReaderThemeMode.AUTO, "浅色" to ReaderThemeMode.LIGHT, "深色" to ReaderThemeMode.DARK)
                    .forEachIndexed { i, (label, mode) ->
                        SegmentedButton(
                            selected = ReaderSettings.themeMode == mode,
                            onClick = { ReaderSettings.themeMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        ) { Text(label) }
                    }
            }

            // 10-11. 浅色/深色配色
            ThemeColorRow(
                label = "浅色主题",
                bg = ReaderSettings.lightBackgroundColor,
                fg = ReaderSettings.lightTextColor,
                onBg = { ReaderSettings.lightBackgroundColor = it },
                onFg = { ReaderSettings.lightTextColor = it },
            )
            ThemeColorRow(
                label = "深色主题",
                bg = ReaderSettings.darkBackgroundColor,
                fg = ReaderSettings.darkTextColor,
                onBg = { ReaderSettings.darkBackgroundColor = it },
                onFg = { ReaderSettings.darkTextColor = it },
            )

            // 12. 背景透明度（不持久化）
            SliderRow(
                "背景透明度", ReaderSettings.backgroundOpacity, 0f..1f, step = 0.05f,
                { "${(it * 100).toInt()}%" },
            ) { ReaderSettings.backgroundOpacity = it }

            // 13. 背景图片设置（mock 占位）
            SettingsLabel("背景图片设置")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("设置浅色背景", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("设置深色背景", style = MaterialTheme.typography.labelMedium)
                }
            }

            // HTML 阅读器独有：自动滚动设置
            if (html) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsLabel("自动滚动设置")
                SliderRow(
                    "滚动速度", ReaderSettings.autoScrollSpeed, 0.5f..3f, step = 0.1f,
                    { String.format("%.1f", it) },
                ) { ReaderSettings.autoScrollSpeed = it }
                SliderRow(
                    "滚动间隔(ms)", ReaderSettings.autoScrollInterval.toFloat(), 8f..32f, step = 4f,
                    { "${it.toInt()}" },
                ) { ReaderSettings.autoScrollInterval = it.toInt() }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = { ReaderSettings.resetDefaults() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("重置为默认") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** 把 [value] 吸附到 range 内 step 的最近整数倍（用于初值不在刻度上的兜底显示）。 */
private fun snapToSteps(value: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    val n = ((value - range.start) / step).roundToInt().coerceIn(0, ((range.endInclusive - range.start) / step).roundToInt())
    return range.start + n * step
}

/**
 * P0-5：`Slider.steps` 语义是「端点间分段数-1」，这里统一改传步长 [step]，
 * 内部换算成 steps，保证滑杆只停在刻度上、标签值与落点一致。
 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label: ${display(value)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(min = 110.dp),
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeColorRow(
    label: String,
    bg: Color,
    fg: Color,
    onBg: (Color) -> Unit,
    onFg: (Color) -> Unit,
) {
    var pickTarget by remember { mutableStateOf<((Color) -> Unit)?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { pickTarget = onBg }) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(bg))
            Spacer(Modifier.width(4.dp))
            Text("背景颜色", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { pickTarget = onFg }) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(4.dp))
            Text("文字颜色", style = MaterialTheme.typography.labelMedium)
        }
    }
    pickTarget?.let { apply ->
        // 简化色板：常用候选色网格（原 App 用 flutter_colorpicker；mock 期给固定色板）
        AlertDialog(
            onDismissRequest = { pickTarget = null },
            title = { Text("选择颜色") },
            text = {
                Column {
                    listOf(
                        listOf(Color.White, Color(0xFFF5F5DC), Color(0xFFE0E0E0), Color(0xFF1A1A1A)),
                        listOf(Color.Black, Color(0xDD000000), Color(0xFFE0E0E0), Color(0xFF3E5F8A)),
                        listOf(Color(0xFFFFF8E7), Color(0xFFEDE6D6), Color(0xFF5B4636), Color(0xFF2E4A3D)),
                    ).forEach { row ->
                        Row {
                            row.forEach { c ->
                                // G-14：clickable 放外层 Box（48dp 热区），内层才是色块
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .semantics { contentDescription = "颜色 #${Integer.toHexString(c.toArgb())}" }
                                        .clickable { apply(c); pickTarget = null },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickTarget = null }) { Text("取消") } },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 500)
@Composable
private fun SettingsSheetPreview() {
    app.wild.android.ui.theme.WildTheme {
        Column(Modifier.fillMaxSize()) { ReaderSettingsSheet(html = false, onDismiss = {}) }
    }
}
