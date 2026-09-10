package dev.prateekthakur.devprobe.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Small, dependency-free Canvas charts for the APK dashboard — donut (proportions),
// horizontal bars (counts across a small category set), and a radial gauge (a single
// 0-100 score). Kept in one file since every APK-inspection dashboard needs them.
// ---------------------------------------------------------------------------

data class ChartSlice(val label: String, val value: Float, val color: Color)

@Composable
fun DonutChart(slices: List<ChartSlice>, modifier: Modifier = Modifier, strokeWidth: Dp = 24.dp) {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "donutProgress",
    )
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val gapDegrees = if (slices.size > 1) 3f else 0f

    Canvas(modifier = modifier) {
        if (total <= 0f) return@Canvas
        val stroke = strokeWidth.toPx()
        val diameter = kotlin.math.min(size.width, size.height) - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        slices.forEach { slice ->
            val fullSweep = (slice.value / total) * 360f
            val sweep = (fullSweep - gapDegrees).coerceAtLeast(0f) * progress
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            startAngle += fullSweep
        }
    }
}

@Composable
fun ChartLegend(slices: List<ChartSlice>, format: (Float) -> String = { it.toInt().toString() }, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                Text(slice.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(format(slice.value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class BarEntry(val label: String, val value: Int, val color: Color)

@Composable
fun HorizontalBarChart(entries: List<BarEntry>, modifier: Modifier = Modifier) {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "barProgress",
    )
    val maxValue = (entries.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { entry ->
            Column {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    Text(entry.value.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(entry.color.copy(alpha = 0.14f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (entry.value / maxValue.toFloat()) * progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(entry.color),
                    )
                }
            }
        }
    }
}

/** A 270° radial gauge for a single 0f..1f score, e.g. a security-health score. */
@Composable
fun GaugeChart(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (played) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gaugeProgress",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = strokeWidth.toPx()
            val diameter = kotlin.math.min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val sweepTotal = 270f
            val startAngle = 135f
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepTotal * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** A compact "hero" KPI tile — big value, small label, optional tint. */
@Composable
fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    AppCard(modifier = modifier, containerColor = color.copy(alpha = 0.10f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
