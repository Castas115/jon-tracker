package com.joncas.jontracker.data

import com.joncas.jontracker.api.Task
import java.time.LocalDate
import java.time.temporal.WeekFields

/** 1-based rank of [date] among the task's completions in the same ISO week. */
fun Task.weeklyGoalRank(date: LocalDate): Int {
    val weekISO = date.get(WeekFields.ISO.weekOfWeekBasedYear())
    val year = date.year
    val key = date.toString()
    val same = completed_dates
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { it.year == year && it.get(WeekFields.ISO.weekOfWeekBasedYear()) == weekISO }
        .map { it.toString() }
        .sorted()
    return same.indexOf(key) + 1
}

fun Task.displayTitle(date: LocalDate): String {
    if (task_type != "weekly_goal") return title
    val rank = weeklyGoalRank(date).coerceAtLeast(0)
    val target = target_per_week ?: 0
    val shown = if (rank == 0) completionsThisWeek(date) else rank
    return "$title ($shown/$target)"
}
