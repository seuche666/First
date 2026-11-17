package de.fernfahrer.fahrtenbuch.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.work.TrackingService

@Composable
fun MapScreen(mainVm: MainViewModel) {
    val vm: MapViewModel = viewModel(factory = MapViewModel.Factory)
    val geometry by vm.geometry.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val mapSettings by mainVm.mapSettings.collectAsStateWithLifecycle()
    val running by mainVm.runningShift.collectAsStateWithLifecycle()
    val recording by TrackingService.running.collectAsStateWithLifecycle()
    val dismissed by vm.dismissedHints.collectAsStateWithLifecycle()
    val permissions = rememberTrackingPermissions(onChanged = mainVm::syncTracking)
    val overnights by vm.overnights.collectAsStateWithLifecycle()
    val milestones by mainVm.milestones.collectAsStateWithLifecycle()
    var selectedCluster by remember { mutableStateOf<Overnights.Cluster?>(null) }
    var showMilestones by remember { mutableStateOf(false) }

    val hint: MapHint? = when {
        !mapSettings.trackingEnabled -> MapHint("aus", Icons.Rounded.Route, TrackingTexts.TRACKING_OFF, "Einschalten") {
            mainVm.setMapSettings(mapSettings.copy(trackingEnabled = true))
        }
        !permissions.location -> MapHint("standort", Icons.Rounded.LocationOff, TrackingTexts.LOCATION_MISSING, "Erlauben", permissions.requestLocation)
        !permissions.background -> MapHint("immer", Icons.Rounded.LocationOff, TrackingTexts.BACKGROUND_MISSING, "Einstellen", permissions.requestBackground)
        !permissions.batteryOk -> MapHint("akku", Icons.Rounded.BatteryAlert, TrackingTexts.BATTERY, "Ausnahme erlauben", permissions.openBattery)
        else -> null
    }?.takeIf { it.id !in dismissed }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FogMap(
                vm = vm,
                geometry = geometry,
                loading = loading,
                corridorM = mapSettings.corridorMeters,
                hasLocation = permissions.location,
                onNeedLocation = permissions.requestLocation,
                onMessage = mainVm::showMessage,
                overnights = if (mapSettings.showOvernights) overnights else emptyList(),
                showOvernights = mapSettings.showOvernights,
                onToggleOvernights = {
                    val on = !mapSettings.showOvernights
                    mainVm.setMapSettings(mapSettings.copy(showOvernights = on))
                    mainVm.showMessage(if (on) "Übernachtungsplätze eingeblendet" else "Übernachtungsplätze ausgeblendet")
                },
                onClusterClick = { selectedCluster = it },
                onOpenMilestones = { showMilestones = true },
                achievedMilestones = milestones.count { it.achieved },
            )

            Column(
                Modifier.align(Alignment.TopCenter).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(visible = running != null && recording, enter = fadeIn(), exit = fadeOut()) {
                    RecordingChip()
                }
                hint?.let { HintCard(it, onDismiss = { vm.dismissHint(it.id) }) }
            }

            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))
            }

            OsmAttribution(Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        MapStatsCard(stats)
    }

    selectedCluster?.let { OvernightDialog(it, onDismiss = { selectedCluster = null }) }
    if (showMilestones) MilestonesSheet(milestones, onDismiss = { showMilestones = false })
}
