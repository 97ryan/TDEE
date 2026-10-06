package com.ryan.tdee.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Rounds to at most [maxDecimals] places and drops trailing zeros: 80.0 → "80", 79.70 → "79.7". */
fun formatNumber(value: Double, maxDecimals: Int): String =
    BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Like [formatNumber] but with an explicit "+" for positive values. */
fun formatSigned(value: Double, maxDecimals: Int): String {
    val text = formatNumber(value, maxDecimals)
    return if (text.startsWith("-") || text == "0") text else "+$text"
}

/** Parses user input, accepting either "." or "," as the decimal separator. */
fun parseNumber(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
