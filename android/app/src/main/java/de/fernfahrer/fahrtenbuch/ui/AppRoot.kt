package de.fernfahrer.fahrtenbuch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.fernfahrer.fahrtenbuch.ui.components.KmDialog
import de.fernfahrer.fahrtenbuch.ui.editor.EditorMode
import de.fernfahrer.fahrtenbuch.ui.editor.EditorScreen
import de.fernfahrer.fahrtenbuch.ui.history.HistoryScreen
import de.fernfahrer.fahrtenbuch.ui.home.HomeScreen
import de.fernfahrer.fahrtenbuch.ui.map.MapScreen
import de.fernfahrer.fahrtenbuch.ui.map.MilestoneCelebration
import de.fernfahrer.fahrtenbuch.ui.settings.SettingsScreen
import de.fernfahrer.fahrtenbuch.ui.stats.StatsScreen

/** Reiter der unteren Leiste in der Ansicht „Fahrtenbuch“. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("heute", "Heute", Icons.Rounded.Today),
    HISTORY("verlauf", "Verlauf", Icons.Rounded.History),
    STATS("auswertung", "Auswertung", Icons.Rounded.BarChart),
}

private const val MAP_ROUTE = "karte"
private const val SETTINGS_ROUTE = "einstellungen"
private const val EDITOR_ROUTE = "eintrag?id={id}&mode={mode}&fresh={fresh}"

/** Überschrift je Ansicht (neben dem Zahnrad). */
private fun titleOf(route: String?): String? = when (route) {
    MAP_ROUTE -> "Karte"
    else -> Tab.entries.firstOrNull { it.route == route }?.label
}

/** Öffnet den Eintrag-Bildschirm: bestehender Tag (id) oder neuer Eintrag für heute. */
fun NavHostController.openEditor(id: Long = 0L, mode: EditorMode = EditorMode.NORMAL, fresh: Boolean = false) {
    navigate("eintrag?id=$id&mode=${mode.name}&fresh=$fresh")
}

/** Wechsel zwischen den Hauptansichten, ohne den Verlauf aufzustapeln. */
private fun NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    // Zuletzt geöffnete Ansicht beim Start wieder zeigen
    val startRoute = remember { if (vm.lastViewMap) MAP_ROUTE else Tab.HOME.route }
    val currentRoute = backStack?.destination?.route ?: startRoute
    val title = titleOf(currentRoute)
    val onMap = currentRoute == MAP_ROUTE
    val showBottomBar = Tab.entries.any { it.route == currentRoute }
    val celebration by vm.celebration.collectAsStateWithLifecycle()

    LaunchedEffect(currentRoute) {
        if (currentRoute == MAP_ROUTE) vm.lastViewMap = true
        else if (Tab.entries.any { it.route == currentRoute }) vm.lastViewMap = false
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    // Beim Öffnen der App: neue Tage einer laufenden Arbeitszeit anlegen (Teilung an Mitternacht)
    LifecycleStartEffect(Unit) {
        vm.onForeground()
        onStopOrDispose { }
    }
    val kmPrompt by vm.kmPrompt.collectAsStateWithLifecycle()
    kmPrompt?.let { prompt ->
        KmDialog(
            prompt = prompt,
            onSave = { vm.confirmKm(it) },
            onLater = { vm.confirmKm(null) },
            onDismiss = vm::dismissKmPrompt,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (title != null) {
                    MainHeader(
                        title = title,
                        mapSelected = onMap,
                        onSelectBook = { nav.switchTo(Tab.HOME.route) },
                        onSelectMap = { nav.switchTo(MAP_ROUTE) },
                        onSettings = { nav.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                    )
                }
            },
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
                                onClick = { nav.switchTo(tab.route) },
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
                startDestination = startRoute,
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
                composable(MAP_ROUTE) { MapScreen(mainVm = vm) }
                composable(
                    SETTINGS_ROUTE,
                    enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)) },
                    popExitTransition = { slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220)) },
                ) {
                    SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
                }
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
                        mainVm = vm,
                        onDone = { message ->
                            message?.let(vm::showMessage)
                            nav.popBackStack()
                        },
                    )
                }
            }
        }
        // „Meilenstein erreicht“ über allem
        MilestoneCelebration(
            milestone = celebration,
            onDone = vm::dismissCelebration,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp),
        )
    }
}

/** Überschrift mit Zahnrad und darunter die Umschalter „Fahrtenbuch“ / „Karte“. */
@Composable
private fun MainHeader(
    title: String,
    mapSelected: Boolean,
    onSelectBook: () -> Unit,
    onSelectMap: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ViewTile("Fahrtenbuch", Icons.AutoMirrored.Rounded.MenuBook, !mapSelected, onSelectBook, Modifier.weight(1f))
                ViewTile("Karte", Icons.Rounded.Map, mapSelected, onSelectMap, Modifier.weight(1f))
            }
        }
    }
}

/** Eine der beiden gleich großen Kacheln; die aktive ist farblich hervorgehoben. */
@Composable
private fun ViewTile(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHigh, label = "kachel")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "text")
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
