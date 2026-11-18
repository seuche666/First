package de.fernfahrer.fahrtenbuch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Übernachtungsplatz auf der Karte (ab Datenbank-Version 6). Entsteht nur, wenn beim „Arbeit
 * beenden“ der Haken „Übernachtungsplatz auf der Karte speichern“ gesetzt wird, und bleibt danach
 * fest an seiner Stelle. Hat nichts mit der Zählung „Übernachtung im LKW“ für die Steuer zu tun.
 */
@Entity(tableName = "overnight_places")
data class OvernightPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Position in Grad × 100 000. */
    val latE5: Int,
    val lonE5: Int,
    /** Land (ISO-Code) laut Offline-Ländergrenzen, falls bekannt. */
    val countryCode: String? = null,
    /** Eigener Name, z. B. „Autohof Fulda“; leer = nächster Ort wird angezeigt. */
    @ColumnInfo(defaultValue = "") val name: String = "",
    /** Bewertung 1–5 Sterne; 0 = keine. */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    @ColumnInfo(defaultValue = "0") val shower: Boolean = false,
    @ColumnInfo(defaultValue = "0") val toilet: Boolean = false,
    /** Restaurant/Imbiss. */
    @ColumnInfo(defaultValue = "0") val food: Boolean = false,
    /** Sicher/bewacht. */
    @ColumnInfo(defaultValue = "0") val secure: Boolean = false,
    @ColumnInfo(defaultValue = "0") val quiet: Boolean = false,
    /** Freitext, z. B. „ab 18 Uhr voll“. */
    @ColumnInfo(defaultValue = "") val note: String = "",
) {
    val lat: Double get() = latE5 / 100_000.0
    val lon: Double get() = lonE5 / 100_000.0

    /** Hat der Platz Notizen (Bewertung, Schalter oder Text)? */
    val hasNotes: Boolean get() = rating > 0 || shower || toilet || food || secure || quiet || note.isNotBlank()

    /** Alle Notizen entfernt (Name und Position bleiben). */
    fun withoutNotes() = copy(rating = 0, shower = false, toilet = false, food = false, secure = false, quiet = false, note = "")
}

/** Eine einzelne Übernachtung an einem [OvernightPlaceEntity]. */
@Entity(
    tableName = "overnight_stays",
    indices = [Index("placeId"), Index("date")],
    foreignKeys = [
        ForeignKey(
            entity = OvernightPlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OvernightStayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val date: LocalDate,
    /** Uhrzeit (Minuten ab Mitternacht) beim Arbeitsende. */
    val minute: Int,
    /** Gemessene Position dieser Übernachtung (Grad × 100 000). */
    val latE5: Int,
    val lonE5: Int,
)

/** Platz mit seinen Übernachtungen (für Karte, Sicherung). */
data class OvernightPlace(val place: OvernightPlaceEntity, val stays: List<OvernightStayEntity>) {
    val count: Int get() = stays.size
}
