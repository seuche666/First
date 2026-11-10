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

    private companion object {
        const val KEY_DRIVER = "fahrer_name"
        const val KEY_SHORT_AS_HALF = "eintaegig_kurz_als_halb"
    }
}
