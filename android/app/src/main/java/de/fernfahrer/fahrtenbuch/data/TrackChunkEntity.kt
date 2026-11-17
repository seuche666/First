package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Stück einer aufgezeichneten GPS-Strecke (bis zu [de.fernfahrer.fahrtenbuch.work.TrackRecorder.MAX_POINTS]
 * Punkte). Die Punkte liegen kompakt in [data] (siehe [de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec]);
 * Begrenzungsrechteck, Länge und Länder sind zum schnellen Anzeigen und Zählen mitgespeichert.
 * Ein Folgestück derselben Aufzeichnung beginnt mit dem letzten Punkt des vorigen (durchgehende Linie).
 */
@Entity(tableName = "track_chunks", indices = [Index("trackId"), Index("startTime")])
data class TrackChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Eine zusammenhängende Aufzeichnung (Beginn in ms seit 1970). */
    val trackId: Long,
    /** Reihenfolge innerhalb der Aufzeichnung. */
    val seq: Int,
    /** Zeit des ersten und letzten Punkts (Sekunden seit 1970, UTC). */
    val startTime: Long,
    val endTime: Long,
    val pointCount: Int,
    /** Begrenzung in Grad × 100 000. */
    val minLatE5: Int,
    val minLonE5: Int,
    val maxLatE5: Int,
    val maxLonE5: Int,
    val lengthMeters: Double,
    /** Durchfahrene Länder als ISO-Codes, durch Komma getrennt (z. B. "DE,AT"). */
    val countries: String,
    val data: ByteArray,
)

/** [TrackChunkEntity] ohne die Punkte – für Statistik und Änderungserkennung. */
data class TrackChunkMeta(
    val id: Long,
    val trackId: Long,
    val seq: Int,
    /** Zeit des ersten Punkts (Sekunden seit 1970, UTC). */
    val startTime: Long,
    val pointCount: Int,
    val lengthMeters: Double,
    val countries: String,
)
