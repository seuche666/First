package de.fernfahrer.fahrtenbuch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.fernfahrer.fahrtenbuch.ui.AppRoot
import de.fernfahrer.fahrtenbuch.ui.theme.FahrtenbuchTheme
import java.time.LocalDateTime
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FahrtenbuchTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** „Arbeit beenden“ aus der Erinnerung: Zeitpunkt merken, die App fragt dann nach dem km-Stand. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_END_WORK) {
            (application as FahrtenbuchApp).endWorkRequests.value = LocalDateTime.now()
            intent.action = null
        } else if (intent?.action == ACTION_OPEN_REPORT) {
            val month = intent.getStringExtra(EXTRA_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            if (month != null) (application as FahrtenbuchApp).reportRequests.value = month
            intent.action = null
        }
    }

    companion object {
        const val ACTION_END_WORK = "de.fernfahrer.fahrtenbuch.ARBEIT_BEENDEN"
        const val ACTION_OPEN_REPORT = "de.fernfahrer.fahrtenbuch.ABRECHNUNG_OEFFNEN"
        const val EXTRA_MONTH = "monat"
    }
}
