package de.fernfahrer.fahrtenbuch.data

import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint

/** Erzeugt Streckenstücke aus Punkten (Begrenzung, Länge und Kodierung an einer Stelle). */
object TrackChunks {
    fun build(
        id: Long,
        trackId: Long,
        seq: Int,
        points: List<TrackPoint>,
        countries: Collection<String>,
    ): TrackChunkEntity {
        require(points.isNotEmpty()) { "Ein Streckenstück braucht mindestens einen Punkt" }
        return TrackChunkEntity(
            id = id,
            trackId = trackId,
            seq = seq,
            startTime = points.first().timeSec,
            endTime = points.last().timeSec,
            pointCount = points.size,
            minLatE5 = points.minOf { it.latE5 },
            minLonE5 = points.minOf { it.lonE5 },
            maxLatE5 = points.maxOf { it.latE5 },
            maxLonE5 = points.maxOf { it.lonE5 },
            lengthMeters = Geo.lengthM(points),
            countries = countries.filter { it.isNotBlank() }.distinct().sorted().joinToString(","),
            data = TrackCodec.encode(points),
        )
    }

    /** Liest die Punkte eines Stücks und berechnet alle abgeleiteten Felder neu (z. B. nach dem Einlesen einer Sicherung). */
    fun rebuild(chunk: TrackChunkEntity): TrackChunkEntity =
        build(chunk.id, chunk.trackId, chunk.seq, TrackCodec.decode(chunk.data).toList(), chunk.countries.split(','))

    fun countriesOf(meta: Collection<TrackChunkMeta>): Set<String> =
        meta.flatMap { it.countries.split(',') }.filter { it.isNotBlank() }.toSortedSet()
}
