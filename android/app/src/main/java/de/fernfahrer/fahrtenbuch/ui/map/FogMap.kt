package de.fernfahrer.fahrtenbuch.ui.map

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Europa (für den Knopf „Europa“ und als Startansicht ohne Strecken). */
internal val EUROPE = BoundingBox(71.0, 40.0, 35.0, -10.0)

@Composable
internal fun FogMap(
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
