package com.joncas.jontracker.data

import com.joncas.jontracker.api.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

/** Whether the task should appear on the given date. Mirrors backend semantics. */
fun Task.appliesOn(date: LocalDate): Boolean {
    return when (task_type) {
        "recurring" -> weekdays?.contains(date.weekdayMonFirst()) == true
        "single", "birthday" -> fixed_date == date.format(DATE)
        "weekly_goal" -> {
            // Weekly goals show every day of the ISO week so user can tick wherever.
            true
        }
        else -> false
    }
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
