package de.fernfahrer.fahrtenbuch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.fernfahrer.fahrtenbuch.domain.DayCountries
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.time.LocalDate

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
    /**
     * Alle Länder des Tages in der Reihenfolge des Besuchs, durch Komma getrennt (z. B. „DE,NL,DE“);
     * leer = nur [countryCode]. [countryCode] ist das Land, für das der Tag zählt (siehe [DayCountries]).
     */
    @ColumnInfo(defaultValue = "") val countries: String = "",
    /**
     * Von Hand geänderter Text „Reiseanlass und Reiseweg“ für die Reisekostenabrechnung
     * (ab Datenbank-Version 8); null = automatischer Text.
     */
    val travelText: String? = null,
) {
    /** Länder des Tages in Besuchsreihenfolge (mindestens eins). */
    val countryRoute: List<String> get() = DayCountries.parse(countries, countryCode)

    /** Jedes Land des Tages einmal (für die Anzeige). */
    val allCountries: List<String> get() = DayCountries.distinct(countryRoute)

    /** Land, in dem man am Ende des Tages war (Startland des Folgetags). */
    val lastCountry: String get() = countryRoute.last()

    /** Setzt die Länder des Tages und daraus das zählende Land. */
    fun withCountries(route: List<String>): DayEntity {
        val r = DayCountries.collapse(route).ifEmpty { listOf(countryCode) }
        return copy(countries = DayCountries.join(r), countryCode = DayCountries.counting(r))
    }

    val absenceMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

    /** Gefahrene km, falls beide Kilometerstände vorhanden und plausibel sind. */
    val distanceKm: Int?
        get() = if (kmStart != null && kmEnd != null && kmEnd >= kmStart) kmEnd - kmStart else null
}
