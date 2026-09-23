package app.wild.android

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import app.wild.android.data.prefs.ThemeMode
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.remote.CfBypass
import app.wild.android.ui.navigation.WildNavShell
import app.wild.android.ui.reader.ReaderSettings
import app.wild.android.ui.theme.WildTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsStore: SettingsStore by inject()
    private val cfBypass: CfBypass by inject()
    private var latestIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestIntent.value = intent
        enableEdgeToEdge()
        // CF 绕过 WebView（1px 隐藏）+ 阅读器设置持久化装载
        cfBypass.attach(this)
        lifecycleScope.launch { ReaderSettings.attach(settingsStore) }
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

    override fun onDestroy() {
        cfBypass.detachFrom(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d("WildDeepLink", "onNewIntent data=${intent.data}")
        latestIntent.value = intent
    }

    /** spec F23：开启音量键翻页后，音量键交由前台阅读器处理（翻页/滚动）。 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ReaderSettings.volumeKeyPaging && event.action == KeyEvent.ACTION_DOWN) {
            val dir = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                else -> null
            }
            if (dir != null) {
                val handler = ReaderSettings.volumeKeyHandler
                if (handler != null) {
                    handler(dir)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
