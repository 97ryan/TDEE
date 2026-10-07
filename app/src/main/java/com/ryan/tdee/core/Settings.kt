package com.ryan.tdee.core

import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How a series is drawn on the graph. Tapping its toggle cycles through these in order. */
enum class SeriesMode {
    BOTH, POINTS, LINE, OFF;

    val showPoints get() = this == BOTH || this == POINTS
    val showLine get() = this == BOTH || this == LINE

    fun next(): SeriesMode = entries[(ordinal + 1) % entries.size]
}

enum class GraphRange(val label: String, val days: Long?) {
    MONTH("1M", 30),
    QUARTER("3M", 91),
    HALF_YEAR("6M", 182),
    YEAR("1Y", 365),
    ALL("All", null),
}

data class Settings(
    val goalKgPerWeek: Double = 0.0,
    val days: Int = 21,
    val startDate: LocalDate? = null,
    val algorithm: Algorithm = Algorithm.CLASSIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
    val weightSmoothing: Int = 21,
    val calorieSmoothing: Int = 7,
    val tdeeSmoothing: Int = 21,
    val weightMode: SeriesMode = SeriesMode.BOTH,
    val calorieMode: SeriesMode = SeriesMode.BOTH,
    val tdeeMode: SeriesMode = SeriesMode.BOTH,
    val graphRange: GraphRange = GraphRange.ALL,
)
