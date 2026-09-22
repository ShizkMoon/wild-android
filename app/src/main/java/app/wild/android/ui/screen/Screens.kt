package app.wild.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.wild.android.R
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.prefs.ThemeMode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun BookshelfScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_bookshelf),
        hint = stringResource(R.string.adaptive_hint),
        body = stringResource(R.string.placeholder_bookshelf),
    )
}

@Composable
fun HistoryScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_history),
        hint = stringResource(R.string.adaptive_hint),
        body = stringResource(R.string.placeholder_history),
    )
}

/**
 * 更多页：复刻基准的 4 个入口（下载/账户/设置/关于），骨架阶段仅「主题模式」可用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(settingsStore: SettingsStore = koinInject()) {
    val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Download, null) },
                headlineContent = { Text(stringResource(R.string.more_download)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Person, null) },
                headlineContent = { Text(stringResource(R.string.more_account)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Settings, null) },
                headlineContent = { Text(stringResource(R.string.more_settings)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, null) },
                headlineContent = { Text(stringResource(R.string.more_about)) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 骨架期可交互项：主题三态（跟随系统/浅色/深色），写进 DataStore。
            Text(
                "主题模式",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                            }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = themeMode == mode,
                            onCheckedChange = { checked ->
                                if (checked) scope.launch { settingsStore.setThemeMode(mode) }
                            },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
