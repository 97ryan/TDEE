package com.ryan.tdee.core

/**
 * Trailing moving average: each output point is the mean of the inputs within the [windowDays]
 * calendar days ending on that point's day. [points] must be sorted by day.
 */
fun trailingAverage(points: List<DayValue>, windowDays: Int): List<DayValue> {
    if (windowDays <= 1 || points.size < 2) return points
    val result = ArrayList<DayValue>(points.size)
    var start = 0
    var sum = 0.0
    for (end in points.indices) {
        sum += points[end].value
        while (points[start].day <= points[end].day - windowDays) {
            sum -= points[start].value
            start++
        }
        result += DayValue(points[end].day, sum / (end - start + 1))
    }
    return result
}

/** The value logged exactly on [day], or null. [this] must be sorted by day. */
fun List<DayValue>.valueOn(day: Long): Double? {
    val i = binarySearch { it.day.compareTo(day) }
    return if (i >= 0) this[i].value else null
}

/** The latest value on or before [day], or null. [this] must be sorted by day. */
fun List<DayValue>.latestOnOrBefore(day: Long): Double? {
    val i = binarySearch { it.day.compareTo(day) }
    return when {
        i >= 0 -> this[i].value
        -i - 2 >= 0 -> this[-i - 2].value
        else -> null
    }
}
