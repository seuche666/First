package de.fernfahrer.fahrtenbuch.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Ermittelt einmalig den aktuellen Standort und daraus offline das Land.
 * Kein Dauer-GPS: Es wird nur auf Anfrage (Arbeitsbeginn, Arbeitsende, App-Start bei laufender
 * Arbeit) ein einzelner Standort abgefragt.
 */
class LocationProvider(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var locator: CountryLocator? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Land am aktuellen Standort oder null (keine Berechtigung, kein GPS, auf dem Meer …). */
    suspend fun currentCountry(): String? {
        if (!hasPermission()) return null
        val location = currentLocation() ?: return null
        return withContext(Dispatchers.Default) {
            countryLocator().countryAt(location.latitude, location.longitude)
        }
    }

    /** Ländergrenzen (einmal geladen, danach im Speicher). */
    suspend fun countryLocator(): CountryLocator = locator ?: withContext(Dispatchers.IO) {
        synchronized(this@LocationProvider) {
            locator ?: context.assets.open(CountryLocator.ASSET_NAME).use(CountryLocator::load).also { locator = it }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        try {
            // Ein frischer, schon bekannter Standort (max. 10 Minuten alt) reicht für das Land.
            providers.mapNotNull { lm.getLastKnownLocation(it) }
                .filter { ageMillis(it) < 10 * 60_000 }
                .minByOrNull { it.accuracy }
                ?.let { return it }
            for (provider in providers) {
                val fix = withTimeoutOrNull(30_000) { singleFix(lm, provider) }
                if (fix != null) return fix
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Keine Standort-Berechtigung", e)
        } catch (e: Exception) {
            Log.w(TAG, "Standort nicht verfügbar", e)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleFix(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }

    private fun ageMillis(loc: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000

    private companion object {
        const val TAG = "LocationProvider"
    }
}
