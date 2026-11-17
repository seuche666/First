package de.fernfahrer.fahrtenbuch.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import android.graphics.drawable.BitmapDrawable
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.StatRow
import de.fernfahrer.fahrtenbuch.ui.components.StatTile
import de.fernfahrer.fahrtenbuch.work.TrackingService
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Europa (für den Knopf „Europa“ und als Startansicht ohne Strecken). */
private val EUROPE = BoundingBox(71.0, 40.0, 35.0, -10.0)

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

private class MapHint(
    val id: String,
    val icon: ImageVector,
    val text: String,
    val action: String,
    val onAction: () -> Unit,
)

@Composable
private fun FogMap(
    vm: MapViewModel,
    geometry: FogGeometry?,
    loading: Boolean,
    corridorM: Int,
    hasLocation: Boolean,
    onNeedLocation: () -> Unit,
    onMessage: (String) -> Unit,
    overnights: List<Overnights.Cluster>,
    showOvernights: Boolean,
    onToggleOvernights: () -> Unit,
    onClusterClick: (Overnights.Cluster) -> Unit,
    onOpenMilestones: () -> Unit,
    achievedMilestones: Int,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val fog = remember { FogOverlay(density) }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            overlays.add(fog)
        }
    }
    // Übernachtungsplätze über dem Nebel, darunter nichts verdeckt
    val nightLayer = remember { FolderOverlay().also { mapView.overlays.add(it) } }
    val clickHandler = rememberUpdatedState(onClusterClick)
    val myLocation = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            isDrawAccuracyEnabled = true
            // Eigenes LKW-Symbol statt Männchen; in Fahrt dreht es sich in Fahrtrichtung
            val truck = MapIcons.truck(density)
            setPersonIcon(truck)
            setDirectionIcon(truck)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionAnchor(0.5f, 0.5f)
            mapView.overlays.add(this)
        }
    }
    LaunchedEffect(overnights) {
        nightLayer.items.clear()
        for (cluster in overnights) {
            nightLayer.add(
                Marker(mapView).apply {
                    position = GeoPoint(cluster.lat, cluster.lon)
                    icon = BitmapDrawable(context.resources, MapIcons.overnight(density, cluster.count))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ ->
                        clickHandler.value(cluster)
                        true
                    }
                },
            )
        }
        mapView.invalidate()
    }

    // Startansicht: zuletzt gesehener Ausschnitt, sonst alle Strecken, sonst Europa
    remember {
        vm.camera?.let { cam ->
            mapView.controller.setZoom(cam.zoom)
            mapView.controller.setCenter(GeoPoint(cam.lat, cam.lon))
        }
    }
    LaunchedEffect(loading) {
        if (vm.camera != null || loading) return@LaunchedEffect
        val fit = {
            val box = vm.geometry.value?.bounds?.let { b ->
                BoundingBox(
                    Geo.latFromMercatorY(b[1]), Geo.lonFromMercatorX(b[2]),
                    Geo.latFromMercatorY(b[3]), Geo.lonFromMercatorX(b[0]),
                )
            }
            when {
                box == null -> mapView.zoomToBoundingBox(EUROPE, false)
                box.latitudeSpan > 0.02 || box.longitudeSpan > 0.02 -> mapView.zoomToBoundingBox(box.increaseByScale(1.3f), false)
                else -> mapView.controller.apply { setZoom(13.0); setCenter(box.centerWithDateLine) }
            }
            val c = mapView.mapCenter
            vm.camera = Camera(c.latitude, c.longitude, mapView.zoomLevelDouble)
        }
        if (mapView.isLayoutOccurred) fit() else mapView.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
    }

    DisposableEffect(lifecycle, hasLocation) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (hasLocation) myLocation.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    myLocation.disableMyLocation()
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            myLocation.disableMyLocation()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val c = mapView.mapCenter
            vm.camera = Camera(c.latitude, c.longitude, mapView.zoomLevelDouble)
            mapView.onDetach()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                fog.geometry = geometry
                fog.corridorMeters = corridorM
                it.invalidate()
            },
        )
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFloatingActionButton(onClick = onOpenMilestones) {
                BadgedBox(badge = { if (achievedMilestones > 0) Badge { Text(achievedMilestones.toString()) } }) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = "Meilensteine")
                }
            }
            SmallFloatingActionButton(
                onClick = onToggleOvernights,
                containerColor = if (showOvernights) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(
                    if (showOvernights) Icons.Rounded.Bedtime else Icons.Rounded.BedtimeOff,
                    contentDescription = if (showOvernights) "Übernachtungsplätze ausblenden" else "Übernachtungsplätze einblenden",
                )
            }
            SmallFloatingActionButton(onClick = { mapView.zoomToBoundingBox(EUROPE, true) }) {
                Icon(Icons.Rounded.Public, contentDescription = "Ganz Europa zeigen")
            }
            SmallFloatingActionButton(onClick = {
                if (!hasLocation) {
                    onNeedLocation()
                    return@SmallFloatingActionButton
                }
                myLocation.enableMyLocation()
                val fix = myLocation.myLocation
                if (fix != null) {
                    mapView.controller.animateTo(fix, maxOf(mapView.zoomLevelDouble, 14.0), 700L)
                } else {
                    onMessage("Standort wird gesucht …")
                    myLocation.runOnFirstFix {
                        mapView.post {
                            myLocation.myLocation?.let { mapView.controller.animateTo(it, 14.0, 700L) }
                        }
                    }
                }
            }) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "Mein Standort")
            }
        }
    }
}

@Composable
private fun RecordingChip() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color(0xFFE53935), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Strecke wird aufgezeichnet", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HintCard(hint: MapHint, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(hint.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hint.text, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = hint.onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(hint.action)
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Hinweis ausblenden") }
        }
    }
}

/** Pflicht-Namensnennung der OpenStreetMap-Daten, mit Link zur Lizenzseite. */
@Composable
private fun OsmAttribution(modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.clickable {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        shape = MaterialTheme.shapes.small,
        color = Color.White.copy(alpha = 0.8f),
        contentColor = Color(0xFF333333),
    ) {
        Text("© OpenStreetMap-Mitwirkende", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun MapStatsCard(stats: MapStats) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            StatRow {
                StatTile(
                    value = Format.number(Math.round(stats.distanceKm).toInt()),
                    label = "km laut GPS",
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatTile(
                    value = stats.countries.size.toString(),
                    label = if (stats.countries.size == 1) "Land befahren" else "Länder befahren",
                    modifier = Modifier.weight(1f),
                )
            }
            if (stats.countries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stats.countries.joinToString(" · ") { Countries.label(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}
