package com.ryan.tdee.core

import java.time.LocalDate

/** Series for the progress graph, in kilograms and kilocalories. */
class GraphData(
    val weight: List<DayValue>,
    val weightTrend: List<DayValue>,
    val calories: List<DayValue>,
    val caloriesTrend: List<DayValue>,
    val tdee: List<DayValue>,
    val tdeeTrend: List<DayValue>,
) {
    val firstDay: Long? = listOfNotNull(weight.firstOrNull(), calories.firstOrNull()).minOfOrNull { it.day }
    val lastDay: Long? = listOfNotNull(weight.lastOrNull(), calories.lastOrNull()).maxOfOrNull { it.day }
}

/** Everything derived from the log and settings, computed once per change. */
class Analysis(entries: List<Entry>, val settings: Settings) {
    val entries: List<Entry> = entries.sortedBy { it.date }
    private val byDate = this.entries.associateBy { it.date }
    private val calculator = TdeeCalculator(this.entries, settings.days, settings.algorithm, settings.startDate)

    fun entry(date: LocalDate): Entry? = byDate[date]

    fun plan(date: LocalDate): Plan? = calculator.estimate(date)?.let { Plan.of(it, settings.goalKgPerWeek) }

    fun daysLogged(date: LocalDate): Long = calculator.daysLogged(date)

    fun previousCalories(date: LocalDate): Double? =
        entries.lastOrNull { it.date < date && it.calories != null }?.calories

    /** TDEE for every logged day, as shown in the table and written to CSV exports. */
    val tdeeByDate: Map<LocalDate, Double> by lazy {
        buildMap { entries.forEach { e -> calculator.estimate(e.date)?.let { put(e.date, it.tdee) } } }
    }

    val graph: GraphData by lazy {
        val weight = entries.mapNotNull { e -> e.weightKg?.let { DayValue(e.date.toEpochDay(), it) } }
        val calories = entries.mapNotNull { e -> e.calories?.let { DayValue(e.date.toEpochDay(), it) } }
        val tdee = entries.mapNotNull { e -> tdeeByDate[e.date]?.let { DayValue(e.date.toEpochDay(), it) } }
        GraphData(
            weight = weight,
            weightTrend = trailingAverage(weight, settings.weightSmoothing),
            calories = calories,
            caloriesTrend = trailingAverage(calories, settings.calorieSmoothing),
            tdee = tdee,
            tdeeTrend = trailingAverage(tdee, settings.tdeeSmoothing),
        )
    }
}
