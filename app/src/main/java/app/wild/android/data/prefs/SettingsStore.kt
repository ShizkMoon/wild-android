package app.wild.android.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 应用级设置（spec §3.5 `property` 表的 DataStore 等价物）。
 * 通用键值读写 + 命名键；阅读器细分项（§2.10）经 [get]/[put] 访问。
 */
class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val apiHostKey = stringPreferencesKey("api_host")
    private val userAgentKey = stringPreferencesKey("user_agent")

    // ---- 命名键 ----

    /** 默认 API Host；留空 = `https://www.wenku8.net`（spec F39）。 */
    val apiHost: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[apiHostKey].orEmpty()
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    val userAgent: Flow<String> = context.dataStore.data.map { it[userAgentKey].orEmpty() }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setApiHost(host: String) {
        context.dataStore.edit { it[apiHostKey] = host.trim() }
    }

    suspend fun setUserAgent(ua: String) {
        context.dataStore.edit { it[userAgentKey] = ua }
    }

    // ---- 通用键值（阅读器设置等） ----

    suspend fun getString(key: String, def: String = ""): String =
        context.dataStore.data.first()[stringPreferencesKey(key)] ?: def

    suspend fun putString(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun getFloat(key: String, def: Float): Float =
        context.dataStore.data.first()[floatPreferencesKey(key)] ?: def

    suspend fun putFloat(key: String, value: Float) {
        context.dataStore.edit { it[floatPreferencesKey(key)] = value }
    }

    suspend fun getInt(key: String, def: Int): Int =
        context.dataStore.data.first()[intPreferencesKey(key)] ?: def

    suspend fun putInt(key: String, value: Int) {
        context.dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun getLong(key: String, def: Long): Long =
        context.dataStore.data.first()[longPreferencesKey(key)] ?: def

    suspend fun putLong(key: String, value: Long) {
        context.dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    suspend fun getBoolean(key: String, def: Boolean): Boolean =
        context.dataStore.data.first()[booleanPreferencesKey(key)] ?: def

    suspend fun putBoolean(key: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    fun flowString(key: String, def: String = ""): Flow<String> =
        context.dataStore.data.map { it[stringPreferencesKey(key)] ?: def }

    companion object {
        const val DEFAULT_API_HOST = "https://www.wenku8.net"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
