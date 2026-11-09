package de.fernfahrer.fahrtenbuch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.fernfahrer.fahrtenbuch.ui.AppRoot
import de.fernfahrer.fahrtenbuch.ui.theme.FahrtenbuchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FahrtenbuchTheme {
                AppRoot()
            }
        }
    }
}
