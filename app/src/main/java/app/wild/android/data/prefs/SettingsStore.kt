package app.wild.android.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 应用级设置（spec §3.5 `property` 表的 DataStore 等价物）。
 * 阅读器细分项（字号/行高/边距/配色/背景图等 §2.10）在 Stage 3/4 逐键补齐，
 * 骨架期先落主题模式与 API Host 两个边界键。
 */
class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val apiHostKey = stringPreferencesKey("api_host")

    /** 默认 API Host；留空 = `https://www.wenku8.net`（spec F39）。 */
    val apiHost: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[apiHostKey].orEmpty()
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setApiHost(host: String) {
        context.dataStore.edit { it[apiHostKey] = host.trim() }
    }

    companion object {
        const val DEFAULT_API_HOST = "https://www.wenku8.net"
    }
}
