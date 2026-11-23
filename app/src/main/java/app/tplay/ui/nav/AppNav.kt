package app.tplay.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.tplay.ui.active.ActiveScreen
import app.tplay.ui.add.AddScreen
import app.tplay.ui.browse.BrowseScreen
import app.tplay.ui.calendar.CalendarScreen
import app.tplay.ui.discover.DiscoverScreen
import app.tplay.ui.library.LibraryScreen
import app.tplay.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val BROWSE_ROUTE = "browse"

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Add("add", "Add", Icons.Filled.Add),
    Discover("discover", "Discover", Icons.Filled.Explore),
    Library("library", "Library", Icons.Filled.PlayCircle),
    Active("active", "Active", Icons.Filled.Download),
    Calendar("calendar", "Calendar", Icons.Filled.CalendarMonth),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun AppNav(incomingMagnet: String?, onIncomingUsed: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var picked by remember { mutableStateOf<String?>(null) }
    val show: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    /** Switches to a bottom-bar tab the same way tapping it does, so tabs never stack on each other. */
    val goTab: (String) -> Unit = { route ->
        // Browse is a page on top of a tab, not a tab: drop it first so it is never saved into the tab's stack.
        if (nav.currentDestination?.route == BROWSE_ROUTE) nav.popBackStack()
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(incomingMagnet) {
        if (incomingMagnet != null) goTab(Dest.Add.route)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = { goTab(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Dest.Add.route, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(Dest.Add.route) {
                AddScreen(
                    incoming = picked ?: incomingMagnet,
                    onIncomingUsed = {
                        picked = null
                        onIncomingUsed()
                    },
                    onStarted = {
                        show("Sent to Real-Debrid")
                        goTab(Dest.Active.route)
                    },
                )
            }
            composable(Dest.Discover.route) {
                DiscoverScreen(
                    onOpenBrowser = { nav.navigate(BROWSE_ROUTE) { launchSingleTop = true } },
                    onStarted = {
                        show("Sent to Real-Debrid")
                        goTab(Dest.Active.route)
                    },
                    onMessage = show,
                )
            }
            composable(BROWSE_ROUTE) {
                BrowseScreen(
                    onMagnet = { magnet ->
                        picked = magnet
                        goTab(Dest.Add.route)
                    },
                    onDiscoverSite = { site ->
                        show("Discover now reads ${site.substringAfter("://")}")
                        nav.popBackStack(Dest.Discover.route, inclusive = false)
                    },
                )
            }
            composable(Dest.Library.route) { LibraryScreen(onMessage = show) }
            composable(Dest.Active.route) { ActiveScreen() }
            composable(Dest.Calendar.route) { CalendarScreen(onMessage = show) }
            composable(Dest.Settings.route) { SettingsScreen(onMessage = show) }
        }
    }
}
