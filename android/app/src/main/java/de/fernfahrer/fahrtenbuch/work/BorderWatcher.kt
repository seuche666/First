package de.fernfahrer.fahrtenbuch.work

import de.fernfahrer.fahrtenbuch.domain.geo.BorderDetector
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Verbindet die Streckenaufzeichnung mit der Grenzerkennung ([BorderDetector]): Für jeden
 * gespeicherten Streckenpunkt wird offline das Land bestimmt; steht ein Grenzübertritt fest, wird
 * [onCrossed] mit Zeitpunkt und neuem Land aufgerufen (dort wird das Land des Tages ergänzt).
 */
class BorderWatcher(
    /** Land, in dem man zu Beginn ist (zuletzt eingetragenes Land), oder null. */
    private val initialCountry: suspend () -> String?,
    private val countryAt: suspend (lat: Double, lon: Double) -> String?,
    private val onCrossed: suspend (at: LocalDateTime, code: String) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val minDurationMs: Long = BorderDetector.MIN_DURATION_MS,
    private val minDistanceM: Double = BorderDetector.MIN_DISTANCE_M,
) {
    private var detector: BorderDetector? = null

    suspend fun onPoint(lat: Double, lon: Double, timeMs: Long) {
        val d = detector ?: BorderDetector(initialCountry(), minDurationMs, minDistanceM).also { detector = it }
        val country = countryAt(lat, lon)
        val crossed = d.onPoint(lat, lon, timeMs, country) ?: return
        onCrossed(LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMs), zone), crossed)
    }
}
