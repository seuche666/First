package de.fernfahrer.fahrtenbuch

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import de.fernfahrer.fahrtenbuch.work.WorkController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** Erinnerung „Arbeit läuft seit … Std.“: Wecker, Benachrichtigung, „Läuft noch“, Beenden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var settings: Settings
    private lateinit var reminders: ReminderScheduler
    private lateinit var controller: WorkController
    private var now = LocalDateTime.now().withSecond(0).withNano(0)

    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = Settings(context)
        settings.setWorkSettings(WorkSettings(reminderEnabled = true, reminderHours = 15, gpsEnabled = false))
        reminders = ReminderScheduler(context, settings)
        controller = WorkController(Repository(db, settings.rules), settings, reminders, LocationProvider(context), clock = { now })
    }

    @After
    fun tearDown() {
        reminders.cancel()
        db.close()
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun weckerNach15StundenBeiArbeitsbeginn() = runBlocking {
        controller.startWork(now)
        val alarm = alarms.nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(millis(now.plusHours(15)), alarm!!.triggerAtTime)
    }

    @Test
    fun benachrichtigungMitBeidenButtons() {
        reminders.showNotification(15)
        val n = notifications.allNotifications.single()
        assertEquals("Arbeit läuft seit 15 Std. – vergessen zu beenden?", shadowOf(n).contentTitle)
        assertEquals(listOf("Arbeit beenden", "Läuft noch"), n.actions.map { it.title.toString() })
    }

    @Test
    fun laeuftNochErinnertNach3Stunden() = runBlocking {
        controller.startWork(now)
        reminders.showNotification(15)
        val later = now.plusHours(15)
        reminders.snooze(later)
        assertEquals(millis(later.plusHours(3)), alarms.nextScheduledAlarm!!.triggerAtTime)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun arbeitBeendenEntferntWeckerUndBenachrichtigung() = runBlocking {
        controller.startWork(now)
        reminders.showNotification(15)
        controller.endWork(now.plusHours(16), km = null)
        assertNull(alarms.nextScheduledAlarm)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun abgeschalteteErinnerungPlantNichts() = runBlocking {
        settings.setWorkSettings(WorkSettings(reminderEnabled = false, reminderHours = 15, gpsEnabled = false))
        controller.startWork(now)
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun geaenderteStundenzahlWirdUebernommen() = runBlocking {
        controller.startWork(now)
        settings.setWorkSettings(WorkSettings(reminderEnabled = true, reminderHours = 10, gpsEnabled = false))
        controller.onReminderSettingsChanged()
        assertEquals(millis(now.plusHours(10)), alarms.nextScheduledAlarm!!.triggerAtTime)
    }
}
