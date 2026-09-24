package app.wild.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val WildSeed = Color(0xFF4A5FA5)

// SZKM-67 §6.1：补齐 M3 tonal 层级令牌（surfaceContainer* / outline* / surfaceTint
// / inverse*），容器一律走 tonal 层级 + outlineVariant 细描边，不用投影。
private val LightScheme = lightColorScheme(
    primary = Color(0xFF4A5FA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF001159),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171A2C),
    tertiary = Color(0xFF76546E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF4A5FA5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F3FC),
    surfaceContainer = Color(0xFFF0EDF6),
    surfaceContainerHigh = Color(0xFFEAE7F1),
    surfaceContainerHighest = Color(0xFFE5E1EB),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDBD8E1),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF2F0F7),
    inversePrimary = Color(0xFFB6C4FF),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF24306F),
    primaryContainer = Color(0xFF324787),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFE5BAD7),
    onTertiary = Color(0xFF44273E),
    tertiaryContainer = Color(0xFF5D3D55),
    onTertiaryContainer = Color(0xFFFFD7F1),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceTint = Color(0xFFB6C4FF),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF33343A),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF4A5FA5),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

/** SZKM-67 §3.1：全站统一形状节奏——小件紧、大件圆。 */
val WildShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // 状态胶囊/徽章
    small = RoundedCornerShape(8.dp),        // 验证码底板、chip
    medium = RoundedCornerShape(12.dp),      // 封面卡/信息卡/下载卡
    large = RoundedCornerShape(16.dp),       // 大按钮
    extraLarge = RoundedCornerShape(28.dp),  // sheet/dialog
)

/**
 * 状态栏/导航栏图标色的 per-screen 覆盖钩子（S-8）：
 * 阅读器等自带配色的屏用 [StatusBarIconAppearance] 声明深色图标与否，
 * 未覆盖的屏回落到 App 明暗主题。
 */
val LocalSystemBarDarkIcons = compositionLocalOf<androidx.compose.runtime.MutableState<Boolean?>?> { null }

/**
 * 声明当前屏的系统栏图标色。[darkIcons]=true 画深色图标（浅色底用）。
 * 离开该屏自动恢复 App 默认（跟随主题明暗）。
 */
@Composable
fun StatusBarIconAppearance(darkIcons: Boolean) {
    val state = LocalSystemBarDarkIcons.current ?: return
    DisposableEffect(darkIcons) {
        state.value = darkIcons
        onDispose { state.value = null }
    }
}

/**
 * Wild 全局主题（spec §6.1）。
 * 默认用 WildSeed 蓝系 scheme（含完整 surfaceContainer / outline / surfaceTint 令牌）；
 * dynamicColor 需显式开启（S-6：默认 true 会让整套配色在 API31+ 成死代码）。
 * 明暗由 [darkTheme] 控制（跟随系统/浅/深三态见 SettingsStore）。
 * 注：material3 1.4.0 的 MaterialExpressiveTheme/MotionScheme 仍是 internal，
 * spec 描述的 expressive 令牌不可达——motion 统一节奏由各屏显式 spring 承担。
 */
@Composable
fun WildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    val barDarkIcons = androidx.compose.runtime.remember { mutableStateOf<Boolean?>(null) }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val darkIcons = barDarkIcons.value ?: !darkTheme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalSystemBarDarkIcons provides barDarkIcons) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = WildShapes,
            typography = Typography(),
            content = content,
        )
    }
}

/** 便捷别名：全站统一的容器细描边（§3.3 ②）。 */
val CardOutline
    @Composable get() = androidx.compose.foundation.BorderStroke(
        1.dp, MaterialTheme.colorScheme.outlineVariant,
    )
