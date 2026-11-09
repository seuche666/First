package de.fernfahrer.fahrtenbuch.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.domain.ChartBar
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.CountryCount
import de.fernfahrer.fahrtenbuch.domain.Format
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Säulendiagramm (z. B. Kilometer pro Woche) mit sanfter Wachstums-Animation. */
@Composable
fun BarChart(bars: List<ChartBar>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val maxValue = niceMax(bars.maxOfOrNull { it.value } ?: 0)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontSize = 10.sp)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = valueColor, fontSize = 9.sp)
    val description = bars.joinToString(", ") { "${it.label}: ${it.value} km" }

    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val bottomLabel = 22.dp.toPx()
        val leftAxis = 40.dp.toPx()
        val topPad = 14.dp.toPx()
        val chartH = size.height - bottomLabel - topPad
        val chartW = size.width - leftAxis

        // Gitterlinien mit Beschriftung
        for (i in 0..3) {
            val v = maxValue * i / 3
            val y = topPad + chartH - chartH * i / 3f
            drawLine(
                gridColor, Offset(leftAxis, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val t = measurer.measure(compactNumber(v), labelStyle)
            drawText(t, topLeft = Offset(leftAxis - t.size.width - 6.dp.toPx(), y - t.size.height / 2))
        }

        if (bars.isEmpty()) return@Canvas
        val slot = chartW / bars.size
        val barW = (slot * 0.62f).coerceAtMost(36.dp.toPx())
        val showEvery = ceil(bars.size / 12f).toInt().coerceAtLeast(1)
        bars.forEachIndexed { i, bar ->
            val x = leftAxis + slot * i + (slot - barW) / 2
            val h = if (maxValue == 0) 0f else chartH * bar.value / maxValue * progress.value
            if (h > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, topPad + chartH - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 4, barW / 4),
                )
                if (bars.size <= 8 && progress.value > 0.95f) {
                    val vt = measurer.measure(compactNumber(bar.value), valueStyle)
                    drawText(vt, topLeft = Offset(x + barW / 2 - vt.size.width / 2, topPad + chartH - h - vt.size.height - 2))
                }
            }
            if (i % showEvery == 0) {
                val t = measurer.measure(bar.label, labelStyle, maxLines = 1, overflow = TextOverflow.Clip)
                drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2, size.height - bottomLabel + 6.dp.toPx()))
            }
        }
    }
}

/** Waagerechte Balken je Land: volle Tage und halbe Tage gestapelt. */
@Composable
fun CountryBars(countries: List<CountryCount>, modifier: Modifier = Modifier) {
    val maxTotal = max(1, countries.maxOfOrNull { it.total } ?: 1)
    val fullColor = MaterialTheme.colorScheme.primary
    val halfColor = MaterialTheme.colorScheme.tertiary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        countries.forEach { c ->
            val fullFraction by animateFloatAsState(c.full / maxTotal.toFloat(), tween(600), label = "voll")
            val halfFraction by animateFloatAsState(c.half / maxTotal.toFloat(), tween(600), label = "halb")
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Countries.flag(c.countryCode), fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(Countries.name(c.countryCode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${c.full} voll · ${c.half} halb",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    if (fullFraction > 0f) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fullFraction).background(fullColor))
                    }
                    if (halfFraction > 0f) {
                        // Restbreite relativ zum verbleibenden Platz berechnen
                        val rest = (halfFraction / (1f - fullFraction).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxHeight().fillMaxWidth(rest).background(halfColor))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(fullColor, "Volle Tage")
            Legend(halfColor, "Halbe Tage")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Runde Obergrenze für die Achse, z. B. 2.340 → 3.000. */
private fun niceMax(v: Int): Int {
    if (v <= 0) return 0
    val magnitude = 10.0.pow(floor10(v)).toInt()
    val steps = listOf(1, 2, 3, 5, 10)
    return steps.map { it * magnitude }.first { it >= v }
}

private fun floor10(v: Int): Int = log10(v.toDouble()).toInt()

private fun compactNumber(v: Int): String = when {
    v >= 10_000 -> "${v / 1000}k"
    else -> Format.number(v)
}
