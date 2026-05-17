package com.joncas.jontracker.data

import com.joncas.jontracker.api.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

/** Whether the task should appear on the given date. Mirrors backend semantics. */
fun Task.appliesOn(date: LocalDate): Boolean {
    if (!isActiveOn(date)) return false
    return when (task_type) {
        "recurring" -> weekdays?.contains(date.weekdayMonFirst()) == true
        "single", "birthday" -> fixed_date == date.format(DATE)
        "weekly_goal" -> {
            // Show on every day inside the goal's segments. Flat goals (no
            // segments) fall back to "every day of the week" so they remain
            // tickable wherever the user is.
            val segs = target_segments ?: emptyList()
            if (segs.isEmpty()) true
            else segs.any { it.weekdays.contains(date.weekdayMonFirst()) }
        }
        else -> false
    }
}

/**
 * Recurring/weekly_goal tasks only apply within [start_date, end_date].
 * Other types ignore the range — their schedule is anchored on fixed_date.
 */
fun Task.isActiveOn(date: LocalDate): Boolean {
    if (task_type != "recurring" && task_type != "weekly_goal") return true
    val key = date.format(DATE)
    if (!start_date.isNullOrEmpty() && key < start_date) return false
    if (!end_date.isNullOrEmpty() && key > end_date) return false
    return true
}

fun Task.isCompletedOn(date: LocalDate): Boolean =
    completed_dates.contains(date.format(DATE))

fun Task.isBacklog(): Boolean = when (task_type) {
    "single" -> fixed_date.isNullOrEmpty() && is_todo
    "weekly_goal" -> is_todo
    else -> false
}

/** Completions in the ISO week containing [date]. */
fun Task.completionsThisWeek(date: LocalDate): Int {
    val mon = date.mondayOfWeek()
    val sun = mon.plusDays(6)
    return completed_dates.count {
        runCatching { LocalDate.parse(it) }.getOrNull()?.let { d ->
            !d.isBefore(mon) && !d.isAfter(sun)
        } == true
    }
}
