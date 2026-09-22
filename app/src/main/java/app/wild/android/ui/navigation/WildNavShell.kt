package app.wild.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.R
import app.wild.android.ui.screen.BookshelfScreen
import app.wild.android.ui.screen.HistoryScreen
import app.wild.android.ui.screen.HomeScreen
import app.wild.android.ui.screen.MoreScreen

/**
 * 顶层导航目的地。对应 wild 原 App `/home` 的 4 个 Tab（首页/书架/历史/更多）。
 */
enum class WildDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    BOOKSHELF("bookshelf", R.string.nav_bookshelf, Icons.Filled.Book),
    HISTORY("history", R.string.nav_history, Icons.Filled.History),
    MORE("more", R.string.nav_more, Icons.Filled.MoreHoriz),
}

/**
 * 「屏幕尺寸 → 导航形态」自适应外壳。
 * WindowSizeClass width: compact → bottom NavigationBar；medium/expanded → NavigationRail。
 * 折叠屏展开、平板横屏会自然落到 NavigationRail；高度极窄的横屏手机走 NavigationBar
 * 由 NavigationSuiteScaffold 内部约束处理，这里只锁定宽度维度。
 */
@Composable
fun WildNavShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val sizeClass = adaptiveInfo.windowSizeClass
    val navSuiteType = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            NavigationSuiteType.NavigationRail
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            WildDestination.entries.forEach { dest ->
                item(
                    selected = currentRoute == dest.route,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        },
        layoutType = navSuiteType,
    ) {
        NavHost(
            navController = navController,
            startDestination = WildDestination.HOME.route,
        ) {
            composable(WildDestination.HOME.route) { HomeScreen() }
            composable(WildDestination.BOOKSHELF.route) { BookshelfScreen() }
            composable(WildDestination.HISTORY.route) { HistoryScreen() }
            composable(WildDestination.MORE.route) { MoreScreen() }
        }
    }
}
