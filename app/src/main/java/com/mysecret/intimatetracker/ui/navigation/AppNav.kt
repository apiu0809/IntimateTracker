package com.mysecret.intimatetracker.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.ui.calendar.CalendarScreen
import com.mysecret.intimatetracker.ui.dictionary.DictionaryScreen
import com.mysecret.intimatetracker.ui.history.HistoryScreen
import com.mysecret.intimatetracker.ui.more.AboutScreen
import com.mysecret.intimatetracker.ui.more.ChangelogScreen
import com.mysecret.intimatetracker.ui.more.LicensesScreen
import com.mysecret.intimatetracker.ui.more.PrivacyScreen
import com.mysecret.intimatetracker.ui.prefs.PreferencesScreen
import com.mysecret.intimatetracker.ui.more.MoreScreen
import com.mysecret.intimatetracker.ui.partner.PartnerEditScreen
import com.mysecret.intimatetracker.ui.partner.PartnerListScreen
import com.mysecret.intimatetracker.ui.record.AddRecordScreen
import com.mysecret.intimatetracker.ui.record.RecordDetailScreen
import com.mysecret.intimatetracker.ui.sync.WebDavSettingsScreen
import com.mysecret.intimatetracker.ui.stats.StatisticsScreen

object Routes {
    const val CALENDAR = "calendar"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val PARTNERS = "partners"
    const val MORE = "more"

    const val PARTNER_NEW = "partner/new"
    const val PARTNER_EDIT = "partner/{partnerId}"
    const val DICTIONARY = "dict/{kind}"
    const val RECORD_NEW = "record/new"
    const val RECORD_DETAIL = "record/{recordId}"
    const val RECORD_EDIT = "record/{recordId}/edit"
    const val WEBDAV = "webdav"
    const val ABOUT = "more/about"
    const val ABOUT_CHANGELOG = "more/about/changelog"
    const val ABOUT_PRIVACY = "more/about/privacy"
    const val ABOUT_LICENSES = "more/about/licenses"
    const val PREFS = "more/prefs"

    fun partnerEdit(id: String) = "partner/$id"
    fun dictionary(kind: DictionaryKind) = "dict/${kind.route}"
}

private data class TabItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.CALENDAR, R.string.tab_calendar, Icons.Outlined.CalendarMonth),
    TabItem(Routes.HISTORY, R.string.tab_history, Icons.Outlined.History),
    TabItem(Routes.STATS, R.string.tab_stats, Icons.Outlined.Insights),
    TabItem(Routes.PARTNERS, R.string.tab_partner, Icons.Outlined.PeopleAlt),
    TabItem(Routes.MORE, R.string.tab_more, Icons.Outlined.MoreHoriz),
)

private val tabRoutes = tabs.mapTo(HashSet()) { it.route }

/** 全屏模态编辑 / 详情页：保持底部上滑的转场。 */
private val editorRoutes = setOf(
    Routes.PARTNER_NEW,
    Routes.PARTNER_EDIT,
    Routes.RECORD_NEW,
    Routes.RECORD_DETAIL,
    Routes.RECORD_EDIT,
)

/** 从 tab 下钻的普通二级页：统一从右侧滑入、返回时滑出。 */
private val secondaryRoutes = setOf(
    Routes.PREFS,
    Routes.ABOUT,
    Routes.ABOUT_CHANGELOG,
    Routes.ABOUT_PRIVACY,
    Routes.ABOUT_LICENSES,
    Routes.WEBDAV,
    Routes.DICTIONARY,
)

private const val TAB_FADE_OUT = 90
private const val TAB_FADE_IN = 210
private const val PAGE_SLIDE = 300

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            SharedTransitionLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isTabRoute) 0f else 1f),
            ) {
            val sharedScope = this
            NavHost(
                navController = navController,
                startDestination = Routes.CALENDAR,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        from in tabRoutes && to in tabRoutes ->
                            fadeIn(tween(TAB_FADE_IN, delayMillis = TAB_FADE_OUT))
                        to in secondaryRoutes ->
                            slideInHorizontally(
                                animationSpec = tween(PAGE_SLIDE, easing = FastOutSlowInEasing),
                                initialOffsetX = { it },
                            ) + fadeIn(tween(220))
                        else -> fadeIn(tween(180))
                    }
                },
                exitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        // 编辑页从底部盖上时，让下面的 tab 留在原位，避免闪一下空背景
                        from in tabRoutes && to in editorRoutes -> ExitTransition.None
                        from in tabRoutes -> fadeOut(tween(TAB_FADE_OUT))
                        else -> slideOutHorizontally(
                            animationSpec = tween(PAGE_SLIDE, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it / 4 },
                        ) + fadeOut(tween(160))
                    }
                },
                popEnterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        from in editorRoutes && to in tabRoutes -> EnterTransition.None
                        from in tabRoutes && to in tabRoutes ->
                            fadeIn(tween(TAB_FADE_IN, delayMillis = TAB_FADE_OUT))
                        from in secondaryRoutes && to in tabRoutes -> fadeIn(tween(200))
                        else -> slideInHorizontally(
                            animationSpec = tween(PAGE_SLIDE, easing = FastOutSlowInEasing),
                            initialOffsetX = { -it / 4 },
                        ) + fadeIn(tween(200))
                    }
                },
                popExitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        from in secondaryRoutes -> slideOutHorizontally(
                            animationSpec = tween(PAGE_SLIDE, easing = FastOutSlowInEasing),
                            targetOffsetX = { it },
                        ) + fadeOut(tween(180))
                        from in tabRoutes && to in tabRoutes -> fadeOut(tween(TAB_FADE_OUT))
                        else -> fadeOut(tween(120))
                    }
                },
            ) {
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onAdd = { navController.navigate(Routes.RECORD_NEW) },
                    onOpenRecord = { id -> navController.navigate("record/$id") },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenRecord = { id -> navController.navigate("record/$id") })
            }
            composable(Routes.STATS) { StatisticsScreen() }
            composable(Routes.PARTNERS) {
                PartnerListScreen(
                    onAdd = { navController.navigate(Routes.PARTNER_NEW) },
                    onOpen = { id -> navController.navigate(Routes.partnerEdit(id)) },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenPrefs = { navController.navigate(Routes.PREFS) },
                    onOpenDictionary = { kind -> navController.navigate(Routes.dictionary(kind)) },
                    onOpenWebDav = { navController.navigate(Routes.WEBDAV) },
                )
            }
            composable(Routes.PARTNER_NEW,
                enterTransition = { slideInVertically(tween(220), initialOffsetY = { it }) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(tween(220), targetOffsetY = { it }) },) {
                PartnerEditScreen(partnerId = null, onDone = { navController.popBackStack() })
            }
            composable(Routes.PARTNER_EDIT,
                enterTransition = { slideInVertically(tween(220), initialOffsetY = { it }) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(tween(220), targetOffsetY = { it }) },) { entry ->
                PartnerEditScreen(
                    partnerId = entry.arguments?.getString("partnerId"),
                    onDone = { navController.popBackStack() },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                )
            }
            composable(Routes.RECORD_NEW,
                enterTransition = { slideInVertically(tween(220), initialOffsetY = { it }) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(tween(220), targetOffsetY = { it }) },) {
                AddRecordScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.PREFS) {
                PreferencesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = { navController.navigate(Routes.ABOUT_PRIVACY) },
                    onOpenChangelog = { navController.navigate(Routes.ABOUT_CHANGELOG) },
                    onOpenLicenses = { navController.navigate(Routes.ABOUT_LICENSES) },
                )
            }
            composable(Routes.ABOUT_CHANGELOG) {
                ChangelogScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT_PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT_LICENSES) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.WEBDAV) {
                WebDavSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECORD_DETAIL,
                enterTransition = { slideInVertically(tween(220), initialOffsetY = { it }) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(tween(220), targetOffsetY = { it }) },) { entry ->
                val id = entry.arguments?.getString("recordId").orEmpty()
                RecordDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { recordId -> navController.navigate("record/$recordId/edit") },
                )
            }
            composable(Routes.RECORD_EDIT,
                enterTransition = { slideInVertically(tween(220), initialOffsetY = { it }) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(tween(220), targetOffsetY = { it }) },) {
                AddRecordScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.DICTIONARY) { entry ->
                DictionaryScreen(
                    kind = DictionaryKind.fromRoute(entry.arguments?.getString("kind")),
                    onBack = { navController.popBackStack() },
                )
            }
            }
            }
            AnimatedVisibility(
                visible = isTabRoute,
                enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 5 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 5 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.navigationBarsPadding(),
        containerColor = com.mysecret.intimatetracker.ui.theme.Surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = com.mysecret.intimatetracker.ui.theme.AccentPill,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
