package com.ryan.tdee.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ChartMathTest {
    @Test
    fun weightAxisCoversTheDataWithRoundNumbers() {
        val axis = niceAxis(70.1, 80.4, 5)
        assertEquals(2.5, axis.step, 1e-9)
        assertEquals(70.0, axis.min, 1e-9)
        assertEquals(82.5, axis.max, 1e-9)
        assertEquals(listOf(70.0, 72.5, 75.0, 77.5, 80.0, 82.5), axis.ticks)
    }

    @Test
    fun aFlatSeriesStillGetsAnAxis() {
        val axis = niceAxis(79.0, 79.0, 5)
        assertTrue(axis.min < 79.0 && axis.max > 79.0)
        assertTrue(axis.ticks.size in 3..12)
        val right = fittedAxis(2800.0, 2800.0, axis.intervals)
        assertTrue(right.min < 2800.0 && right.max > 2800.0)
    }

    @Test
    fun rightAxisMatchesTheLeftAxisGridLines() {
        val left = niceAxis(70.1, 80.4, 5)
        val right = fittedAxis(2100.0, 3450.0, left.intervals)
        assertEquals(left.intervals, right.intervals)
        assertTrue(right.min <= 2100.0 && right.max >= 3450.0)
        assertEquals(left.ticks.size, right.ticks.size)
    }

    @Test
    fun axesAlwaysCoverTheirRangeAndTerminate() {
        val random = Random(42)
        repeat(5_000) {
            val scale = 10.0.pow(random.nextInt(-3, 7))
            val lo = (random.nextDouble() - 0.4) * scale
            val hi = lo + random.nextDouble() * scale * random.nextInt(0, 3)
            val left = niceAxis(lo, hi, 5)
            assertTrue("left $lo..$hi -> $left", left.min <= lo + 1e-9 && left.max >= hi - 1e-9)
            assertTrue(left.ticks.isNotEmpty() && left.ticks.size <= 50)
            val right = fittedAxis(lo, hi, left.intervals.coerceAtLeast(2))
            assertTrue("right $lo..$hi -> $right", right.min <= lo + 1e-9 && right.max >= hi - 1e-9)
        }
    }

    @Test
    fun anAxisNeverHangsOrCrashesOnOddNumbers() {
        listOf(0.0, -0.0, 1e-12, 1e12, 1e300, -1e300, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            val left = niceAxis(value, value, 5)
            assertTrue(left.ticks.size <= 50)
            fittedAxis(value, value, 4)
        }
    }

    private fun ticksFor(first: LocalDate, last: LocalDate) = dateTicks(first.toEpochDay(), last.toEpochDay())

    @Test
    fun dateTicksAreFewOrderedAndInsideTheRange() {
        val start = LocalDate.of(2025, 8, 20)
        for (span in listOf(0L, 1L, 2L, 7L, 30L, 62L, 63L, 91L, 150L, 182L, 365L, 411L, 800L, 3000L)) {
            val end = start.plusDays(span)
            val ticks = ticksFor(start, end)
            assertTrue("span $span: ${ticks.size} ticks", ticks.size in 1..7)
            assertEquals(ticks.map { it.first }.sorted(), ticks.map { it.first })
            assertTrue(ticks.all { it.first in start.toEpochDay()..end.toEpochDay() })
            assertEquals("labels should differ: $ticks", ticks.size, ticks.map { it.second }.toSet().size)
        }
    }

    @Test
    fun longRangesLabelMonthsAndYears() {
        // The span of the original data: 20 Aug 2025 to 5 Oct 2026.
        val ticks = ticksFor(LocalDate.of(2025, 8, 20), LocalDate.of(2026, 10, 5))
        assertEquals(listOf("Oct '25", "Jan '26", "Apr '26", "Jul '26", "Oct '26"), ticks.map { it.second })
        assertEquals(listOf("5 Oct", "12 Oct", "19 Oct"), ticksFor(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 19)).map { it.second }.take(3))
    }

    private fun Double.pow(exp: Int) = Math.pow(this, exp.toDouble())
}
