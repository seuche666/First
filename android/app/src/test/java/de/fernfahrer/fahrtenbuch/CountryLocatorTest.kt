package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.location.CountryLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Prüft die Offline-Ländererkennung mit den mitgelieferten Ländergrenzen. */
class CountryLocatorTest {
    companion object {
        private lateinit var locator: CountryLocator

        @BeforeClass
        @JvmStatic
        fun load() {
            locator = File("src/main/assets/${CountryLocator.ASSET_NAME}").inputStream().use(CountryLocator::load)
        }
    }

    @Test
    fun dateiEnthaeltAlleLaender() {
        assertEquals(239, locator.countryCount)
    }

    @Test
    fun standortInDeutschland() {
        assertEquals("DE", locator.countryAt(51.3127, 9.4797))   // Kassel
        assertEquals("DE", locator.countryAt(53.5511, 9.9937))   // Hamburg
        assertEquals("DE", locator.countryAt(48.5665, 13.4312))  // Passau
        assertEquals("DE", locator.countryAt(52.3471, 14.5506))  // Frankfurt (Oder)
    }

    @Test
    fun standortImAusland() {
        assertEquals("AT", locator.countryAt(47.8095, 13.0550))  // Salzburg
        assertEquals("AT", locator.countryAt(47.5833, 12.1667))  // Kufstein, direkt an der Grenze
        assertEquals("FR", locator.countryAt(45.7640, 4.8357))   // Lyon
        assertEquals("IT", locator.countryAt(45.4642, 9.1900))   // Mailand
        assertEquals("PL", locator.countryAt(52.2297, 21.0122))  // Warschau
        assertEquals("NL", locator.countryAt(51.9244, 4.4777))   // Rotterdam
        assertEquals("CH", locator.countryAt(47.3769, 8.5417))   // Zürich
        assertEquals("ES", locator.countryAt(40.4168, -3.7038))  // Madrid
    }

    /**
     * Grenznahe Orte, die mit den 1:10m-Daten (Rastergenauigkeit ca. 400 m) richtig erkannt werden.
     * Direkt an Grenzflüssen (unter ca. 1 km) kann die Erkennung danebenliegen – dann gilt die
     * Auswahl von Hand.
     */
    @Test
    fun grenznaheOrte() {
        assertEquals("DE", locator.countryAt(48.5726, 7.8156))   // Kehl am Rhein
        assertEquals("FR", locator.countryAt(48.5734, 7.7521))   // Straßburg, gegenüber
        assertEquals("DE", locator.countryAt(47.8409, 12.9779))  // Freilassing
        assertEquals("AT", locator.countryAt(47.8225, 13.0080))  // Salzburg-Liefering
        assertEquals("DE", locator.countryAt(50.7753, 6.0839))   // Aachen
        assertEquals("NL", locator.countryAt(50.8660, 6.0627))   // Kerkrade
        assertEquals("CH", locator.countryAt(47.5596, 7.5886))   // Basel
        assertEquals("DE", locator.countryAt(47.5947, 7.6106))   // Weil am Rhein
    }

    @Test
    fun enklaven() {
        assertEquals("SM", locator.countryAt(43.9356, 12.4473))  // San Marino in Italien
        assertEquals("LU", locator.countryAt(49.6116, 6.1319))   // Luxemburg
        assertEquals("LS", locator.countryAt(-29.31, 27.48))     // Lesotho in Südafrika
    }

    @Test
    fun aufDemMeerKeinLand() {
        assertNull(locator.countryAt(55.0, 3.0))                  // Nordsee
        assertNull(locator.countryAt(38.0, -30.0))                // Atlantik
    }
}
