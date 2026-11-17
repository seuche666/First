package de.fernfahrer.fahrtenbuch.ui.map

import android.graphics.Canvas
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Nebel-Ebene für osmdroid; die eigentliche Zeichnung macht [FogRenderer]. */
class FogOverlay(density: Float) : Overlay() {
    private val renderer = FogRenderer(density)

    @Volatile var geometry: FogGeometry? = null
    @Volatile var corridorMeters: Int = 100

    var fogColor: Int
        get() = renderer.fogColor
        set(value) {
            renderer.fogColor = value
        }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = mapView.projection
        val worldPx = TileSystem.MapSize(p.zoomLevel)
        val center = p.currentCenter
        val metersPerPx = Geo.metersPerUnit(center.latitude) / worldPx
        renderer.draw(
            canvas,
            geometry,
            FogRenderer.Viewport(
                widthPx = p.width,
                heightPx = p.height,
                worldPx = worldPx,
                offsetX = p.offsetX.toDouble(),
                offsetY = p.offsetY.toDouble(),
                metersPerPx = metersPerPx,
            ),
            corridorMeters,
        )
    }
}
