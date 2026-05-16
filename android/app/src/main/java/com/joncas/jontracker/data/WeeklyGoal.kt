package com.joncas.jontracker.data

import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TargetSegment
import java.time.LocalDate
import java.time.temporal.WeekFields

data class SegmentProgress(
    val segment: TargetSegment,
    val done: Int,
    val hit: Boolean,
    val exceeded: Boolean,
)

data class GoalStatus(
    val done: Int,
    val target: Int,
    val hit: Boolean,
    val exceeded: Boolean,
    val segments: List<SegmentProgress>,
)

private fun Task.completionsInWeek(refDate: LocalDate, weekdays: List<Int>? = null): Int {
    val refWeek = refDate.get(WeekFields.ISO.weekOfWeekBasedYear())
    val refYear = refDate.year
    return completed_dates.count { d ->
        val cd = runCatching { LocalDate.parse(d) }.getOrNull() ?: return@count false
        if (cd.year != refYear || cd.get(WeekFields.ISO.weekOfWeekBasedYear()) != refWeek) return@count false
        if (weekdays == null) true else weekdays.contains(cd.weekdayMonFirst())
    }
}

fun Task.goalStatus(refDate: LocalDate): GoalStatus {
    val segs = target_segments ?: emptyList()
    if (segs.isEmpty()) {
        val target = target_per_week ?: 0
        val done = completionsInWeek(refDate)
        return GoalStatus(
            done = done,
            target = target,
            hit = target > 0 && done >= target,
            exceeded = target > 0 && done > target,
            segments = emptyList(),
        )
    }
    val perSegment = segs.map { seg ->
        val done = completionsInWeek(refDate, seg.weekdays)
        SegmentProgress(
            segment = seg,
            done = done,
            hit = done >= seg.target,
            exceeded = done > seg.target,
        )
    }
    return GoalStatus(
        done = perSegment.sumOf { it.done },
        target = segs.sumOf { it.target },
        hit = perSegment.all { it.hit },
        exceeded = perSegment.any { it.exceeded },
        segments = perSegment,
    )
}

/** 1-based rank of [date] among completions in the segment that owns its weekday. */
fun Task.weeklyGoalRank(date: LocalDate): Int {
    val wd = date.weekdayMonFirst()
    val filter = (target_segments ?: emptyList()).firstOrNull { it.weekdays.contains(wd) }?.weekdays
    val refWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear())
    val refYear = date.year
    val key = date.toString()
    val same = completed_dates
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { it.year == refYear && it.get(WeekFields.ISO.weekOfWeekBasedYear()) == refWeek }
        .filter { filter == null || filter.contains(it.weekdayMonFirst()) }
        .map { it.toString() }
        .sorted()
    return same.indexOf(key) + 1
}

fun Task.displayTitle(date: LocalDate): String {
    if (task_type != "weekly_goal") return title
    val wd = date.weekdayMonFirst()
    val seg = (target_segments ?: emptyList()).firstOrNull { it.weekdays.contains(wd) }
    val target = seg?.target ?: target_per_week ?: 0
    val rank = weeklyGoalRank(date).coerceAtLeast(0)
    val shown = if (rank == 0) completionsInWeek(date, seg?.weekdays) else rank
    return "$title ($shown/$target)"
}

private val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun segmentLabel(seg: TargetSegment): String {
    val sorted = seg.weekdays.sorted()
    if (sorted.isEmpty()) return ""
    if (sorted.size == 1) return WEEKDAY_SHORT[sorted[0]]
    val contiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (contiguous) "${WEEKDAY_SHORT[sorted.first()]}-${WEEKDAY_SHORT[sorted.last()]}"
    else sorted.joinToString(" · ") { WEEKDAY_SHORT[it] }
}
