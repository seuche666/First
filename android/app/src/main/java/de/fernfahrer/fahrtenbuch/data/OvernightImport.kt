package de.fernfahrer.fahrtenbuch.data

import android.util.Log
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import java.time.LocalDate
import java.time.ZoneId

/**
 * Übernimmt die Übernachtungsplätze, die bis Datenbank-Version 5 laufend aus Übernachtungstagen,
 * Standorten beim Arbeitsende und GPS-Strecken berechnet wurden, einmalig als feste Plätze.
 * Der heutige Tag wird ausgelassen (das war der Punkt, der mit dem LKW mitgewandert ist).
 * Läuft nach dem Update und nach dem Laden einer älteren Sicherung ohne Übernachtungsplätze.
 */
object OvernightImport {
    private const val TAG = "OvernightImport"

    /** Berechnet die Plätze wie die Karte bis Version 5 (ohne [today] und spätere Tage). */
    fun compute(
        days: List<ClassifiedDay>,
        stops: List<StopLocationEntity>,
        tracks: List<TrackChunkEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OvernightPlace> {
        val stopsByDate = stops.groupBy { it.date }.mapValues { (_, list) ->
            val last = list.maxBy { it.minute }
            Overnights.Spot(last.date, last.latE5, last.lonE5, last.countryCode, last.minute.toLong())
        }
        val dayEnds = Overnights.mergeDayEnds(
            tracks.mapNotNull { chunk ->
                try {
                    Overnights.lastPointPerDay(TrackCodec.decode(chunk.data), zone)
                } catch (e: Exception) {
                    null
                }
            },
        )
        val nights = days.filter { it.overnight && it.day.date.isBefore(today) }.map { it.day.date }
        return Overnights.cluster(Overnights.spots(nights, stopsByDate, dayEnds)).map { c ->
            val place = OvernightPlaceEntity(
                latE5 = Math.round(c.lat * 100_000).toInt(),
                lonE5 = Math.round(c.lon * 100_000).toInt(),
                countryCode = c.spots.lastOrNull { it.countryCode != null }?.countryCode,
            )
            val stays = c.spots.map { s ->
                OvernightStayEntity(
                    placeId = 0,
                    date = s.date,
                    minute = stopsByDate[s.date]?.timeKey?.toInt() ?: (24 * 60),
                    latE5 = s.latE5,
                    lonE5 = s.lonE5,
                )
            }
            OvernightPlace(place, stays)
        }
    }

    /**
     * Übernimmt die alten Plätze, falls das noch nicht geschehen ist und es noch keine festen Plätze
     * gibt. Bei Erfolg wird es nicht wiederholt.
     */
    suspend fun runIfNeeded(
        settings: Settings,
        repo: Repository,
        tracks: TrackRepository,
        today: LocalDate = LocalDate.now(),
    ) {
        if (settings.overnightPlacesImported) return
        try {
            importNow(repo, tracks, today)
            settings.overnightPlacesImported = true
        } catch (e: Exception) {
            Log.e(TAG, "Übernachtungsplätze konnten nicht übernommen werden", e)
        }
    }

    /** Übernimmt die berechneten Plätze, sofern noch keine festen Plätze vorhanden sind. */
    suspend fun importNow(repo: Repository, tracks: TrackRepository, today: LocalDate = LocalDate.now()) {
        if (repo.hasOvernightPlaces()) return
        val days = repo.classifiedDaysNow()
        val places = compute(days, repo.getStops(), tracks.allChunks(), today)
        if (places.isNotEmpty()) repo.insertOvernightPlaces(places)
    }
}
