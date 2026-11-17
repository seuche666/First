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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.domain.Milestones
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
internal fun Confetti(modifier: Modifier) {
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
