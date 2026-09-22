package app.wild.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import app.wild.android.data.prefs.ThemeMode
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.ui.navigation.WildNavShell
import app.wild.android.ui.theme.WildTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsStore: SettingsStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            WildTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                WildNavShell()
            }
        }
    }
}
