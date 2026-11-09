package de.fernfahrer.fahrtenbuch.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Deutsche Formatierung für Datum, Uhrzeit und Zahlen. */
object Format {
    private val de = Locale.GERMANY
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", de)
    private val shortDateFmt = DateTimeFormatter.ofPattern("dd.MM.", de)
    private val weekdayDateFmt = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", de)
    private val longDateFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", de)
    private val monthYearFmt = DateTimeFormatter.ofPattern("LLLL yyyy", de)
    private val numberFmt get() = NumberFormat.getIntegerInstance(de)

    fun date(d: LocalDate): String = d.format(dateFmt)
    fun shortDate(d: LocalDate): String = d.format(shortDateFmt)
    fun weekdayDate(d: LocalDate): String = d.format(weekdayDateFmt)
    fun longDate(d: LocalDate): String = d.format(longDateFmt)
    fun monthYear(d: LocalDate): String = d.format(monthYearFmt).replaceFirstChar { it.uppercase(de) }
    fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, de).removeSuffix(".")
    fun monthShort(month: Int): String =
        java.time.Month.of(month).getDisplayName(TextStyle.SHORT, de).removeSuffix(".")

    /** Minuten ab Mitternacht als Uhrzeit, z. B. 390 → "06:30", 1440 → "24:00". */
    fun time(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Dauer in Minuten als "h:mm Std.". */
    fun duration(minutes: Int): String = "%d:%02d Std.".format(minutes / 60, minutes % 60)

    fun number(n: Int): String = numberFmt.format(n)
    fun km(n: Int): String = "${number(n)} km"

    fun nowMinutes(roundTo: Int = 5): Int {
        val now = LocalTime.now()
        val m = now.hour * 60 + now.minute
        return (m / roundTo) * roundTo
    }

    fun days(n: Int): String = if (n == 1) "1 Tag" else "$n Tage"
}
