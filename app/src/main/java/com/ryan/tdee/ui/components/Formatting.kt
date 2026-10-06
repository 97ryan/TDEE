package com.ryan.tdee.ui.components

import com.ryan.tdee.core.EnergyUnit
import com.ryan.tdee.core.WeightUnit
import com.ryan.tdee.core.formatNumber
import com.ryan.tdee.core.formatSigned
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val longDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val tableDate = DateTimeFormatter.ofPattern("d MMM yyyy")

fun WeightUnit.format(kg: Double) = formatNumber(fromKg(kg), 2)
fun WeightUnit.formatRate(kgPerWeek: Double) = formatSigned(fromKg(kgPerWeek), 2)
fun EnergyUnit.format(kcal: Double) = formatNumber(fromKcal(kcal), 0)
fun EnergyUnit.formatSigned(kcal: Double) = formatSigned(fromKcal(kcal), 0)

fun dateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(longDate)
}

fun tableDateLabel(date: LocalDate): String = date.format(tableDate)
