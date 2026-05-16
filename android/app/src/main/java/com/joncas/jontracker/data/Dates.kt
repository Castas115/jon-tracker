package com.joncas.jontracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Web app convention: weekday 0 = Monday … 6 = Sunday. */
fun LocalDate.weekdayMonFirst(): Int = (dayOfWeek.value - 1).coerceIn(0, 6)

fun LocalDate.mondayOfWeek(): LocalDate =
    minusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

fun LocalDate.weekDates(): List<LocalDate> {
    val mon = mondayOfWeek()
    return (0..6).map { mon.plusDays(it.toLong()) }
}

/**
 * Cells of the month grid (Mon-first), always 6 rows × 7 cols = 42 dates,
 * padded with adjacent-month days at the head/tail.
 */
fun YearMonth.gridDates(): List<LocalDate> {
    val first = atDay(1)
    val lead = first.weekdayMonFirst()
    val start = first.minusDays(lead.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}
