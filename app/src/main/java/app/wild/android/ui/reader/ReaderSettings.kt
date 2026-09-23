package app.wild.android.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.wild.android.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 阅读器设置（spec §2.10 全部项）：会话级单列态 + SettingsStore 持久化。
 * 键名沿用原 App `property` 表（reader_* / auto_scroll_* / _volumeControlProperty /
 * _screenUpOn*Property），除背景透明度（spec 明确不持久化）外改即写回（防抖 400ms）。
 * [attach] 由 MainActivity.onCreate 调用加载持久值。
 */
object ReaderSettings {

    private var store: SettingsStore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeJobs = ConcurrentHashMap<String, Job>()

    private fun persist(key: String, block: suspend SettingsStore.() -> Unit) {
        val s = store ?: return
        writeJobs[key]?.cancel()
        writeJobs[key] = scope.launch {
            delay(400)
            s.block()
        }
    }

    /** 启动时灌入持久值（幂等）。 */
    suspend fun attach(s: SettingsStore) {
        if (store != null) return
        store = s
        readerType = if (s.getInt(K_TYPE, 0) == 1) ReaderType.HTML else ReaderType.NORMAL
        fontSize = s.getFloat(K_FONT, 18f)
        paragraphSpacing = s.getFloat(K_PSPACING, 24f)
        lineHeight = s.getFloat(K_LHEIGHT, 1.3f)
        topBarHeight = s.getFloat(K_TOP, 56f)
        bottomBarHeight = s.getFloat(K_BOTTOM, 56f)
        leftPadding = s.getFloat(K_LEFT, 16f)
        rightPadding = s.getFloat(K_RIGHT, 16f)
        themeMode = ReaderThemeMode.entries.getOrElse(s.getInt(K_THEME, 0)) { ReaderThemeMode.AUTO }
        lightBackgroundColor = Color(s.getInt(K_LBG, 0xFFFFFFFF.toInt()))
        lightTextColor = Color(s.getInt(K_LFG, 0xDD000000.toInt()))
        darkBackgroundColor = Color(s.getInt(K_DBG, 0xFF1A1A1A.toInt()))
        darkTextColor = Color(s.getInt(K_DFG, 0xFFE0E0E0.toInt()))
        autoScrollSpeed = s.getFloat(K_AS_SPEED, 1.0f)
        autoScrollInterval = s.getInt(K_AS_INTERVAL, 16)
        volumeKeyPaging = s.getBoolean(K_VOLUME, false)
        keepOnReading = s.getBoolean(K_KEEP_READ, false)
        keepOnScroll = s.getBoolean(K_KEEP_SCROLL, true)
    }

    var readerType by persistedEnum(K_TYPE, ReaderType.NORMAL)

    var fontSize by persistedFloat(K_FONT, 18f)
    var paragraphSpacing by persistedFloat(K_PSPACING, 24f)
    var lineHeight by persistedFloat(K_LHEIGHT, 1.3f)
    var topBarHeight by persistedFloat(K_TOP, 56f)
    var bottomBarHeight by persistedFloat(K_BOTTOM, 56f)
    var leftPadding by persistedFloat(K_LEFT, 16f)
    var rightPadding by persistedFloat(K_RIGHT, 16f)

    var themeMode by persistedEnum(K_THEME, ReaderThemeMode.AUTO)
    var lightBackgroundColor by persistedColor(K_LBG, Color.White)
    var lightTextColor by persistedColor(K_LFG, Color(0xDD000000))
    var darkBackgroundColor by persistedColor(K_DBG, Color(0xFF1A1A1A))
    var darkTextColor by persistedColor(K_DFG, Color(0xFFE0E0E0))

    /** 背景透明度：spec 明确不持久化，重启回 0.1 */
    var backgroundOpacity by mutableStateOf(0.1f)

    var autoScrollSpeed by persistedFloat(K_AS_SPEED, 1.0f)
    var autoScrollInterval by persistedInt(K_AS_INTERVAL, 16)
    var volumeKeyPaging by persistedBool(K_VOLUME, false)
    var keepOnReading by persistedBool(K_KEEP_READ, false)
    var keepOnScroll by persistedBool(K_KEEP_SCROLL, true)

    /** 音量键翻页回调（前台阅读器注册；普通=翻页/翻章，HTML=滚0.8屏/翻章）。MainActivity.dispatchKeyEvent 分发。 */
    var volumeKeyHandler: ((direction: Int) -> Unit)? = null

    /** 重置为默认（spec：只重置主题 + 四边距，不动字号/行高/段距/透明度/背景图）。 */
    fun resetDefaults() {
        themeMode = ReaderThemeMode.AUTO
        lightBackgroundColor = Color.White
        lightTextColor = Color(0xDD000000)
        darkBackgroundColor = Color(0xFF1A1A1A)
        darkTextColor = Color(0xFFE0E0E0)
        topBarHeight = 56f
        bottomBarHeight = 56f
        leftPadding = 16f
        rightPadding = 16f
    }

    // ---- 持久化委托 ----

    private fun persistedFloat(key: String, initial: Float) =
        object : androidx.compose.runtime.MutableState<Float> {
            private var v by mutableStateOf(initial)
            override var value: Float
                get() = v
                set(value) {
                    v = value
                    persist(key) { putFloat(key, value) }
                }
            override fun component1() = v
            override fun component2(): (Float) -> Unit = { value = it }
        }

    private fun persistedInt(key: String, initial: Int) =
        object : androidx.compose.runtime.MutableState<Int> {
            private var v by mutableStateOf(initial)
            override var value: Int
                get() = v
                set(value) {
                    v = value
                    persist(key) { putInt(key, value) }
                }
            override fun component1() = v
            override fun component2(): (Int) -> Unit = { value = it }
        }

    private fun persistedBool(key: String, initial: Boolean) =
        object : androidx.compose.runtime.MutableState<Boolean> {
            private var v by mutableStateOf(initial)
            override var value: Boolean
                get() = v
                set(value) {
                    v = value
                    persist(key) { putBoolean(key, value) }
                }
            override fun component1() = v
            override fun component2(): (Boolean) -> Unit = { value = it }
        }

    private fun persistedColor(key: String, initial: Color) =
        object : androidx.compose.runtime.MutableState<Color> {
            private var v by mutableStateOf(initial)
            override var value: Color
                get() = v
                set(value) {
                    v = value
                    persist(key) { putInt(key, value.toArgb()) }
                }
            override fun component1() = v
            override fun component2(): (Color) -> Unit = { value = it }
        }

    private inline fun <reified E : Enum<E>> persistedEnum(key: String, initial: E) =
        object : androidx.compose.runtime.MutableState<E> {
            private var v by mutableStateOf(initial)
            override var value: E
                get() = v
                set(value) {
                    v = value
                    persist(key) { putInt(key, value.ordinal) }
                }
            override fun component1() = v
            override fun component2(): (E) -> Unit = { value = it }
        }

    // ---- 键名（沿用原 App property 表） ----
    private const val K_TYPE = "reader_type" // 0 normal / 1 html
    private const val K_FONT = "font_size"
    private const val K_PSPACING = "paragraph_spacing"
    private const val K_LHEIGHT = "line_height"
    private const val K_TOP = "top_bar_height"
    private const val K_BOTTOM = "bottom_bar_height"
    private const val K_LEFT = "left_padding"
    private const val K_RIGHT = "right_padding"
    private const val K_THEME = "reader_theme_mode"
    private const val K_LBG = "reader_light_bg_color"
    private const val K_LFG = "reader_light_text_color"
    private const val K_DBG = "reader_dark_bg_color"
    private const val K_DFG = "reader_dark_text_color"
    private const val K_AS_SPEED = "auto_scroll_speed"
    private const val K_AS_INTERVAL = "auto_scroll_interval"
    private const val K_VOLUME = "_volumeControlProperty"
    private const val K_KEEP_READ = "_screenUpOnReadingProperty"
    private const val K_KEEP_SCROLL = "_screenUpOnScrollProperty"
}

enum class ReaderType { NORMAL, HTML }

enum class ReaderThemeMode { AUTO, LIGHT, DARK }
