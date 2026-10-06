package com.ryan.tdee.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CsvFormatTest {
    private val original = """
        "Date","Kilograms","Calories","TDEE"
        "2026-10-05","79.7","","2925"
        "2026-10-04","80.2","2500","2883"
        "2026-09-19","","3000","2422"
        "2025-08-20","70.4","2400",""
    """.trimIndent()

    @Test
    fun readsOriginalExport() {
        val result = CsvFormat.parse(original)
        assertEquals(0, result.skippedRows)
        assertEquals(
            listOf(
                Entry(LocalDate.of(2025, 8, 20), 70.4, 2400.0),
                Entry(LocalDate.of(2026, 9, 19), null, 3000.0),
                Entry(LocalDate.of(2026, 10, 4), 80.2, 2500.0),
                Entry(LocalDate.of(2026, 10, 5), 79.7, null),
            ),
            result.entries,
        )
    }

    @Test
    fun roundTripsInOriginalFormat() {
        val entries = CsvFormat.parse(original).entries
        val tdee = mapOf(LocalDate.of(2026, 10, 5) to 2925.4, LocalDate.of(2026, 10, 4) to 2883.0, LocalDate.of(2026, 9, 19) to 2421.6)
        val out = CsvFormat.export(entries, tdee, WeightUnit.KG, EnergyUnit.KCAL)
        assertEquals(original + "\n", out)
    }

    @Test
    fun readsHeaderlessUnquotedPoundsAndSemicolons() {
        assertEquals(
            listOf(Entry(LocalDate.of(2026, 1, 2), 80.0, 2000.0)),
            CsvFormat.parse("2026-01-02,80,2000").entries,
        )
        val lb = CsvFormat.parse("Date;Pounds;Kilojoules\n2026-01-02;\"176,37\";8368").entries.single()
        assertEquals(80.0, lb.weightKg!!, 0.001)
        assertEquals(2000.0, lb.calories!!, 0.001)
    }

    @Test
    fun skipsInvalidRows() {
        val result = CsvFormat.parse("Date,Kilograms,Calories\nnot-a-date,80,2000\n2026-01-01,abc,2000\n2026-01-02,80,\n,,")
        assertEquals(1, result.entries.size)
        assertEquals(3, result.skippedRows)
    }

    @Test
    fun formatsNumbers() {
        assertEquals("80", formatNumber(80.0, 2))
        assertEquals("79.22", formatNumber(79.2234, 2))
        assertEquals("+0.02", formatSigned(0.0249, 2))
        assertEquals("0", formatSigned(-0.001, 2))
        assertEquals(79.7, parseNumber(" 79,7 "))
    }
}
