package app.wild.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import app.wild.android.data.prefs.ThemeMode
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.ui.navigation.WildNavShell
import app.wild.android.ui.theme.WildTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsStore: SettingsStore by inject()
    private var latestIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestIntent.value = intent
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
                WildNavShell(intent = latestIntent.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d("WildDeepLink", "onNewIntent data=${intent.data}")
        latestIntent.value = intent
    }
}
