package de.fernfahrer.fahrtenbuch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint
import de.fernfahrer.fahrtenbuch.ui.map.FogRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nebel-Darstellung mit echter Grafik (Robolectric „native graphics“): Nebel deckt alles ab,
 * entlang der Strecke ist die Karte frei, der Rand ist weich, mehrfach befahrene Strecken werden
 * nicht breiter, und beim Rauszoomen bleibt eine Mindestbreite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FogRendererTest {
    private val size = 400
    private val lat = 50.0
    private val lon0 = 8.0
    private val fogAlpha = 222

    /** Waagerechte Strecke von West nach Ost durch die Bildmitte. */
    private fun geometry(copies: Int = 1): FogGeometry {
        val lats = IntArray(200) { Math.round(lat * TrackPoint.E5).toInt() }
        val lons = IntArray(200) { Math.round((lon0 - 1.0 + it * 0.01) * TrackPoint.E5).toInt() }
        return FogGeometry((1..copies).map { FogGeometry.chunk(it.toLong(), it.toLong(), 0, lats, lons) })
    }

    /** Ansicht mit [metersPerPx] Metern pro Pixel, Bildmitte auf (lat, lon0). */
    private fun viewport(metersPerPx: Double): FogRenderer.Viewport {
        val worldPx = Geo.metersPerUnit(lat) / metersPerPx
        return FogRenderer.Viewport(
            widthPx = size, heightPx = size, worldPx = worldPx,
            offsetX = size / 2 - Geo.mercatorX(lon0) * worldPx,
            offsetY = size / 2 - Geo.mercatorY(lat) * worldPx,
            metersPerPx = metersPerPx,
        )
    }

    private fun render(geometry: FogGeometry?, metersPerPx: Double, corridorM: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val renderer = FogRenderer(density = 1f)
        renderer.draw(Canvas(bitmap), geometry, viewport(metersPerPx), corridorM)
        return bitmap
    }

    private fun Bitmap.alphaAt(x: Int, y: Int) = Color.alpha(getPixel(x, y))

    @Test
    fun ohneStreckenIstAllesVerdeckt() {
        val b = render(null, 2.0, 100)
        assertEquals(fogAlpha, b.alphaAt(0, 0))
        assertEquals(fogAlpha, b.alphaAt(200, 200))
        assertEquals(fogAlpha, b.alphaAt(399, 399))
    }

    @Test
    fun streckeWirdMitWeichemRandFreigelegt() {
        // 2 m pro Pixel: Korridor 100 m = 50 px (25 px links und rechts der Strecke)
        val b = render(geometry(), 2.0, 100)
        val center = 200
        assertEquals("Strecke frei", 0, b.alphaAt(200, center))
        assertEquals("innerhalb des Korridors frei", 0, b.alphaAt(200, center + 22))
        assertEquals("weit weg: Nebel", fogAlpha, b.alphaAt(200, center + 150))
        // Weicher Übergang: außerhalb des Korridors steigt der Nebel allmählich an
        val edge = (24..60).map { b.alphaAt(200, center + it) }
        assertTrue("Übergang $edge", edge.any { it in 30..190 })
        assertTrue("Übergang nimmt nach außen zu: $edge", edge.zipWithNext().all { (a, c) -> c >= a - 2 })
        assertEquals(fogAlpha, edge.last())
    }

    @Test
    fun mehrfachBefahreneStreckeWirdNichtBreiter() {
        val once = render(geometry(1), 2.0, 100)
        val many = render(geometry(5), 2.0, 100)
        for (dy in 0..60) {
            assertEquals("Abstand $dy px", once.alphaAt(200, 200 + dy).toDouble(), many.alphaAt(200, 200 + dy).toDouble(), 3.0)
        }
    }

    @Test
    fun breitererKorridorLegtMehrFrei() {
        val narrow = render(geometry(), 2.0, 100)
        val wide = render(geometry(), 2.0, 500) // 250 m = 125 px zu jeder Seite
        assertEquals(fogAlpha, narrow.alphaAt(200, 300))
        assertEquals(0, wide.alphaAt(200, 300))
    }

    @Test
    fun beimRauszoomenBleibtEineMindestbreite() {
        // Europa-Ansicht: 5 km pro Pixel – 100 m wären nur 0,02 px; sichtbar bleiben trotzdem ein paar Pixel
        val b = render(geometry(), 5000.0, 100)
        assertTrue("Strecke sichtbar: ${b.alphaAt(200, 200)}", b.alphaAt(200, 200) < 60)
        assertEquals(fogAlpha, b.alphaAt(200, 230))
    }
}
