package com.ryan.tdee.core

import java.time.LocalDate

/** Plausible ranges for logged values, in kg and kcal. Anything outside is treated as a typo. */
object Limits {
    const val MIN_WEIGHT_KG = 1.0
    const val MAX_WEIGHT_KG = 700.0
    const val MAX_CALORIES = 30_000.0

    /** The earliest day the app accepts; also the date picker's lower bound. */
    val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)

    fun isValidWeightKg(kg: Double) = kg.isFinite() && kg in MIN_WEIGHT_KG..MAX_WEIGHT_KG

    fun isValidCalories(kcal: Double) = kcal.isFinite() && kcal in 0.0..MAX_CALORIES

    /** Allows one day past [today] so a phone in a different time zone to the file's author still works. */
    fun isValidDate(date: LocalDate, today: LocalDate = LocalDate.now()) =
        date >= EARLIEST_DATE && date <= today.plusDays(1)
}
