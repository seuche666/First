package de.fernfahrer.fahrtenbuch

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.MapSettings
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import de.fernfahrer.fahrtenbuch.work.Tracking
import de.fernfahrer.fahrtenbuch.work.TrackingControl
import de.fernfahrer.fahrtenbuch.work.TrackingService
import de.fernfahrer.fahrtenbuch.work.WorkController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Aufzeichnung: Dienst mit Benachrichtigung, GPS-Punkte werden gespeichert, Start/Stopp mit der Arbeitszeit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingServiceTest {
    private val app = ApplicationProvider.getApplicationContext<FahrtenbuchApp>()
    private val shadowApp get() = shadowOf(app as Application)

    @Before
    fun setUp() {
        app.settings.setMapSettings(MapSettings(trackingEnabled = true))
    }

    @After
    fun tearDown() = runBlocking {
        app.tracks.deleteAll()
        app.repository.getRunningShift()?.let { app.repository.endWork(LocalDateTime.now(), null) }
        Unit
    }

    private fun location(lat: Double, lon: Double, accuracy: Float, timeMs: Long) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = lat
        longitude = lon
        this.accuracy = accuracy
        time = timeMs
        // Messzeitpunkte im Abstand von 1 s (der Dienst fragt jede Sekunde ab)
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() + (timeMs - baseMs) * 1_000_000L
    }

    private val baseMs = System.currentTimeMillis()

    @Test
    fun dienstZeichnetGpsPunkteAufUndSpeichertSie() = runBlocking {
        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        app.repository.startWork(LocalDateTime.now())
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val controller = Robolectric.buildService(TrackingService::class.java).create().startCommand(0, 1)
        val service = controller.get()
        // Vordergrunddienst mit der vorgeschriebenen Benachrichtigung
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertEquals("Strecke wird aufgezeichnet", shadowOf(notification).contentTitle)
        assertTrue(TrackingService.running.value)

        // Fahrt durch München: alle 40 m eine Messung, dazwischen eine ungenaue
        val start = baseMs
        for (i in 0 until 30) {
            shadowOf(lm).simulateLocation(location(48.137 + i * 0.00036, 11.575, 5f, start + i * 1000L))
            if (i == 10) shadowOf(lm).simulateLocation(location(48.2, 11.6, 150f, start + i * 1000L + 500))
        }
        // Warten, bis die Punkte verarbeitet und (alle 10 Punkte) gespeichert sind – Hintergrund-Threads
        val deadline = System.currentTimeMillis() + 10_000
        while (pointsSaved() < 30 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        controller.destroy() // speichert die restlichen Punkte
        assertTrue(!TrackingService.running.value)

        val chunks = app.tracks.load(allIds())
        assertEquals(1, chunks.size)
        val points = TrackCodec.decode(chunks.single().data)
        assertEquals("alle 30 genauen Punkte, ungenaue verworfen", 30, points.size)
        assertTrue(points.latE5.all { it < 4_820_000 })
        assertEquals("DE", chunks.single().countries)
        assertEquals(29 * 40.0, chunks.single().lengthMeters, 3.0)
    }

    private suspend fun allIds(): List<Long> = app.tracks.meta.first().map { it.id }

    private suspend fun pointsSaved(): Int = app.tracks.load(allIds()).sumOf { it.pointCount }

    @Test
    fun ohneStandortBerechtigungKeineAufzeichnung() {
        shadowApp.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        Tracking(app, app.settings).sync(workRunning = true)
        assertNull(shadowApp.nextStartedService)
    }

    @Test
    fun startNurWennArbeitLaeuftUndEingeschaltet() {
        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val tracking = Tracking(app, app.settings)

        tracking.sync(workRunning = true)
        val started = shadowApp.nextStartedService
        assertEquals(TrackingService::class.java.name, started?.component?.className)

        tracking.sync(workRunning = false)
        assertEquals(TrackingService::class.java.name, shadowApp.nextStoppedService?.component?.className)

        app.settings.setMapSettings(MapSettings(trackingEnabled = false))
        tracking.sync(workRunning = true)
        assertNull(shadowApp.nextStartedService)
    }

    @Test
    fun arbeitBeginnenUndBeendenStartetUndStopptDieAufzeichnung() = runBlocking {
        val calls = mutableListOf<Boolean>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings = Settings(app)
            settings.setWorkSettings(WorkSettings(reminderEnabled = false, gpsEnabled = false))
            val now = LocalDateTime.now().withSecond(0).withNano(0)
            val controller = WorkController(
                Repository(db, settings.rules), settings, ReminderScheduler(app, settings), LocationProvider(app),
                clock = { now }, tracking = TrackingControl { calls += it },
            )
            controller.startWork(now.minusHours(2))
            assertEquals(listOf(true), calls)
            controller.onForeground() // App wieder geöffnet: läuft weiter
            assertEquals(listOf(true, true), calls)
            controller.endWork(now, 123_456)
            assertEquals(listOf(true, true, false), calls)
            controller.onForeground()
            assertEquals(false, calls.last())
        } finally {
            db.close()
        }
    }
}
