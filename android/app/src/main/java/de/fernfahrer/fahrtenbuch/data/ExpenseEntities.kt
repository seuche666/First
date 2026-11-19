package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.fernfahrer.fahrtenbuch.domain.ExpenseRate
import de.fernfahrer.fahrtenbuch.domain.ExpenseTable

/**
 * Spesentabelle eines Jahres (Tabelle `expense_years`, ab Datenbank-Version 7). Jedes Jahr ist
 * unabhängig: Eine neue Tabelle wird einmalig aus dem Vorjahr kopiert, danach wirken Änderungen nie
 * auf andere Jahre.
 */
@Entity(tableName = "expense_years")
data class ExpenseYearEntity(
    @PrimaryKey val year: Int,
    /** Übernachtungspauschale LKW pro Tag in Cent (gilt für alle Länder); null = noch nicht eingetragen. */
    val overnightCents: Long? = null,
)

/** Beträge eines Landes in der Spesentabelle eines Jahres (Tabelle `expense_rates`), in Cent. */
@Entity(tableName = "expense_rates", primaryKeys = ["year", "countryCode"])
data class ExpenseRateEntity(
    val year: Int,
    val countryCode: String,
    /** Voller Tag (24 Std.). */
    val fullDayCents: Long? = null,
    /** An-/Abreisetag bzw. halber Tag. */
    val partialDayCents: Long? = null,
) {
    fun toRate() = ExpenseRate(countryCode, fullDayCents, partialDayCents)
}

/** Spesentabelle eines Jahres mit allen Ländern (für Sicherung und Anzeige). */
data class ExpenseYear(val year: ExpenseYearEntity, val rates: List<ExpenseRateEntity>) {
    fun toTable() = ExpenseTable(year.year, year.overnightCents, rates.associate { it.countryCode to it.toRate() })
}
