package com.ryan.tdee.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** A graph axis: round-numbered ticks from [min] to [max], [step] apart. */
data class Axis(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double>
        get() = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.take(MAX_TICKS).toList()

    val intervals: Int get() = ((max - min) / step).roundToLong().toInt()

    private companion object {
        const val MAX_TICKS = 50
    }
}

/** The smallest "nice" number (1, 2, 2.5, 5 or 10 times a power of ten) that is at least [raw]. */
fun niceStep(raw: Double): Double {
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
fun niceAxis(lo: Double, hi: Double, target: Int): Axis {
    if (!lo.isFinite() || !hi.isFinite()) return Axis(0.0, target.toDouble(), 1.0)
    val (a, b) = if (hi - lo < 1e-9) (lo - 1) to (hi + 1) else lo to hi
    val step = niceStep((b - a) / target)
    return Axis(floor(a / step) * step, ceil(b / step) * step, step)
}

/** Axis covering [lo]..[hi] with exactly [intervals] intervals, so its grid lines match another axis. */
fun fittedAxis(lo: Double, hi: Double, intervals: Int): Axis {
    val count = intervals.coerceAtLeast(2)
    if (!lo.isFinite() || !hi.isFinite()) return Axis(0.0, count.toDouble(), 1.0)
    val (a, b) = if (hi - lo < 1e-9) (lo - 1) to (hi + 1) else lo to hi
    var step = niceStep((b - a) / count)
    // Each pass moves to the next larger nice step, so this ends once the axis is tall enough. The
    // pass limit is only a safety net: with finite input it is reached at most a few dozen times.
    repeat(MAX_PASSES) {
        val min = floor(a / step) * step
        if (min + step * count >= b - 1e-9) return Axis(min, min + step * count, step)
        step = niceStep(step * 1.01)
    }
    return Axis(a, b, (b - a) / count)
}

private const val MAX_PASSES = 2_000

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val monthYear = DateTimeFormatter.ofPattern("MMM ''yy")

/** Labelled date ticks (epoch day to text) for a graph covering [start]..[end]: at most about six. */
fun dateTicks(start: Long, end: Long): List<Pair<Long, String>> {
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
