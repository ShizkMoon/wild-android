package app.wild.android.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 阅读器设置会话级持有（spec §2.10 全部项；Stage 4 落 property 表/DataStore）。
 * 单列态模拟原 App 的 Cubit；各键与默认值严格对齐 spec。
 */
object ReaderSettings {
    var readerType by mutableStateOf(ReaderType.NORMAL)
    var fontSize by mutableFloatStateOf(18f)
    var paragraphSpacing by mutableFloatStateOf(24f)
    var lineHeight by mutableFloatStateOf(1.3f)
    var topBarHeight by mutableFloatStateOf(56f)
    var bottomBarHeight by mutableFloatStateOf(56f)
    var leftPadding by mutableFloatStateOf(16f)
    var rightPadding by mutableFloatStateOf(16f)
    var themeMode by mutableStateOf(ReaderThemeMode.AUTO)
    var lightBackgroundColor by mutableStateOf(Color.White)
    var lightTextColor by mutableStateOf(Color(0xDD000000))
    var darkBackgroundColor by mutableStateOf(Color(0xFF1A1A1A))
    var darkTextColor by mutableStateOf(Color(0xFFE0E0E0))
    /** 背景透明度：spec 明确不持久化，重启回 0.1 */
    var backgroundOpacity by mutableFloatStateOf(0.1f)
    var autoScrollSpeed by mutableFloatStateOf(1.0f)
    var autoScrollInterval by mutableIntStateOf(16)
    var volumeKeyPaging by mutableStateOf(false)
    var keepOnReading by mutableStateOf(false)
    var keepOnScroll by mutableStateOf(true)

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
}

enum class ReaderType { NORMAL, HTML }

enum class ReaderThemeMode { AUTO, LIGHT, DARK }
