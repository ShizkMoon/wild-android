package app.wild.android.ui.navigation

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.window.core.layout.WindowSizeClass
import app.wild.android.R
import app.wild.android.ui.reader.PagedReaderScreen
import app.wild.android.ui.reader.ReaderSettings
import app.wild.android.ui.reader.ReaderType
import app.wild.android.ui.reader.ScrollReaderScreen
import app.wild.android.ui.screen.AboutScreen
import app.wild.android.ui.screen.AccountScreen
import app.wild.android.ui.screen.BookshelfScreen
import app.wild.android.ui.screen.CategoryScreen
import app.wild.android.ui.screen.DownloadDetailScreen
import app.wild.android.ui.screen.DownloadSelectScreen
import app.wild.android.ui.screen.DownloadsScreen
import app.wild.android.ui.screen.HistoryScreen
import app.wild.android.ui.screen.HomeScreen
import app.wild.android.ui.screen.InitScreen
import app.wild.android.ui.screen.LoginScreen
import app.wild.android.ui.screen.MoreScreen
import app.wild.android.ui.screen.NovelInfoScreen
import app.wild.android.ui.screen.ReviewsScreen
import app.wild.android.ui.screen.SearchScreen
import app.wild.android.ui.screen.SettingsScreen

/** 顶层导航目的地。对应 wild 原 App `/home` 的 4 个 Tab（首页/书架/历史/更多）。 */
enum class WildDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val iconOutlined: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BOOKSHELF("bookshelf", R.string.nav_bookshelf, Icons.Filled.Book, Icons.Outlined.Book),
    HISTORY("history", R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    MORE("more", R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}

object WildRoutes {
    const val INIT = "init"
    const val LOGIN = "login"
    const val NOVEL = "novel/{aid}"
    const val REVIEWS = "novel/{aid}/reviews"
    const val DOWNLOAD_SELECT = "novel/{aid}/download-select"
    const val READER = "reader/{aid}/{cid}"
    const val SEARCH = "search?type={type}&key={key}"
    const val CATEGORY = "category?tag={tag}"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val ABOUT = "about"
    const val DOWNLOADS = "downloads"
    const val DOWNLOAD_DETAIL = "download/{aid}"

    fun novel(aid: Int) = "novel/$aid"
    fun reviews(aid: Int) = "novel/$aid/reviews"
    fun downloadSelect(aid: Int) = "novel/$aid/download-select"
    fun reader(aid: Int, cid: Int) = "reader/$aid/$cid"
    fun search(type: String = "", key: String = "") = "search?type=$type&key=$key"
    fun category(tag: String) = "category?tag=$tag"
    fun downloadDetail(aid: Int) = "download/$aid"
    fun deepLink(route: String) = "wild://app/$route"
}

private fun tabRouteSet(): Set<String> = WildDestination.entries.map { it.route }.toSet()

/**
 * 「屏幕尺寸 → 导航形态」自适应外壳（spec §2.3 主框架）。
 * - 顶层 4 tab：compact → NavigationBar，medium/expanded → NavigationRail。
 * - 非顶层路由（详情/阅读器/搜索等）：NavigationSuiteType.None，导航件让位给内容。
 * 折叠屏展开/窗口拉伸会自然切换形态且 NavHost 不重建、tab 状态保留。
 */
@Composable
fun WildNavShell(intent: Intent? = null) {
    val navController = rememberNavController()
    LaunchedEffect(intent) {
        intent?.let {
            val handled = navController.handleDeepLink(it)
            android.util.Log.d("WildDeepLink", "handleDeepLink($handled) data=${it.data}")
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val onTabRoute = currentRoute in tabRouteSet()

    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val navSuiteType = when {
        !onTabRoute -> NavigationSuiteType.None
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
                    icon = {
                        Icon(
                            if (currentRoute == dest.route) dest.icon else dest.iconOutlined,
                            contentDescription = null,
                        )
                    },
                    label = { Text(androidx.compose.ui.res.stringResource(dest.labelRes)) },
                    // 更多页「新版本」角标（spec F41/§11）：Stage 3 mock 恒无更新，不显示
                )
            }
        },
        layoutType = navSuiteType,
    ) {
        WildNavHost(navController)
    }
}

@Composable
private fun WildNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = WildRoutes.INIT,
    ) {
        composable(
            WildRoutes.INIT,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("init") }),
        ) {
            InitScreen(onFinished = {
                navController.navigate(WildDestination.HOME.route) {
                    popUpTo(WildRoutes.INIT) { inclusive = true }
                }
            })
        }
        composable(
            WildRoutes.LOGIN,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("login") }),
        ) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(WildDestination.HOME.route) {
                    popUpTo(0) { inclusive = true }
                }
            })
        }

        composable(
            WildDestination.HOME.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("home") }),
        ) {
            HomeScreen(
                onSearchClick = { navController.navigate(WildRoutes.search()) },
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
                onCategoryClick = { tag -> navController.navigate(WildRoutes.category(tag)) },
            )
        }
        composable(
            WildDestination.BOOKSHELF.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("bookshelf") }),
        ) {
            BookshelfScreen(onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) })
        }
        composable(
            WildDestination.HISTORY.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("history") }),
        ) {
            HistoryScreen(
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
                onContinueRead = { h -> navController.navigate(WildRoutes.reader(h.novelId, h.novelId * 1000 + h.progressPage + 1)) },
            )
        }
        composable(
            WildDestination.MORE.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("more") }),
        ) {
            MoreScreen(
                onDownloads = { navController.navigate(WildRoutes.DOWNLOADS) },
                onAccount = { navController.navigate(WildRoutes.ACCOUNT) },
                onSettings = { navController.navigate(WildRoutes.SETTINGS) },
                onAbout = { navController.navigate(WildRoutes.ABOUT) },
            )
        }

        composable(
            WildRoutes.NOVEL,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("novel/{aid}") }),
        ) { entry ->
            val aid = entry.arguments?.getInt("aid") ?: 0
            NovelInfoScreen(
                aid = aid,
                onBack = { navController.popBackStack() },
                onReviews = { navController.navigate(WildRoutes.reviews(aid)) },
                onDownload = { navController.navigate(WildRoutes.downloadSelect(aid)) },
                onAuthorClick = { author -> navController.navigate(WildRoutes.search("author", author)) },
                onTagClick = { tag -> navController.navigate(WildRoutes.category(tag)) },
                onChapterClick = { cid -> navController.navigate(WildRoutes.reader(aid, cid)) },
            )
        }
        composable(
            WildRoutes.REVIEWS,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("novel/{aid}/reviews") }),
        ) { entry ->
            ReviewsScreen(
                aid = entry.arguments?.getInt("aid") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            WildRoutes.DOWNLOAD_SELECT,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("novel/{aid}/download-select") }),
        ) { entry ->
            DownloadSelectScreen(
                aid = entry.arguments?.getInt("aid") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            WildRoutes.READER,
            arguments = listOf(
                navArgument("aid") { type = NavType.IntType },
                navArgument("cid") { type = NavType.IntType },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("reader/{aid}/{cid}") }),
        ) { entry ->
            val aid = entry.arguments?.getInt("aid") ?: 0
            val cid = entry.arguments?.getInt("cid") ?: 0
            // spec：阅读器类型在路由层分流（reader_type）
            when (ReaderSettings.readerType) {
                ReaderType.NORMAL -> PagedReaderScreen(
                    aid = aid, cid = cid,
                    onBack = { navController.popBackStack() },
                )
                ReaderType.HTML -> ScrollReaderScreen(
                    aid = aid, cid = cid,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            WildRoutes.SEARCH,
            arguments = listOf(
                navArgument("type") { defaultValue = "" },
                navArgument("key") { defaultValue = "" },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("search?type={type}&key={key}") }),
        ) { entry ->
            SearchScreen(
                initialType = entry.arguments?.getString("type").orEmpty(),
                initialKey = entry.arguments?.getString("key").orEmpty(),
                onBack = { navController.popBackStack() },
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
            )
        }
        composable(
            WildRoutes.CATEGORY,
            arguments = listOf(navArgument("tag") { defaultValue = "" }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("category?tag={tag}") }),
        ) { entry ->
            CategoryScreen(
                initialTag = entry.arguments?.getString("tag").orEmpty(),
                showAppBar = true,
                onBack = { navController.popBackStack() },
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
            )
        }
        composable(
            WildRoutes.SETTINGS,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("settings") }),
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(WildRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(
            WildRoutes.ACCOUNT,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("account") }),
        ) {
            AccountScreen(onBack = { navController.popBackStack() })
        }
        composable(
            WildRoutes.ABOUT,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("about") }),
        ) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(
            WildRoutes.DOWNLOADS,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("downloads") }),
        ) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { aid -> navController.navigate(WildRoutes.downloadDetail(aid)) },
            )
        }
        composable(
            WildRoutes.DOWNLOAD_DETAIL,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("download/{aid}") }),
        ) { entry ->
            DownloadDetailScreen(
                aid = entry.arguments?.getInt("aid") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenChapter = { cid ->
                    // 下载详情页永远进普通阅读器（spec §9）
                    navController.navigate(WildRoutes.reader(entry.arguments?.getInt("aid") ?: 0, cid))
                },
            )
        }
    }
}
