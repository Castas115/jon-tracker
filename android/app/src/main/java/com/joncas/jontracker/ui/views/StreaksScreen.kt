package com.joncas.jontracker.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.data.goalStatus
import com.joncas.jontracker.data.mondayOfWeek
import com.joncas.jontracker.data.segmentLabel
import com.joncas.jontracker.data.streakInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val WEEKS = 18
private val WEEKDAY_SHORT = listOf("M", "T", "W", "T", "F", "S", "S")
private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
fun StreaksScreen() {
    val tasks by TaskRepo.tasks.collectAsState()
    val today = LocalDate.now()

    val goals = tasks
        .filter { it.task_type == "weekly_goal" }
        .map { t ->
            val streak = t.streakInfo(today)
            val status = t.goalStatus(today)
            GoalCard(
                task = t,
                current = streak.current,
                best = streak.best,
                weekDone = status.done,
                weekTarget = status.target,
                weekHit = status.hit,
            )
        }
        .sortedWith(
            compareByDescending<GoalCard> { it.current }
                .thenByDescending { it.best }
                .thenBy { it.task.title }
        )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (goals.isEmpty()) {
            Text(
                "No weekly goals yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = goals, key = { it.task.id }) { card ->
                    StreakCard(card, today)
                }
            }
        }
    }
}

private data class GoalCard(
    val task: Task,
    val current: Int,
    val best: Int,
    val weekDone: Int,
    val weekTarget: Int,
    val weekHit: Boolean,
)

@Composable
private fun StreakCard(card: GoalCard, today: LocalDate) {
    val segs = card.task.target_segments ?: emptyList()
    val sub = if (segs.isEmpty()) "${card.task.target_per_week ?: 0}/wk"
    else segs.joinToString(" + ") { "${it.target}x ${segmentLabel(it)}" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${flame(card.current)} ${card.current}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "best ${card.best} · ${card.weekDone}/${card.weekTarget} wk",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (card.weekHit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (card.weekHit) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Heatmap(card.task, today)
    }
}

@Composable
private fun Heatmap(task: Task, today: LocalDate) {
    val map = remember(task, today) { buildHeatmap(task, today) }
    val cellSize = 14.dp
    val gap = 2.dp
    val accent = MaterialTheme.colorScheme.primary
    val baseBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        // Month strip
        Row(
            modifier = Modifier.padding(start = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            for ((i, monday) in map.weeks.withIndex()) {
                val show = i == 0 || monday.month != map.weeks[i - 1].month
                Text(
                    text = if (show) monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(cellSize),
                )
            }
        }
        Row {
            // Weekday column
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for ((i, w) in WEEKDAY_SHORT.withIndex()) {
                    Text(
                        text = w,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (i % 2 == 1) 0.5f else 0.8f),
                        modifier = Modifier.size(width = 18.dp, height = cellSize),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (wd in 0..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (wi in 0 until WEEKS) {
                            val cell = map.cells[wd][wi]
                            val bg = when {
                                !cell.inActive || cell.isFuture -> Color.Transparent
                                !cell.inSegment -> baseBg.copy(alpha = 0.3f)
                                else -> baseBg.mixWith(accent, intensity(cell.count))
                            }
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(bg)
                                    .then(
                                        if (cell.isToday) Modifier.border(
                                            1.dp,
                                            accent,
                                            RoundedCornerShape(3.dp),
                                        )
                                        else if (!cell.inActive || cell.isFuture) Modifier.border(
                                            1.dp,
                                            outline,
                                            RoundedCornerShape(3.dp),
                                        )
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CellInfo(
    val date: LocalDate,
    val inActive: Boolean,
    val inSegment: Boolean,
    val count: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
)

private data class HeatmapData(
    val weeks: List<LocalDate>, // monday of each week shown
    val cells: Array<Array<CellInfo>>, // [weekday][week index]
)

private fun buildHeatmap(task: Task, today: LocalDate): HeatmapData {
    val lastMonday = today.mondayOfWeek()
    val weeks = (WEEKS - 1 downTo 0).map { lastMonday.minusWeeks(it.toLong()) }

    val counts = HashMap<String, Int>()
    for (d in task.completed_dates) counts.merge(d, 1) { a, b -> a + b }

    val segWeekdays = (task.target_segments ?: emptyList()).flatMap { it.weekdays }.toSet()
    val flatTarget = (task.target_segments ?: emptyList()).isEmpty()
    val start = task.start_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = task.end_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val cells = Array(7) { wd ->
        Array(weeks.size) { wi ->
            val d = weeks[wi].plusDays(wd.toLong())
            val k = d.format(ISO)
            val inActive = (start == null || !d.isBefore(start)) && (end == null || !d.isAfter(end))
            val inSegment = flatTarget || segWeekdays.contains(wd)
            CellInfo(
                date = d,
                inActive = inActive,
                inSegment = inSegment,
                count = counts[k] ?: 0,
                isToday = d == today,
                isFuture = d.isAfter(today),
            )
        }
    }
    return HeatmapData(weeks, cells)
}

private fun intensity(count: Int): Float = when {
    count <= 0 -> 0f
    count >= 4 -> 1f
    else -> 0.2f + 0.25f * count
}

private fun Color.mixWith(other: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = red * (1 - u) + other.red * u,
        green = green * (1 - u) + other.green * u,
        blue = blue * (1 - u) + other.blue * u,
        alpha = alpha * (1 - u) + other.alpha * u,
    )
}

private fun flame(n: Int): String = when {
    n == 0 -> "·"
    n >= 12 -> "🔥🔥🔥"
    n >= 6 -> "🔥🔥"
    else -> "🔥"
}
