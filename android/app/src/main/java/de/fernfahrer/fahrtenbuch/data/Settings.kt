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

    private val _map = MutableStateFlow(
        MapSettings(
            trackingEnabled = prefs.getBoolean(KEY_TRACKING, true),
            showOvernights = prefs.getBoolean(KEY_SHOW_OVERNIGHTS, true),
            corridorMeters = MapSettings.validCorridor(prefs.getInt(KEY_CORRIDOR, MapSettings.DEFAULT_CORRIDOR)),
            fuelPer100Km = MapSettings.validFuel(prefs.getFloat(KEY_FUEL, MapSettings.DEFAULT_FUEL.toFloat()).toDouble()),
        ),
    )

    /** Streckenaufzeichnung und Korridorbreite der Karte. */
    val map: StateFlow<MapSettings> = _map.asStateFlow()

    fun setMapSettings(value: MapSettings) {
        val v = value.copy(
            corridorMeters = MapSettings.validCorridor(value.corridorMeters),
            fuelPer100Km = MapSettings.validFuel(value.fuelPer100Km),
        )
        _map.value = v
        prefs.edit().putBoolean(KEY_TRACKING, v.trackingEnabled).putInt(KEY_CORRIDOR, v.corridorMeters)
            .putBoolean(KEY_SHOW_OVERNIGHTS, v.showOvernights).putFloat(KEY_FUEL, v.fuelPer100Km.toFloat()).apply()
    }

    private val _autoBackup = MutableStateFlow(
        AutoBackupSettings(
            enabled = prefs.getBoolean(KEY_AUTO_BACKUP, false),
            interval = AutoBackupSettings.Interval.entries
                .firstOrNull { it.name == prefs.getString(KEY_AUTO_BACKUP_INTERVAL, null) }
                ?: AutoBackupSettings.Interval.WEEKLY,
            keep = AutoBackupSettings.validKeep(prefs.getInt(KEY_AUTO_BACKUP_KEEP, AutoBackupSettings.DEFAULT_KEEP)),
        ),
    )

    /** Automatisches Backup (an/aus, Abstand, Anzahl). */
    val autoBackup: StateFlow<AutoBackupSettings> = _autoBackup.asStateFlow()

    fun setAutoBackup(value: AutoBackupSettings) {
        val v = value.copy(keep = AutoBackupSettings.validKeep(value.keep))
        _autoBackup.value = v
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, v.enabled).putString(KEY_AUTO_BACKUP_INTERVAL, v.interval.name)
            .putInt(KEY_AUTO_BACKUP_KEEP, v.keep).apply()
    }

    private val _backupFolder = MutableStateFlow(prefs.getString(KEY_AUTO_BACKUP_FOLDER, null))

    /** Vom Nutzer gewählter Ordner (Android-Ordnerauswahl, als URI) oder null. */
    val backupFolder: StateFlow<String?> = _backupFolder.asStateFlow()

    fun setBackupFolder(uri: String?) {
        _backupFolder.value = uri
        prefs.edit().putString(KEY_AUTO_BACKUP_FOLDER, uri).apply()
    }

    private val _lastAutoBackup = MutableStateFlow(prefs.getLong(KEY_AUTO_BACKUP_LAST, 0L))

    /** Zeitpunkt (epoch ms) des letzten erfolgreichen Backups in den Ordner; 0 = noch keins. */
    val lastAutoBackup: StateFlow<Long> = _lastAutoBackup.asStateFlow()

    fun setLastAutoBackup(epochMs: Long) {
        _lastAutoBackup.value = epochMs
        prefs.edit().putLong(KEY_AUTO_BACKUP_LAST, epochMs).apply()
    }

    /**
     * Wurden die früher berechneten Übernachtungsplätze (bis Datenbank-Version 5) schon einmalig
     * als feste Plätze übernommen?
     */
    var overnightPlacesImported: Boolean
        get() = prefs.getBoolean(KEY_PLACES_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PLACES_IMPORTED, value).apply()

    /** Zuletzt geöffnete Ansicht: true = Karte, false = Fahrtenbuch. */
    var lastViewMap: Boolean
        get() = prefs.getBoolean(KEY_LAST_VIEW_MAP, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_VIEW_MAP, value).apply()

    /**
     * Wurden die vorhandenen Daten schon einmal auf Meilensteine geprüft? Beim ersten Mal werden
     * bereits erreichte Meilensteine still freigeschaltet (ohne Meldungen).
     */
    var milestonesInitialized: Boolean
        get() = prefs.getBoolean(KEY_MILESTONES_INIT, false)
        set(value) = prefs.edit().putBoolean(KEY_MILESTONES_INIT, value).apply()

    /** Wurde die Erklärung zu „Standort immer erlauben“ schon gezeigt? */
    var backgroundLocationExplained: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_EXPLAINED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_EXPLAINED, value).apply()

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
        private const val KEY_TRACKING = "strecke_aufzeichnen"
        private const val KEY_SHOW_OVERNIGHTS = "karte_uebernachtungen"
        private const val KEY_LAST_VIEW_MAP = "ansicht_karte"
        private const val KEY_MILESTONES_INIT = "meilensteine_geprueft"
        private const val KEY_CORRIDOR = "karte_korridor_m"
        private const val KEY_BACKGROUND_EXPLAINED = "standort_immer_erklaert"
        private const val KEY_FUEL = "verbrauch_l_100km"
        private const val KEY_AUTO_BACKUP = "auto_backup_an"
        private const val KEY_AUTO_BACKUP_INTERVAL = "auto_backup_abstand"
        private const val KEY_AUTO_BACKUP_KEEP = "auto_backup_anzahl"
        private const val KEY_AUTO_BACKUP_FOLDER = "auto_backup_ordner"
        private const val KEY_AUTO_BACKUP_LAST = "auto_backup_zuletzt"
        private const val KEY_PLACES_IMPORTED = "uebernachtungsplaetze_uebernommen"
    }
}
