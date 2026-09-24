package app.wild.android.ui.navigation

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import app.wild.android.ui.components.LocalNavAnimatedVisibilityScope
import app.wild.android.ui.components.LocalSharedTransitionScope
import app.wild.android.ui.reader.PagedReaderScreen
import app.wild.android.ui.reader.ReaderSettings
import app.wild.android.ui.reader.ReaderType
import app.wild.android.ui.reader.ScrollReaderScreen
import app.wild.android.ui.screen.AboutScreen
import app.wild.android.ui.screen.AccountScreen
import app.wild.android.ui.screen.BookshelfScreen
import app.wild.android.ui.screen.CategoryScreen
import app.wild.android.ui.screen.CfVerifyScreen
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
    const val CF_VERIFY = "cf-verify"

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

    // CF 验证联动（SZKM-68）：CfSession 需要用户手动过验证时全局弹可见验证页；
    // 收起由 CfVerifyScreen 自己负责（onBack=popBackStack），这里只负责进、不负责出——
    // 两侧都 pop 会把 init 也弹掉，NavHost 变白屏。
    val cfSession = org.koin.compose.koinInject<app.wild.android.data.remote.CfSession>()
    val cfState by cfSession.state.collectAsState()
    LaunchedEffect(cfState) {
        if (cfState is app.wild.android.data.remote.CfState.NeedsUser &&
            navController.currentDestination?.route != WildRoutes.CF_VERIFY
        ) {
            navController.navigate(WildRoutes.CF_VERIFY)
        }
    }

    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val navSuiteType = when {
        !onTabRoute -> NavigationSuiteType.None
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    // §3.3⑤：导航件容器走 surfaceContainer token，与内容 surface 分层
    val scheme = MaterialTheme.colorScheme
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
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = scheme.surfaceContainer,
            navigationRailContainerColor = scheme.surfaceContainer,
        ),
        containerColor = scheme.surface,
    ) {
        WildNavHost(navController)
    }
}

/**
 * 每个 `composable` 目的地内容的作用域注入包装：
 * navigation-compose 2.9.4 没有 sharedElement 参数，把 AnimatedContentScope 经
 * [LocalNavAnimatedVisibilityScope] 下沉，屏内共享元素修饰符自取（M-2）。
 */
@Composable
private fun AnimatedContentScope.NavScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
        content()
    }
}

/** M-1：全局空间转场——前进 slide-in 右入 + fade，回退镜像；位移 spring 无 overshoot。 */
private val NavSpring = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WildNavHost(navController: NavHostController) {
    // M-2：SharedTransitionLayout 包 NavHost，作用域经 Local 下沉到各屏
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(
            navController = navController,
            startDestination = WildRoutes.INIT,
            enterTransition = { slideInHorizontally(NavSpring) { it / 4 } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally(NavSpring) { it / 4 } + fadeOut() },
        ) {
        composable(
            WildRoutes.INIT,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("init") }),
        ) { NavScope {
            InitScreen(
                onFinished = {
                    navController.navigate(WildDestination.HOME.route) {
                        popUpTo(WildRoutes.INIT) { inclusive = true }
                    }
                },
                onNeedLogin = {
                    navController.navigate(WildRoutes.LOGIN) {
                        popUpTo(WildRoutes.INIT) { inclusive = true }
                    }
                },
            )
        } }
        composable(
            WildRoutes.LOGIN,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("login") }),
        ) { NavScope {
            LoginScreen(onLoginSuccess = {
                navController.navigate(WildDestination.HOME.route) {
                    popUpTo(0) { inclusive = true }
                }
            })
        } }

        composable(
            WildDestination.HOME.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("home") }),
        ) { NavScope {
            HomeScreen(
                onSearchClick = { navController.navigate(WildRoutes.search()) },
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
                onCategoryClick = { tag -> navController.navigate(WildRoutes.category(tag)) },
                onReviews = { aid -> navController.navigate(WildRoutes.reviews(aid)) },
                onDownloadSelect = { aid -> navController.navigate(WildRoutes.downloadSelect(aid)) },
                onAuthorClick = { author -> navController.navigate(WildRoutes.search("author", author)) },
                onChapterClick = { aid, cid -> navController.navigate(WildRoutes.reader(aid, cid)) },
            )
        } }
        composable(
            WildDestination.BOOKSHELF.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("bookshelf") }),
        ) { NavScope {
            BookshelfScreen(onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) })
        } }
        composable(
            WildDestination.HISTORY.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("history") }),
        ) { NavScope {
            HistoryScreen(
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
                onContinueRead = { h -> navController.navigate(WildRoutes.reader(h.novelId, h.chapterId)) },
            )
        } }
        composable(
            WildDestination.MORE.route,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("more") }),
        ) { NavScope {
            MoreScreen(
                onDownloads = { navController.navigate(WildRoutes.DOWNLOADS) },
                onAccount = { navController.navigate(WildRoutes.ACCOUNT) },
                onSettings = { navController.navigate(WildRoutes.SETTINGS) },
                onAbout = { navController.navigate(WildRoutes.ABOUT) },
            )
        } }

        composable(
            WildRoutes.NOVEL,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("novel/{aid}") }),
        ) { entry -> NavScope {
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
        } }
        composable(
            WildRoutes.REVIEWS,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("novel/{aid}/reviews") }),
        ) { entry -> NavScope {
            ReviewsScreen(
                aid = entry.arguments?.getInt("aid") ?: 0,
                onBack = { navController.popBackStack() },
            )
        } }
        composable(
            WildRoutes.DOWNLOAD_SELECT,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("novel/{aid}/download-select") }),
        ) { entry -> NavScope {
            DownloadSelectScreen(
                aid = entry.arguments?.getInt("aid") ?: 0,
                onBack = { navController.popBackStack() },
            )
        } }
        composable(
            WildRoutes.READER,
            arguments = listOf(
                navArgument("aid") { type = NavType.IntType },
                navArgument("cid") { type = NavType.IntType },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("reader/{aid}/{cid}") }),
        ) { entry -> NavScope {
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
        } }
        composable(
            WildRoutes.SEARCH,
            arguments = listOf(
                navArgument("type") { defaultValue = "" },
                navArgument("key") { defaultValue = "" },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("search?type={type}&key={key}") }),
        ) { entry -> NavScope {
            SearchScreen(
                initialType = entry.arguments?.getString("type").orEmpty(),
                initialKey = entry.arguments?.getString("key").orEmpty(),
                onBack = { navController.popBackStack() },
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
            )
        } }
        composable(
            WildRoutes.CATEGORY,
            arguments = listOf(navArgument("tag") { defaultValue = "" }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("category?tag={tag}") }),
        ) { entry -> NavScope {
            CategoryScreen(
                initialTag = entry.arguments?.getString("tag").orEmpty(),
                showAppBar = true,
                onBack = { navController.popBackStack() },
                onNovelClick = { aid -> navController.navigate(WildRoutes.novel(aid)) },
            )
        } }
        composable(
            WildRoutes.SETTINGS,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("settings") }),
        ) { NavScope {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(WildRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        } }
        composable(
            WildRoutes.ACCOUNT,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("account") }),
        ) { NavScope {
            AccountScreen(onBack = { navController.popBackStack() })
        } }
        composable(
            WildRoutes.ABOUT,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("about") }),
        ) { NavScope {
            AboutScreen(onBack = { navController.popBackStack() })
        } }
        composable(
            WildRoutes.DOWNLOADS,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("downloads") }),
        ) { NavScope {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { aid -> navController.navigate(WildRoutes.downloadDetail(aid)) },
            )
        } }
        composable(
            WildRoutes.DOWNLOAD_DETAIL,
            arguments = listOf(navArgument("aid") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("download/{aid}") }),
        ) { entry -> NavScope {
            DownloadDetailScreen(
                aid = entry.arguments?.getInt("aid") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenChapter = { cid ->
                    // 下载详情页永远进普通阅读器（spec §9）
                    navController.navigate(WildRoutes.reader(entry.arguments?.getInt("aid") ?: 0, cid))
                },
            )
        } }
        composable(
            WildRoutes.CF_VERIFY,
            deepLinks = listOf(navDeepLink { uriPattern = WildRoutes.deepLink("cf-verify") }),
        ) { NavScope {
            CfVerifyScreen(onBack = { navController.popBackStack() })
        } }
        }
        }
    }
}
