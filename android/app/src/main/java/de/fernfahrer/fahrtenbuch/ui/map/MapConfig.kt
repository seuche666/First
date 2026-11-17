package de.fernfahrer.fahrtenbuch.ui.map

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Einstellungen für die Kartenbibliothek osmdroid und die OpenStreetMap-Kacheln.
 * Nutzungsbedingungen (https://operations.osmfoundation.org/policies/tiles/): eigener, eindeutiger
 * User-Agent, Kacheln werden zwischengespeichert (Cache-Vorgaben des Servers werden beachtet),
 * kein Massen-Download, Namensnennung „© OpenStreetMap-Mitwirkende“ auf der Karte.
 */
object MapConfig {
    fun init(context: Context) {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        } ?: "1"
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "Fahrtenbuch-Android/$version (${context.packageName})"
            // Nur im App-Speicher (keine Speicher-Berechtigung nötig)
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid-kacheln")
            tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        }
    }
}
