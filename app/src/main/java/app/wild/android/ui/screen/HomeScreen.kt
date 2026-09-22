package app.wild.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 通用占位屏：标题栏 + 提示文案 + 当前窗口尺寸类别。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(title: String, hint: String, body: String) {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "WidthSizeClass: " + when {
                    sizeClass.isWidthAtLeastBreakpoint(
                        androidx.window.core.layout.WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
                    ) -> "expanded"
                    sizeClass.isWidthAtLeastBreakpoint(
                        androidx.window.core.layout.WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                    ) -> "medium"
                    else -> "compact"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    // 占位：演示复刻基准的「区块标题 + 3 列封面网格」布局（spec §2.4）。
    Scaffold(
        topBar = { TopAppBar(title = { Text("轻小说文库") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PLACEHOLDER_NOVELS) { novel ->
                NovelCoverCard(title = novel)
            }
        }
    }
}

/** 封面占位卡，宽高比与复刻基准一致（207:307 ≈ 0.674）。 */
@Composable
private fun NovelCoverCard(title: String) {
    Card {
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(207f / 307f),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        title.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

private val PLACEHOLDER_NOVELS = listOf(
    "示例小说 A", "示例小说 B", "示例小说 C",
    "示例小说 D", "示例小说 E", "示例小说 F",
    "示例小说 G", "示例小说 H", "示例小说 I",
)
