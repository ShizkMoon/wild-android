package app.wild.android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.ui.vm.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * `/category` 路由页（spec：详情页 tag chip 跳入，外层包 AppBar(「分类」)）。
 * 内部复用首页分类 tab：SegmentedButton + 分组标签选择器 + 网格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    initialTag: String,
    showAppBar: Boolean,
    onBack: () -> Unit,
    onNovelClick: (Int) -> Unit,
    vm: HomeViewModel = koinViewModel(),
) {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 6
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 4
        else -> 3
    }
    Scaffold(
        topBar = {
            if (showAppBar) {
                TopAppBar(
                    title = { Text("分类") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoryTab(
                vm = vm,
                onNovelClick = onNovelClick,
                onCategoryClick = {},
                columns = columns,
                initialTag = initialTag,
            )
        }
    }
}
