package com.ryan.tdee.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ryan.tdee.core.DayValue
import com.ryan.tdee.core.SeriesMode
import com.ryan.tdee.core.formatNumber
import com.ryan.tdee.core.latestOnOrBefore
import com.ryan.tdee.core.valueOn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** One series on the graph: raw daily [points] plus a smoothed [trend] line, already in display units. */
data class ChartSeries(
    val points: List<DayValue>,
    val trend: List<DayValue>,
    val color: Color,
    val rightAxis: Boolean,
    val mode: SeriesMode,
)

private data class Axis(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double>
        get() = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    val intervals: Int get() = ((max - min) / step).roundToLong().toInt()
}

private fun niceStep(raw: Double): Double {
    if (raw <= 0.0 || raw.isNaN()) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val fraction = raw / magnitude
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/** Axis covering [lo]..[hi] with about [target] round-numbered intervals. */
private fun niceAxis(lo: Double, hi: Double, target: Int): Axis {
    val (a, b) = if (hi - lo < 1e-9) (lo - 1) to (hi + 1) else lo to hi
    val step = niceStep((b - a) / target)
    return Axis(floor(a / step) * step, ceil(b / step) * step, step)
}

/** Axis covering [lo]..[hi] with exactly [intervals] intervals, so its grid lines match the other axis. */
private fun fittedAxis(lo: Double, hi: Double, intervals: Int): Axis {
    val (a, b) = if (hi - lo < 1e-9) (lo - 1) to (hi + 1) else lo to hi
    var step = niceStep((b - a) / intervals)
    while (true) {
        val min = floor(a / step) * step
        if (min + step * intervals >= b - 1e-9) return Axis(min, min + step * intervals, step)
        step = niceStep(step * 1.01)
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val monthYear = DateTimeFormatter.ofPattern("MMM yy")

private fun dateTicks(start: Long, end: Long): List<Pair<Long, String>> {
    val span = end - start
    if (span <= 62) {
        val step = listOf(1L, 2L, 7L, 14L).firstOrNull { span / it <= 5 } ?: 14L
        return (start..end step step).map { it to LocalDate.ofEpochDay(it).format(dayMonth) }
    }
    val months = listOf(1, 2, 3, 6, 12, 24).firstOrNull { span / 30.4 / it <= 5 } ?: 24
    val ticks = mutableListOf<Pair<Long, String>>()
    var month = LocalDate.ofEpochDay(start).withDayOfMonth(1).plusMonths(1)
    while (month.toEpochDay() <= end) {
        if ((month.year * 12 + month.monthValue - 1) % months == 0) {
            ticks += month.toEpochDay() to month.format(monthYear)
        }
        month = month.plusMonths(1)
    }
    return ticks
}

private fun range(series: List<ChartSeries>, start: Long, end: Long): Pair<Double, Double>? {
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    for (s in series) {
        for (list in listOf(s.points, s.trend)) {
            for (p in list) {
                if (p.day in start..end) {
                    if (p.value < lo) lo = p.value
                    if (p.value > hi) hi = p.value
                }
            }
        }
    }
    return if (lo <= hi) lo to hi else null
}

private class Geometry {
    var left = 0f
    var width = 1f
}

@Composable
fun TdeeChart(
    series: List<ChartSeries>,
    startDay: Long,
    endDay: Long,
    selectedDay: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    val leftAxis = remember(series, startDay, endDay) {
        val r = range(series.filter { !it.rightAxis }, startDay, endDay)
        niceAxis(r?.first ?: 0.0, r?.second ?: 1.0, 5)
    }
    val rightAxis = remember(series, startDay, endDay, leftAxis) {
        val r = range(series.filter { it.rightAxis }, startDay, endDay)
        fittedAxis(r?.first ?: 0.0, r?.second ?: 1.0, leftAxis.intervals.coerceAtLeast(1))
    }
    val xTicks = remember(startDay, endDay) { dateTicks(startDay, endDay) }

    val spec = tween<Float>(600, easing = FastOutSlowInEasing)
    val xStart by animateFloatAsState(startDay.toFloat(), spec, label = "xStart")
    val xEnd by animateFloatAsState(endDay.toFloat(), spec, label = "xEnd")
    val leftMin by animateFloatAsState(leftAxis.min.toFloat(), spec, label = "leftMin")
    val leftMax by animateFloatAsState(leftAxis.max.toFloat(), spec, label = "leftMax")
    val rightMin by animateFloatAsState(rightAxis.min.toFloat(), spec, label = "rightMin")
    val rightMax by animateFloatAsState(rightAxis.max.toFloat(), spec, label = "rightMax")
    val pointAlpha = series.map { animateFloatAsState(if (it.mode.showPoints) 1f else 0f, spec, label = "points").value }
    val lineAlpha = series.map { animateFloatAsState(if (it.mode.showLine) 1f else 0f, spec, label = "line").value }

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val geometry = remember { Geometry() }
    val currentStart by rememberUpdatedState(startDay)
    val currentEnd by rememberUpdatedState(endDay)
    val select by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedDay)
    fun dayAt(x: Float): Long {
        val fraction = ((x - geometry.left) / geometry.width).coerceIn(0f, 1f)
        return (currentStart + fraction * (currentEnd - currentStart)).roundToLong()
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset ->
                    val day = dayAt(offset.x)
                    select(if (currentSelected == day) null else day)
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { select(dayAt(it.x)) },
                    onHorizontalDrag = { change, _ -> select(dayAt(change.position.x)) },
                )
            },
    ) {
        val leftLabels = leftAxis.ticks.map { formatNumber(it, 2) }
        val rightLabels = rightAxis.ticks.map { formatNumber(it, 0) }
        val leftWidth = (leftLabels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0) + 8.dp.toPx()
        val rightWidth = (rightLabels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0) + 8.dp.toPx()
        val plot = Rect(leftWidth, 8.dp.toPx(), size.width - rightWidth, size.height - 24.dp.toPx())
        geometry.left = plot.left
        geometry.width = plot.width.coerceAtLeast(1f)

        val xSpan = (xEnd - xStart).coerceAtLeast(1f)
        fun x(day: Long) = plot.left + (day - xStart) / xSpan * plot.width
        fun y(value: Double, right: Boolean): Float {
            val lo = if (right) rightMin else leftMin
            val hi = if (right) rightMax else leftMax
            val span = (hi - lo).coerceAtLeast(1e-6f)
            return plot.bottom - ((value.toFloat() - lo) / span) * plot.height
        }

        // Horizontal grid shared by both axes, with labels on each side.
        leftAxis.ticks.forEachIndexed { i, tick ->
            val ty = y(tick, false)
            if (ty < plot.top - 1 || ty > plot.bottom + 1) return@forEachIndexed
            drawLine(gridColor, Offset(plot.left, ty), Offset(plot.right, ty), 1f)
            drawLabel(measurer, leftLabels[i], labelStyle, Offset(plot.left - 6.dp.toPx(), ty), alignEnd = true)
        }
        rightAxis.ticks.forEachIndexed { i, tick ->
            val ty = y(tick, true)
            if (ty < plot.top - 1 || ty > plot.bottom + 1) return@forEachIndexed
            drawLabel(measurer, rightLabels[i], labelStyle, Offset(plot.right + 6.dp.toPx(), ty), alignEnd = false)
        }
        // Vertical grid at date ticks.
        xTicks.forEach { (day, label) ->
            val tx = x(day)
            if (tx < plot.left || tx > plot.right) return@forEach
            drawLine(gridColor.copy(alpha = 0.5f), Offset(tx, plot.top), Offset(tx, plot.bottom), 1f)
            val layout = measurer.measure(label, labelStyle)
            val left = (tx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(left, plot.bottom + 6.dp.toPx()))
        }

        clipRect(plot.left - 4.dp.toPx(), plot.top - 4.dp.toPx(), plot.left + plot.width * reveal.value + 4.dp.toPx(), plot.bottom + 4.dp.toPx()) {
            series.forEachIndexed { i, s ->
                if (pointAlpha[i] > 0f) {
                    val color = s.color.copy(alpha = 0.45f * pointAlpha[i])
                    val radius = 2.5.dp.toPx()
                    for (p in s.points) {
                        if (p.day < startDay - 1 || p.day > endDay + 1) continue
                        drawCircle(color, radius, Offset(x(p.day), y(p.value, s.rightAxis)))
                    }
                }
                if (lineAlpha[i] > 0f && s.trend.size > 1) {
                    val path = Path()
                    s.trend.forEachIndexed { j, p ->
                        val px = x(p.day)
                        val py = y(p.value, s.rightAxis)
                        if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path,
                        s.color.copy(alpha = lineAlpha[i]),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }

        // Scrub marker.
        if (selectedDay != null && selectedDay in startDay..endDay) {
            val sx = x(selectedDay)
            drawLine(
                markerColor.copy(alpha = 0.6f),
                Offset(sx, plot.top),
                Offset(sx, plot.bottom),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            series.forEachIndexed { i, s ->
                if (s.mode == SeriesMode.OFF) return@forEachIndexed
                val value = (if (s.mode.showLine) s.trend.latestOnOrBefore(selectedDay) else null)
                    ?: s.points.valueOn(selectedDay)
                    ?: return@forEachIndexed
                val center = Offset(sx, y(value, s.rightAxis))
                val alpha = maxOf(pointAlpha[i], lineAlpha[i])
                drawCircle(surface.copy(alpha = alpha), 6.dp.toPx(), center)
                drawCircle(s.color.copy(alpha = alpha), 4.dp.toPx(), center)
            }
        }
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, anchor: Offset, alignEnd: Boolean) {
    val layout = measurer.measure(text, style)
    val left = if (alignEnd) anchor.x - layout.size.width else anchor.x
    val top = (anchor.y - layout.size.height / 2f).coerceIn(0f, abs(size.height - layout.size.height))
    drawText(layout, topLeft = Offset(left, top))
}
