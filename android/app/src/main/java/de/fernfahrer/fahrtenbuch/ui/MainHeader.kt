package de.fernfahrer.fahrtenbuch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Überschrift mit Zahnrad und darunter die Umschalter „Fahrtenbuch“ / „Karte“. */
@Composable
internal fun MainHeader(
    title: String,
    mapSelected: Boolean,
    onSelectBook: () -> Unit,
    onSelectMap: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ViewTile("Fahrtenbuch", Icons.AutoMirrored.Rounded.MenuBook, !mapSelected, onSelectBook, Modifier.weight(1f))
                ViewTile("Karte", Icons.Rounded.Map, mapSelected, onSelectMap, Modifier.weight(1f))
            }
        }
    }
}

/** Eine der beiden gleich großen Kacheln; die aktive ist farblich hervorgehoben. */
@Composable
internal fun ViewTile(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHigh, label = "kachel")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "text")
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
