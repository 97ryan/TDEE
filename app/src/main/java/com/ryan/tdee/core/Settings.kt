package com.ryan.tdee.core

import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Seed colours for the theme when wallpaper colours are off. [WHITE] gives a monochrome theme. */
enum class AccentColor(val label: String, val argb: Int) {
    BLUE("Blue", 0xFF669DFF.toInt()),
    CYAN("Cyan", 0xFF00B0C8.toInt()),
    GREEN("Green", 0xFF4DB65A.toInt()),
    LIME("Lime", 0xFF86AC2B.toInt()),
    YELLOW("Yellow", 0xFFB39D00.toInt()),
    ORANGE("Orange", 0xFFE67E22.toInt()),
    RED("Red", 0xFFF0706A.toInt()),
    PINK("Pink", 0xFFE66EA4.toInt()),
    MAGENTA("Magenta", 0xFFD272CF.toInt()),
    PURPLE("Purple", 0xFFAE86F0.toInt()),
    WHITE("White", 0xFFFFFFFF.toInt()),
}

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
    val accent: AccentColor = AccentColor.BLUE,
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
