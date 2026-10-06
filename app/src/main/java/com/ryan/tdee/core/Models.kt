package com.ryan.tdee.core

import java.time.LocalDate

/** One day's log. Weight is always stored in kilograms and energy in kilocalories. */
data class Entry(val date: LocalDate, val weightKg: Double?, val calories: Double?)

/** A value on a given day, with the day expressed as [LocalDate.toEpochDay]. */
data class DayValue(val day: Long, val value: Double)

/** Energy stored in one kilogram of body-weight change (the classic 3500 kcal per pound). */
const val KCAL_PER_KG = 7716.17

enum class Algorithm { CLASSIC, SMOOTHED }

enum class WeightUnit(val label: String, val csvHeader: String, private val perKg: Double) {
    KG("kg", "Kilograms", 1.0),
    LB("lb", "Pounds", 2.20462262185);

    fun fromKg(kg: Double) = kg * perKg
    fun toKg(value: Double) = value / perKg
}

enum class EnergyUnit(val label: String, val csvHeader: String, private val perKcal: Double) {
    KCAL("kcal", "Calories", 1.0),
    KJ("kJ", "Kilojoules", 4.184);

    fun fromKcal(kcal: Double) = kcal * perKcal
    fun toKcal(value: Double) = value / perKcal
}
