package my.edu.um.rainready.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import my.edu.um.rainready.R
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.HomeRepository
import my.edu.um.rainready.data.MapRepository
import my.edu.um.rainready.data.RouteChecker
import my.edu.um.rainready.data.SampleHomeRepository
import my.edu.um.rainready.data.SampleMapRepository
import my.edu.um.rainready.data.SampleRouteChecker

/** The four bottom-bar tabs from the Figma nav bar. */
enum class Tab(val route: String, @param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME("home", R.string.tab_home, R.drawable.ic_nav_home),
    MAP("map", R.string.tab_map, R.drawable.ic_nav_map),
    NOTICES("notices", R.string.tab_notices, R.drawable.ic_nav_notices),
    SETTINGS("settings", R.string.tab_settings, R.drawable.ic_nav_settings),
}

/** Screens outside the bottom bar. */
object Routes {
    const val ROUTE_CHECK = "route_check"
    const val SIGN_IN = "sign_in"
}

/**
 * The app shell: bottom bar + navigation between the tabs.
 * The bottom bar shows on the four tabs and hides on full-screen flows such as the route check.
 */
@Composable
fun RainReadyApp(
    homeRepository: HomeRepository = remember { SampleHomeRepository() },
    mapRepository: MapRepository = remember { SampleMapRepository() },
    routeChecker: RouteChecker? = null,
) {
    val context = LocalContext.current
    // The stand-in route checker uses the same sample data as the map, until Jingying's engine is connected.
    val checker = routeChecker ?: remember {
        SampleRouteChecker(CampusData.load(context), SampleMapRepository.rainyDay(), SampleMapRepository.causes)
    }
    // A route from "Show on map", highlighted on the Map tab until the user clears it.
    var mapRoute by remember { mutableStateOf<MapRoute?>(null) }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    // Keep one copy of each tab and remember its scroll position.
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            // Same colours as the Figma nav bar.
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.HOME.route) {
                val state by homeRepository.home.collectAsState(initial = null)
                state?.let { HomeScreen(it, onChooseBuilding = { nav.navigate(Routes.ROUTE_CHECK) }) }
            }
            composable(Tab.MAP.route) {
                val state by mapRepository.map.collectAsState(initial = null)
                MapScreen(state, route = mapRoute, onClearRoute = { mapRoute = null })
            }
            composable(Tab.NOTICES.route) { NoticesScreen() }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    onSignIn = { nav.navigate(Routes.SIGN_IN) }
                )
            }

            composable(Routes.SIGN_IN) {
                SignInScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ROUTE_CHECK) {
                val state by mapRepository.map.collectAsState(initial = null)
                RouteCheckScreen(
                    checker = checker,
                    mapState = state,
                    onBack = { nav.popBackStack() },
                    onShowOnMap = { route ->
                        mapRoute = route
                        nav.navigate(Tab.MAP.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
