package com.ryan.tdee.core

import java.time.LocalDate
import kotlin.math.pow

/** The raw result of fitting the logged data around one day. */
data class Estimate(
    val tdee: Double,
    val weightSlopeKgPerDay: Double,
    val averageIntake: Double,
)

/** Everything the home screen shows for a day. Energy in kcal, weight in kg. */
data class Plan(
    val tdee: Double,
    val needToEat: Double,
    val calorieChangeNeeded: Double,
    val weightChangePerWeekKg: Double,
) {
    companion object {
        fun of(estimate: Estimate, goalKgPerWeek: Double): Plan {
            val needToEat = estimate.tdee + goalKgPerWeek * KCAL_PER_KG / 7
            return Plan(
                tdee = estimate.tdee,
                needToEat = needToEat,
                calorieChangeNeeded = needToEat - estimate.averageIntake,
                weightChangePerWeekKg = estimate.weightSlopeKgPerDay * 7,
            )
        }
    }
}

/**
 * Estimates TDEE from logged weight and intake using energy balance:
 *
 *     TDEE = average intake − KCAL_PER_KG × (rate of weight change per day)
 *
 * The rate of weight change is the slope of a least-squares line through the weigh-ins, which is
 * far less sensitive to day-to-day water fluctuations than comparing two single weigh-ins.
 *
 * [Algorithm.CLASSIC] reproduces the original app: an unweighted fit over the selected day and the
 * [days] days before it (so "21 days" spans 22 calendar days).
 *
 * [Algorithm.SMOOTHED] fits every day in the last `6 × days` days, weighting each by how recent it is
 * (its weight halves every [days] days). Nothing falls off a hard window edge, so the estimate moves
 * smoothly from day to day instead of jumping when an unusual day drops out.
 */
class TdeeCalculator(
    entries: List<Entry>,
    private val days: Int,
    private val algorithm: Algorithm,
    startDate: LocalDate? = null,
) {
    private val sorted = entries
        .filter { startDate == null || !it.date.isBefore(startDate) }
        .sortedBy { it.date }
    private val epochDays = LongArray(sorted.size) { sorted[it].date.toEpochDay() }

    /** First day included in the calculation, or null when there is no data. */
    val firstDate: LocalDate? = sorted.firstOrNull()?.date

    /** Calendar days from the first included entry up to and including [date]. */
    fun daysLogged(date: LocalDate): Long =
        firstDate?.let { (date.toEpochDay() - it.toEpochDay() + 1).coerceAtLeast(0) } ?: 0

    fun estimate(date: LocalDate): Estimate? {
        val window = days.coerceAtLeast(1)
        return when (algorithm) {
            Algorithm.CLASSIC -> fit(date.toEpochDay(), window.toLong()) { 1.0 }
            Algorithm.SMOOTHED -> {
                val halfLife = window.toDouble()
                fit(date.toEpochDay(), window * 6L) { age -> 0.5.pow(age / halfLife) }
            }
        }
    }

    private inline fun fit(end: Long, lookback: Long, weightForAge: (Double) -> Double): Estimate? {
        val from = lowerBound(end - lookback)

        var weightSum = 0.0
        var xSum = 0.0
        var ySum = 0.0
        var weighIns = 0
        var intakeWeightSum = 0.0
        var intakeSum = 0.0
        var i = from
        while (i < sorted.size && epochDays[i] <= end) {
            val x = (epochDays[i] - end).toDouble()
            val w = weightForAge(-x)
            sorted[i].weightKg?.let {
                weightSum += w
                xSum += w * x
                ySum += w * it
                weighIns++
            }
            sorted[i].calories?.let {
                intakeWeightSum += w
                intakeSum += w * it
            }
            i++
        }
        if (weighIns < 2 || intakeWeightSum == 0.0) return null

        val xMean = xSum / weightSum
        val yMean = ySum / weightSum
        var sxy = 0.0
        var sxx = 0.0
        i = from
        while (i < sorted.size && epochDays[i] <= end) {
            val y = sorted[i].weightKg
            if (y != null) {
                val x = (epochDays[i] - end).toDouble()
                val w = weightForAge(-x)
                sxy += w * (x - xMean) * (y - yMean)
                sxx += w * (x - xMean) * (x - xMean)
            }
            i++
        }
        if (sxx == 0.0) return null

        val slope = sxy / sxx
        val intake = intakeSum / intakeWeightSum
        return Estimate(tdee = intake - KCAL_PER_KG * slope, weightSlopeKgPerDay = slope, averageIntake = intake)
    }

    private fun lowerBound(day: Long): Int {
        var lo = 0
        var hi = epochDays.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (epochDays[mid] < day) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
