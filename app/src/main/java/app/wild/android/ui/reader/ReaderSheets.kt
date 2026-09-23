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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wild.android.data.remote.Volume

/**
 * 目录弹层（spec §2.11）：普通阅读器 0.9 高可拖拽；HTML 0.8。
 * 卷名分组 + 章 ListTile，当前章高亮并自动滚到位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterCatalogSheet(
    volumes: List<Volume>,
    currentCid: Int,
    heightFraction: Float,
    currentHighlightColor: Color,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(heightFraction)) {
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
            // 打开时滚到当前章（按章 56/卷 48 估高）
            LaunchedEffect(Unit) {
                var index = 0
                var found = 0
                volumes.forEach { v ->
                    index++
                    v.chapters.forEach { c ->
                        if (c.cid == currentCid) found = index
                        index++
                    }
                }
                listState.scrollToItem(found.coerceAtLeast(0))
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
                                        color = if (currentHighlightColor == Color.Transparent && selected)
                                            MaterialTheme.colorScheme.primary
                                        else Color.Unspecified,
                                    )
                                },
                                modifier = Modifier
                                    .clickable { onSelect(ch.cid) }
                                    .then(
                                        if (selected && currentHighlightColor != Color.Transparent)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(2f / 3f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))

            // 1. 阅读器类型
            SettingsLabel("阅读器类型")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = ReaderSettings.readerType == ReaderType.NORMAL,
                    onClick = { ReaderSettings.readerType = ReaderType.NORMAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Outlined.Book, null, Modifier.size(18.dp)) },
                ) { Text("普通阅读器") }
                SegmentedButton(
                    selected = ReaderSettings.readerType == ReaderType.HTML,
                    onClick = { ReaderSettings.readerType = ReaderType.HTML },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.Html, null, Modifier.size(18.dp)) },
                ) { Text("HTML阅读器") }
            }

            // 2. 字体大小 14-24 步1
            SliderRow("字体大小", ReaderSettings.fontSize, 14f..24f, 10, { "${it.toInt()}" }) {
                ReaderSettings.fontSize = it
            }
            // 3. 段落间距：普通 2/17/32 三档；HTML 16-32 步2
            if (html) {
                SliderRow("段落间距", ReaderSettings.paragraphSpacing, 16f..32f, 7, { "${it.toInt()}" }) {
                    ReaderSettings.paragraphSpacing = it
                }
            } else {
                SliderRow(
                    "段落间距", ReaderSettings.paragraphSpacing, 2f..32f, 1,
                    { "${it.toInt()}" },
                ) { ReaderSettings.paragraphSpacing = it }
            }
            // 4. 行高 1.0-2.0 步0.05
            SliderRow("行高", ReaderSettings.lineHeight, 1f..2f, 20, { String.format("%.1f", it) }) {
                ReaderSettings.lineHeight = it
            }
            // 5-8. 四边距
            SliderRow("顶部边距", ReaderSettings.topBarHeight, 0f..100f, 20, { "${it.toInt()}" }) {
                ReaderSettings.topBarHeight = it
            }
            SliderRow("底部边距", ReaderSettings.bottomBarHeight, 0f..100f, 20, { "${it.toInt()}" }) {
                ReaderSettings.bottomBarHeight = it
            }
            SliderRow("左边距", ReaderSettings.leftPadding, 0f..50f, 25, { "${it.toInt()}" }) {
                ReaderSettings.leftPadding = it
            }
            SliderRow("右边距", ReaderSettings.rightPadding, 0f..50f, 25, { "${it.toInt()}" }) {
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
                "背景透明度", ReaderSettings.backgroundOpacity, 0f..1f, 25,
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
                    "滚动速度", ReaderSettings.autoScrollSpeed, 0.5f..3f, 10,
                    { String.format("%.2f", it) },
                ) { ReaderSettings.autoScrollSpeed = it }
                SliderRow(
                    "滚动间隔(ms)", ReaderSettings.autoScrollInterval.toFloat(), 8f..32f, 6,
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

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label: ${display(value)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        Slider(
            value = value,
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
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(c)
                                        .clickable { apply(c); pickTarget = null },
                                )
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
