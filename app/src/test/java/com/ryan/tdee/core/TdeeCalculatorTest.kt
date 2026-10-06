package com.ryan.tdee.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TdeeCalculatorTest {
    private val start = LocalDate.of(2026, 1, 1)

    /** Weight changing by [kgPerDay] every day while eating [intake]. */
    private fun steady(days: Int, kgPerDay: Double, intake: Double) = (0 until days).map {
        Entry(start.plusDays(it.toLong()), 70.0 + kgPerDay * it, intake)
    }

    @Test
    fun maintenanceEqualsIntakeWhenWeightIsFlat() {
        val calc = TdeeCalculator(steady(30, 0.0, 2500.0), 21, Algorithm.CLASSIC)
        assertEquals(2500.0, calc.estimate(start.plusDays(29))!!.tdee, 1e-6)
    }

    @Test
    fun gainingWeightMeansTdeeBelowIntake() {
        val calc = TdeeCalculator(steady(30, 0.05, 3000.0), 21, Algorithm.CLASSIC)
        val estimate = calc.estimate(start.plusDays(29))!!
        assertEquals(0.05, estimate.weightSlopeKgPerDay, 1e-9)
        assertEquals(3000.0 - KCAL_PER_KG * 0.05, estimate.tdee, 1e-6)
    }

    @Test
    fun smoothedAgreesOnPerfectlyLinearData() {
        val calc = TdeeCalculator(steady(120, -0.03, 2200.0), 21, Algorithm.SMOOTHED)
        assertEquals(2200.0 + KCAL_PER_KG * 0.03, calc.estimate(start.plusDays(119))!!.tdee, 1e-6)
    }

    @Test
    fun classicWindowSpansDaysPlusOne() {
        // An outlier exactly 21 days back is inside the window; 22 days back is outside.
        val base = steady(40, 0.0, 2500.0).toMutableList()
        base[39 - 22] = base[39 - 22].copy(calories = 10_000.0)
        val calc = TdeeCalculator(base, 21, Algorithm.CLASSIC)
        assertEquals(2500.0, calc.estimate(start.plusDays(39))!!.tdee, 1e-6)
        assertEquals(2500.0 + 7500.0 / 22, calc.estimate(start.plusDays(38))!!.tdee, 1e-6)
    }

    @Test
    fun needsTwoWeighInsAndSomeIntake() {
        val one = listOf(Entry(start, 70.0, 2000.0))
        assertNull(TdeeCalculator(one, 21, Algorithm.CLASSIC).estimate(start))
        val noIntake = listOf(Entry(start, 70.0, null), Entry(start.plusDays(1), 70.1, null))
        assertNull(TdeeCalculator(noIntake, 21, Algorithm.CLASSIC).estimate(start.plusDays(1)))
    }

    @Test
    fun startDateExcludesEarlierEntries() {
        val entries = steady(10, 0.0, 2500.0) + Entry(start.minusDays(5), 90.0, 9000.0)
        val calc = TdeeCalculator(entries, 21, Algorithm.CLASSIC, startDate = start)
        assertEquals(2500.0, calc.estimate(start.plusDays(9))!!.tdee, 1e-6)
        assertEquals(10, calc.daysLogged(start.plusDays(9)))
    }

    @Test
    fun planAddsGoalSurplus() {
        val plan = Plan.of(Estimate(tdee = 2949.0, weightSlopeKgPerDay = 0.004, averageIntake = 2977.0), 0.25)
        assertEquals(2949.0 + 0.25 * KCAL_PER_KG / 7, plan.needToEat, 1e-6)
        assertEquals(plan.needToEat - 2977.0, plan.calorieChangeNeeded, 1e-6)
        assertEquals(0.028, plan.weightChangePerWeekKg, 1e-9)
    }

    @Test
    fun trailingAverageUsesCalendarWindow() {
        val points = listOf(DayValue(0, 1.0), DayValue(1, 3.0), DayValue(5, 5.0))
        val avg = trailingAverage(points, 3)
        assertEquals(listOf(1.0, 2.0, 5.0), avg.map { it.value })
        assertEquals(3.0, points.latestOnOrBefore(4))
        assertNull(points.valueOn(4))
    }
}
