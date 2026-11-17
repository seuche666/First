package de.fernfahrer.fahrtenbuch.work

import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.TrackRepository
import de.fernfahrer.fahrtenbuch.location.LocationProvider

/** Position beim Arbeitsende (für die Übernachtungsorte). */
data class StopFix(val lat: Double, val lon: Double, val countryCode: String?)

fun interface StopLocator {
    suspend fun locate(): StopFix?

    companion object {
        val NONE = StopLocator { null }
    }
}

/**
 * Ermittelt die Position einmalig per GPS (offline); klappt das nicht, gilt der letzte Punkt der
 * Streckenaufzeichnung (höchstens 30 Minuten alt). Nur wenn die Streckenaufzeichnung eingeschaltet
 * und der Standort erlaubt ist.
 */
class DeviceStopLocator(
    private val settings: Settings,
    private val location: LocationProvider,
    private val tracks: TrackRepository,
) : StopLocator {
    override suspend fun locate(): StopFix? {
        if (!settings.map.value.trackingEnabled || !location.hasPermission()) return null
        val (lat, lon) = location.currentPosition()?.let { it.latitude to it.longitude }
            ?: tracks.lastPoint()?.let { it.lat to it.lon }
            ?: return null
        val country = runCatching { location.countryLocator().countryAt(lat, lon) }.getOrNull()
        return StopFix(lat, lon, country)
    }
}
