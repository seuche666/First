package de.fernfahrer.fahrtenbuch.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Milestones
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private fun Milestones.Group.icon(): ImageVector = when (this) {
    Milestones.Group.KM -> Icons.Rounded.Route
    Milestones.Group.COUNTRIES -> Icons.Rounded.Flag
    Milestones.Group.NIGHTS -> Icons.Rounded.Bedtime
    Milestones.Group.TOURS -> Icons.Rounded.LocalShipping
}

/** Antippen eines Übernachtungsplatzes: Datum, Land und Ort jeder Übernachtung dort. */
@Composable
fun OvernightDialog(cluster: Overnights.Cluster, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? FahrtenbuchApp
    // Land und nächster Ort offline nachschlagen
    val details by produceState<Pair<String?, String?>>(null to null, cluster) {
        value = try {
            val country = cluster.spots.lastOrNull { it.countryCode != null }?.countryCode
                ?: app?.location?.countryLocator()?.countryAt(cluster.lat, cluster.lon)
            val place = app?.location?.placeLocator()?.nearest(cluster.lat, cluster.lon)?.label
            country to place
        } catch (e: Exception) {
            null to null
        }
    }
    val (country, place) = details
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = {
            Text(if (cluster.count == 1) "Übernachtung" else "${cluster.count} Übernachtungen hier")
        },
        text = {
            Column {
                Text(
                    listOfNotNull(place, country?.let(Countries::label)).joinToString(" · ").ifEmpty { "Ort wird gesucht …" },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "%.5f, %.5f".format(java.util.Locale.ROOT, cluster.lat, cluster.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(cluster.spots.sortedByDescending { it.date }) { spot ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.weekdayDate(spot.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                Countries.label(spot.countryCode ?: country ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/** Übersicht aller Meilensteine: erreichte mit Datum, offene ausgegraut mit Fortschritt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesSheet(list: List<Milestones.Status>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val achieved = list.count { it.achieved }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFF5B400), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Meilensteine", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "$achieved von ${list.size} erreicht",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            for (group in Milestones.Group.entries) {
                val items = list.filter { it.group == group }
                    .sortedWith(compareBy<Milestones.Status>({ !it.achieved }, { it.achievedOn }, { it.target }))
                if (items.isEmpty()) continue
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(group.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                items(items, key = { it.id }) { MilestoneRow(it) }
            }
            if (list.none { it.group == Milestones.Group.COUNTRIES && it.id.startsWith("land_") }) {
                item {
                    Text(
                        "Jedes neu befahrene Land erscheint hier als eigener Meilenstein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(m: Milestones.Status) {
    val done = m.achieved
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (done) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(
                if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (done) m.group.icon() else Icons.Rounded.Lock,
                null,
                tint = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall)
            if (done) {
                Text(
                    "erreicht am ${Format.date(m.achievedOn!!)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Format.number(m.current.coerceAtMost(m.target).toInt())} von ${Format.number(m.target.toInt())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * Kurze Meldung „Meilenstein erreicht“ mit Animation (hereinfedern, Pokal mit Strahlenkranz,
 * kleines Konfetti). Verschwindet nach einigen Sekunden oder beim Antippen.
 */
@Composable
fun MilestoneCelebration(milestone: Milestones.Status?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Milestones.Status?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(milestone) {
        if (milestone != null) {
            shown = milestone
            visible = true
            delay(4_500)
            visible = false
            delay(350)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = visible && shown != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { -it } + fadeIn() + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)),
    ) {
        val m = shown ?: return@AnimatedVisibility
        val pop = remember { Animatable(0.4f) }
        LaunchedEffect(m.id) {
            pop.snapTo(0.4f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
        val spin = rememberInfiniteTransition(label = "strahlen")
        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "winkel")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { visible = false },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF6A3DE8))),
                ),
            ) {
                Confetti(Modifier.matchParentSize())
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().rotate(angle)) {
                            val c = center
                            for (k in 0 until 12) {
                                val a = Math.toRadians(k * 30.0)
                                drawLine(
                                    Color.White.copy(alpha = 0.35f),
                                    Offset(c.x + cos(a).toFloat() * size.minDimension * 0.28f, c.y + sin(a).toFloat() * size.minDimension * 0.28f),
                                    Offset(c.x + cos(a).toFloat() * size.minDimension * 0.5f, c.y + sin(a).toFloat() * size.minDimension * 0.5f),
                                    strokeWidth = 3f,
                                )
                            }
                        }
                        Icon(
                            m.group.icon().takeIf { m.group == Milestones.Group.COUNTRIES } ?: Icons.Rounded.EmojiEvents,
                            null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(34.dp).scale(pop.value),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Meilenstein erreicht!", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                        Text(m.message, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Ein paar fallende Konfetti-Punkte im Hintergrund der Meldung. */
@Composable
private fun Confetti(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "konfetti")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400), RepeatMode.Restart), label = "fall")
    val colors = listOf(Color(0xFFFFD54F), Color(0xFF80DEEA), Color(0xFFFF8A80), Color(0xFFB9F6CA), Color.White)
    Canvas(modifier) {
        for (k in 0 until 18) {
            val x = ((k * 53) % 100) / 100f * size.width
            val y = (((k * 37) % 100) / 100f + fall) % 1f * size.height
            drawCircle(colors[k % colors.size].copy(alpha = 0.7f), radius = 3f + (k % 3) * 1.5f, center = Offset(x, y))
        }
    }
}
