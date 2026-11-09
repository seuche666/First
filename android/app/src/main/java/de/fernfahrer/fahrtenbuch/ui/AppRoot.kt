package de.fernfahrer.fahrtenbuch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.fernfahrer.fahrtenbuch.ui.editor.EditorMode
import de.fernfahrer.fahrtenbuch.ui.editor.EditorScreen
import de.fernfahrer.fahrtenbuch.ui.history.HistoryScreen
import de.fernfahrer.fahrtenbuch.ui.home.HomeScreen
import de.fernfahrer.fahrtenbuch.ui.settings.SettingsScreen
import de.fernfahrer.fahrtenbuch.ui.stats.StatsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("heute", "Heute", Icons.Rounded.Today),
    HISTORY("verlauf", "Verlauf", Icons.Rounded.History),
    STATS("auswertung", "Auswertung", Icons.Rounded.BarChart),
    SETTINGS("einstellungen", "Einstellungen", Icons.Rounded.Settings),
}

private const val EDITOR_ROUTE = "eintrag?id={id}&mode={mode}&fresh={fresh}"

/** Öffnet den Eintrag-Bildschirm: bestehender Tag (id) oder neuer Eintrag für heute. */
fun NavHostController.openEditor(id: Long = 0L, mode: EditorMode = EditorMode.NORMAL, fresh: Boolean = false) {
    navigate("eintrag?id=$id&mode=${mode.name}&fresh=$fresh")
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = Tab.entries.any { it.route == currentRoute }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    vm = vm,
                    onOpenEditor = { id, mode -> nav.openEditor(id, mode) },
                )
            }
            composable(Tab.HISTORY.route) {
                HistoryScreen(vm = vm, onOpenDay = { nav.openEditor(it) }, onAdd = { nav.openEditor(fresh = true) })
            }
            composable(Tab.STATS.route) { StatsScreen(vm = vm) }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm = vm) }
            composable(
                route = EDITOR_ROUTE,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("mode") { type = NavType.StringType; defaultValue = EditorMode.NORMAL.name },
                    navArgument("fresh") { type = NavType.BoolType; defaultValue = false },
                ),
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)) },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                EditorScreen(
                    onDone = { message ->
                        message?.let(vm::showMessage)
                        nav.popBackStack()
                    },
                )
            }
        }
    }
}
