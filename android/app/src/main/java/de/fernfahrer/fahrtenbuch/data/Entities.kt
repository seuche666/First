package de.fernfahrer.fahrtenbuch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.time.LocalDate
import java.time.LocalDateTime

/** Eine mehrtägige Tour. Beginn und Ende ergeben sich aus den zugehörigen Tagen. */
@Entity(tableName = "tours")
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** false = Tour läuft noch. */
    val ended: Boolean = false,
)

/** Ein erfasster Tag. Pro Kalendertag gibt es höchstens einen Eintrag. */
@Entity(
    tableName = "days",
    indices = [Index(value = ["date"], unique = true), Index("tourId")],
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tourId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class DayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Beginn der Abwesenheit in Minuten ab Mitternacht (0 = 00:00). */
    val startMinute: Int,
    /** Ende der Abwesenheit in Minuten ab Mitternacht (1440 = 24:00, über Nacht weg). */
    val endMinute: Int,
    /** ISO-Ländercode, z. B. "DE", "AT". */
    val countryCode: String,
    val kmStart: Int? = null,
    val kmEnd: Int? = null,
    val note: String = "",
    val tourId: Long? = null,
    /** Manuell gesetzte Einordnung; null = automatisch. */
    val dayTypeOverride: DayType? = null,
    /**
     * Arbeitszeit aus App-Version 1.0.6 (ein Zeitraum pro Tag). Seit Datenbank-Version 3 steht die
     * Arbeitszeit in [WorkShiftEntity]; diese Spalten bleiben unverändert als Sicherung erhalten.
     */
    @ColumnInfo(name = "workStartMinute") val legacyWorkStartMinute: Int? = null,
    @ColumnInfo(name = "workEndMinute") val legacyWorkEndMinute: Int? = null,
    /** Land wurde von Hand gewählt – die GPS-Erkennung ändert es dann nie. Alte Einträge: ja. */
    @ColumnInfo(defaultValue = "1") val countryManual: Boolean = false,
    /** Übernachtung im LKW von Hand gesetzt; null = automatisch (bei mehrtägiger Tour). */
    val overnightOverride: Boolean? = null,
    /** km-Stände wurden bei einer Schicht über Mitternacht anteilig geschätzt. */
    @ColumnInfo(defaultValue = "0") val kmInterpolated: Boolean = false,
    /**
     * Abwesenheitszeiten wurden automatisch aus der Arbeitszeit vorgeschlagen (nicht von Hand
     * bestätigt). Nur dann passt die App sie beim Arbeitsende noch an.
     */
    @ColumnInfo(defaultValue = "0") val timesAuto: Boolean = false,
) {
    val absenceMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

    /** Gefahrene km, falls beide Kilometerstände vorhanden und plausibel sind. */
    val distanceKm: Int?
        get() = if (kmStart != null && kmEnd != null && kmEnd >= kmStart) kmEnd - kmStart else null
}

/**
 * Eine Arbeitsschicht für die Firma. Sie kann über Mitternacht und mehrere Tage laufen; die
 * Aufteilung auf die Kalendertage berechnet [de.fernfahrer.fahrtenbuch.domain.WorkSplitter].
 * Hat keinen Einfluss auf die Steuer-Einordnung.
 */
@Entity(tableName = "work_shifts", indices = [Index("startDate")])
data class WorkShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: LocalDate,
    /** Beginn in Minuten ab Mitternacht; null nur bei unvollständigen alten Einträgen. */
    val startMinute: Int?,
    /** Ende; beide null, solange die Arbeit läuft oder bei unvollständigen alten Einträgen. */
    val endDate: LocalDate?,
    /** Ende in Minuten ab Mitternacht (1440 = 24:00). */
    val endMinute: Int?,
    /** true = Arbeit läuft gerade (höchstens eine Schicht). */
    val running: Boolean = false,
    /** km-Stand bei Arbeitsbeginn und -ende. */
    val kmStart: Int? = null,
    val kmEnd: Int? = null,
) {
    val start: LocalDateTime? get() = startMinute?.let { startDate.atStartOfDay().plusMinutes(it.toLong()) }
    val end: LocalDateTime?
        get() = if (endDate != null && endMinute != null) endDate.atStartOfDay().plusMinutes(endMinute.toLong()) else null
}

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
    val pointCount: Int,
    val lengthMeters: Double,
    val countries: String,
)

/**
 * Standort beim „Arbeit beenden“ (für die Übernachtungsorte auf der Karte). Als Übernachtung
 * angezeigt wird der letzte Standort eines Tages, an dem „Übernachtung im LKW“ gilt – so passt die
 * Karte automatisch, wenn die Übernachtung später von Hand geändert wird.
 */
@Entity(tableName = "stop_locations", indices = [Index("date")])
data class StopLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Uhrzeit in Minuten ab Mitternacht. */
    val minute: Int,
    /** Position in Grad × 100 000. */
    val latE5: Int,
    val lonE5: Int,
    /** Land (ISO-Code) laut Offline-Ländergrenzen, falls bekannt. */
    val countryCode: String? = null,
)

/** Ein erreichter Meilenstein. */
@Entity(tableName = "milestones")
data class MilestoneEntity(
    /** Kennung, z. B. "km_1000", "land_AT", "nacht_10". */
    @PrimaryKey val id: String,
    /** Datum, an dem der Meilenstein erreicht wurde (aus den Daten ermittelt). */
    val achievedOn: LocalDate,
)
