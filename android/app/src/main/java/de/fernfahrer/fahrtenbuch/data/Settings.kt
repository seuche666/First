package de.fernfahrer.fahrtenbuch.data

import android.content.Context
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

    private companion object {
        const val KEY_DRIVER = "fahrer_name"
    }
}
