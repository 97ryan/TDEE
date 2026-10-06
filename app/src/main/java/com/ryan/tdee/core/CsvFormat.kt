package com.ryan.tdee.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ImportResult(val entries: List<Entry>, val skippedRows: Int)

/**
 * Reads and writes the original app's CSV format:
 *
 *     "Date","Kilograms","Calories","TDEE"
 *     "2026-10-05","79.7","","2925"
 *
 * Import also accepts files without a header, unquoted fields, a missing TDEE column, ';' or tab
 * delimiters, and a "Pounds" or "Kilojoules" header, which is converted to kg and kcal.
 */
object CsvFormat {

    fun export(
        entries: List<Entry>,
        tdeeByDate: Map<LocalDate, Double>,
        weightUnit: WeightUnit,
        energyUnit: EnergyUnit,
    ): String = buildString {
        appendRow(listOf("Date", weightUnit.csvHeader, energyUnit.csvHeader, "TDEE"))
        for (entry in entries.sortedByDescending { it.date }) {
            appendRow(
                listOf(
                    entry.date.toString(),
                    entry.weightKg?.let { formatNumber(weightUnit.fromKg(it), 2) }.orEmpty(),
                    entry.calories?.let { formatNumber(energyUnit.fromKcal(it), 1) }.orEmpty(),
                    tdeeByDate[entry.date]?.let { formatNumber(energyUnit.fromKcal(it), 0) }.orEmpty(),
                )
            )
        }
    }

    fun parse(text: String): ImportResult {
        val lines = text.removePrefix("﻿").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ImportResult(emptyList(), 0)

        val delimiter = detectDelimiter(lines.first())
        var weightUnit = WeightUnit.KG
        var energyUnit = EnergyUnit.KCAL
        var firstDataLine = 0

        val first = splitRow(lines.first(), delimiter)
        if (parseDate(first.getOrNull(0)) == null) {
            firstDataLine = 1
            val weightHeader = first.getOrNull(1).orEmpty().lowercase()
            val energyHeader = first.getOrNull(2).orEmpty().lowercase()
            if ("pound" in weightHeader || "lb" in weightHeader) weightUnit = WeightUnit.LB
            if ("kilojoule" in energyHeader || "kj" in energyHeader) energyUnit = EnergyUnit.KJ
        }

        val byDate = LinkedHashMap<LocalDate, Entry>()
        var skipped = 0
        for (line in lines.drop(firstDataLine)) {
            val fields = splitRow(line, delimiter)
            val date = parseDate(fields.getOrNull(0))
            val weight = parseOptional(fields.getOrNull(1))
            val calories = parseOptional(fields.getOrNull(2))
            val weightValid = weight == null || (weight.isNaN().not() && weight > 0)
            val caloriesValid = calories == null || (calories.isNaN().not() && calories >= 0)
            if (date == null || !weightValid || !caloriesValid) {
                skipped++
                continue
            }
            if (weight == null && calories == null) continue
            byDate[date] = Entry(date, weight?.let(weightUnit::toKg), calories?.let(energyUnit::toKcal))
        }
        return ImportResult(byDate.values.sortedBy { it.date }, skipped)
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.joinTo(this, ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        append('\n')
    }

    /** Null for a blank field, NaN for one that isn't a number. */
    private fun parseOptional(field: String?): Double? {
        if (field.isNullOrBlank()) return null
        return parseNumber(field) ?: Double.NaN
    }

    private val lenientDate = DateTimeFormatter.ofPattern("uuuu-M-d")
    private val slashDate = DateTimeFormatter.ofPattern("uuuu/M/d")

    private fun parseDate(field: String?): LocalDate? {
        val text = field?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (format in listOf(DateTimeFormatter.ISO_LOCAL_DATE, lenientDate, slashDate)) {
            try {
                return LocalDate.parse(text, format)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun detectDelimiter(line: String): Char {
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var quoted = false
        for (c in line) {
            if (c == '"') quoted = !quoted
            else if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun splitRow(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> {
                    fields += current.toString().trim()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString().trim()
        return fields
    }
}
