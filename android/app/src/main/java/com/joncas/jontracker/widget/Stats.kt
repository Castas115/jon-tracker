package com.joncas.jontracker.widget

import com.joncas.jontracker.api.Task
import com.joncas.jontracker.data.appliesOn
import com.joncas.jontracker.data.goalStatus
import com.joncas.jontracker.data.streakInfo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

/** Combined stats consumed by all three home-screen widgets. */
data class TodoProgress(val done: Int, val total: Int)

data class GoalProgress(
    val title: String,
    val done: Int,
    val target: Int,
    val hit: Boolean,
)

data class StreakLine(
    val title: String,
    val current: Int,
    val best: Int,
)

data class WidgetStats(
    val weekNumber: Int,
    val weekTodos: TodoProgress,
    val weekGoals: List<GoalProgress>,
    val monthLabel: String,
    val monthTodos: TodoProgress,
    val streaks: List<StreakLine>,
)

fun computeStats(tasks: List<Task>, today: LocalDate = LocalDate.now()): WidgetStats {
    val (weekStart, weekEnd) = weekRange(today)
    val (monthStart, monthEnd) = monthRange(today)

    val weekTodos = todoProgress(tasks, weekStart, weekEnd)
    val monthTodos = todoProgress(tasks, monthStart, monthEnd)

    val goals = tasks
        .filter { it.task_type == "weekly_goal" }
        .map { t ->
            val st = t.goalStatus(today)
            GoalProgress(t.title, st.done, st.target, st.hit)
        }
        .sortedWith(compareByDescending<GoalProgress> { it.hit }.thenByDescending { it.done.toFloat() / (it.target.takeIf { it > 0 } ?: 1) })

    val streaks = tasks
        .filter { it.task_type == "weekly_goal" }
        .map { t ->
            val s = t.streakInfo(today)
            StreakLine(t.title, s.current, s.best)
        }
        .sortedWith(compareByDescending<StreakLine> { it.current }.thenByDescending { it.best })

    return WidgetStats(
        weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear()),
        weekTodos = weekTodos,
        weekGoals = goals,
        monthLabel = "${today.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }} ${today.year}",
        monthTodos = monthTodos,
        streaks = streaks,
    )
}

private fun todoProgress(tasks: List<Task>, from: LocalDate, to: LocalDate): TodoProgress {
    var done = 0
    var total = 0
    var d = from
    while (!d.isAfter(to)) {
        val k = d.toString()
        for (t in tasks) {
            if (!t.is_todo) continue
            if (t.task_type == "weekly_goal") continue // counted separately
            if (!t.appliesOn(d)) continue
            total++
            if (k in t.completed_dates) done++
        }
        d = d.plusDays(1)
    }
    return TodoProgress(done, total)
}

private fun weekRange(today: LocalDate): Pair<LocalDate, LocalDate> {
    val monday = today.with(DayOfWeek.MONDAY)
    return monday to monday.plusDays(6)
}

private fun monthRange(today: LocalDate): Pair<LocalDate, LocalDate> {
    val ym = YearMonth.from(today)
    return ym.atDay(1) to ym.atEndOfMonth()
}
