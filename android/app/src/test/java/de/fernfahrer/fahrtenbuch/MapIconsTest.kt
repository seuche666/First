package de.fernfahrer.fahrtenbuch

import android.graphics.Color
import de.fernfahrer.fahrtenbuch.ui.map.MapIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** LKW- und Übernachtungssymbol: Form, Ausrichtung (Fahrerhaus oben) und gut sichtbarer weißer Rand. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapIconsTest {
    @Test
    fun lkwSymbolZeigtNachObenMitWeissemRand() {
        val b = MapIcons.truck(2f)
        assertTrue("länglich (Sattelzug)", b.height > b.width * 1.8)
        val cx = b.width / 2
        // Mitte des Aufliegers: deckend blau
        val body = b.getPixel(cx, b.height * 2 / 3)
        assertEquals(255, Color.alpha(body))
        assertTrue(Color.blue(body) > Color.red(body))
        // Ecken bleiben durchsichtig
        assertEquals(0, Color.alpha(b.getPixel(0, 0)))
        // Weißer Rand links am Auflieger (sichtbar auf dunklem Nebel)
        val edge = (0 until cx).map { b.getPixel(it, b.height * 2 / 3) }.first { Color.alpha(it) > 200 }
        assertTrue(Color.red(edge) > 220 && Color.green(edge) > 220)
        // Fahrerhaus (oben) ist schmaler als der Auflieger → Fahrtrichtung erkennbar
        fun width(y: Int) = (0 until b.width).count { Color.alpha(b.getPixel(it, y)) > 200 }
        assertTrue(width(b.height * 12 / 64) < width(b.height * 2 / 3))
    }

    @Test
    fun uebernachtungSymbolMitAnzahl() {
        val single = MapIcons.overnight(2f, 1)
        val many = MapIcons.overnight(2f, 7)
        assertTrue("Abzeichen mit Anzahl macht das Symbol größer", many.width > single.width)
        assertEquals(255, Color.alpha(single.getPixel(single.width / 2, single.height / 2 + 12)))
    }
}
