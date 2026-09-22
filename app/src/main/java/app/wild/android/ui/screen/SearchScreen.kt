package app.wild.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.data.mock.WildMock
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.NovelGrid

/**
 * 搜索页 `/search`（spec §2.13）：AppBar「搜索」+ actions SegmentedButton[书名|作者]；
 * 16 padding 搜索框；三态：历史 ListTile / 结果网格 / 「输入关键词开始搜索」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialType: String,
    initialKey: String,
    onBack: () -> Unit,
    onNovelClick: (Int) -> Unit,
) {
    var searchType by rememberSaveable { mutableStateOf(if (initialType == "author") "author" else "articlename") }
    var input by rememberSaveable { mutableStateOf(initialKey) }
    var submittedKey by rememberSaveable { mutableStateOf(initialKey) }
    var histories by remember { mutableStateOf(WildMock.searchHistories) }
    val results = remember(submittedKey, searchType) {
        if (submittedKey.isBlank()) null else WildMock.search(searchType, submittedKey)
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
                                onClick = { searchType = value; input = ""; submittedKey = "" },
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
                        submittedKey = input
                        histories = listOf(app.wild.android.data.mock.MockSearchHistory(searchType, input)) +
                            histories.filter { it.searchKey != input }
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            when {
                // 1. 输入为空且有历史 → 历史列表
                input.isBlank() && histories.isNotEmpty() && results == null -> {
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
                                    submittedKey = h.searchKey
                                },
                            )
                        }
                    }
                }
                // 2. 有结果 → 网格
                results != null -> {
                    if (results!!.isEmpty()) EmptyBlock("没有找到相关小说")
                    else NovelGrid(results!!, onNovelClick = { onNovelClick(it.aid) }, columns = columns)
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
    app.wild.android.ui.theme.WildTheme { SearchScreen("", "", {}, {}) }
}
