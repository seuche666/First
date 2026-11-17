package de.fernfahrer.fahrtenbuch.data

import android.content.Context
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Einfache Einstellungen, gespeichert in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    private val _driverName = MutableStateFlow(prefs.getString(KEY_DRIVER, "").orEmpty())
    val driverName: StateFlow<String> = _driverName.asStateFlow()

    fun setDriverName(name: String) {
        _driverName.value = name
        prefs.edit().putString(KEY_DRIVER, name).apply()
    }

    private val _rules = MutableStateFlow(
        CountingRules(shortSingleDayAsHalf = prefs.getBoolean(KEY_SHORT_AS_HALF, false)),
    )

    /** Aktive Zählregeln (z. B. Firmenregel für kurze eintägige Fahrten). */
    val rules: StateFlow<CountingRules> = _rules.asStateFlow()

    fun setShortSingleDayAsHalf(enabled: Boolean) {
        _rules.value = _rules.value.copy(shortSingleDayAsHalf = enabled)
        prefs.edit().putBoolean(KEY_SHORT_AS_HALF, enabled).apply()
    }

    private val _work = MutableStateFlow(
        WorkSettings(
            reminderEnabled = prefs.getBoolean(KEY_REMINDER, true),
            reminderHours = prefs.getInt(KEY_REMINDER_HOURS, 15),
            gpsEnabled = prefs.getBoolean(KEY_GPS, true),
        ),
    )

    /** Erinnerung bei langer Arbeitszeit und GPS-Ländererkennung. */
    val work: StateFlow<WorkSettings> = _work.asStateFlow()

    fun setWorkSettings(value: WorkSettings) {
        val v = value.copy(reminderHours = value.reminderHours.coerceIn(MIN_HOURS, MAX_HOURS))
        _work.value = v
        prefs.edit()
            .putBoolean(KEY_REMINDER, v.reminderEnabled)
            .putInt(KEY_REMINDER_HOURS, v.reminderHours)
            .putBoolean(KEY_GPS, v.gpsEnabled)
            .apply()
    }

    /** Wurde die Erklärung zu den Berechtigungen (Standort, Benachrichtigungen) schon gezeigt? */
    var permissionsExplained: Boolean
        get() = prefs.getBoolean(KEY_PERMISSIONS_EXPLAINED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSIONS_EXPLAINED, value).apply()

    /** Zeitpunkt (epoch ms), zu dem die nächste Erinnerung fällig ist, und für welche Schicht. */
    var reminderDueAt: Long
        get() = prefs.getLong(KEY_REMINDER_DUE, 0L)
        set(value) = prefs.edit().putLong(KEY_REMINDER_DUE, value).apply()
    var reminderShiftId: Long
        get() = prefs.getLong(KEY_REMINDER_SHIFT, 0L)
        set(value) = prefs.edit().putLong(KEY_REMINDER_SHIFT, value).apply()

    /** Hinweis auf eine vor dem Update angelegte Sicherung, einmal anzeigen. */
    var pendingBackupNotice: String?
        get() = prefs.getString(KEY_BACKUP_NOTICE, null)
        set(value) = prefs.edit().putString(KEY_BACKUP_NOTICE, value).apply()

    companion object {
        const val MIN_HOURS = 1
        const val MAX_HOURS = 24
        private const val KEY_DRIVER = "fahrer_name"
        private const val KEY_SHORT_AS_HALF = "eintaegig_kurz_als_halb"
        private const val KEY_REMINDER = "erinnerung_an"
        private const val KEY_REMINDER_HOURS = "erinnerung_stunden"
        private const val KEY_GPS = "gps_land"
        private const val KEY_PERMISSIONS_EXPLAINED = "berechtigungen_erklaert"
        private const val KEY_REMINDER_DUE = "erinnerung_faellig"
        private const val KEY_REMINDER_SHIFT = "erinnerung_schicht"
        private const val KEY_BACKUP_NOTICE = "hinweis_sicherung"
    }
}
