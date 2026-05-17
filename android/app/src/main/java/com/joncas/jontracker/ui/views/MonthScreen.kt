package com.joncas.jontracker.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.CalendarEvent
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TrackerApi
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.data.appliesOn
import com.joncas.jontracker.data.displayTitle
import com.joncas.jontracker.data.gridDates
import com.joncas.jontracker.data.isCompletedOn
import com.joncas.jontracker.ui.LocalCreateOnDate
import com.joncas.jontracker.ui.LocalEditTask
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

@Composable
fun MonthScreen() {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val tasks by TaskRepo.tasks.collectAsState()
    val events by TaskRepo.events.collectAsState()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val createOnDate = LocalCreateOnDate.current

    val grid = remember(month) { month.gridDates() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${month.year}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
            }
            TextButton(onClick = {
                month = YearMonth.now()
                selected = LocalDate.now()
            }) { Text("Today") }
        }
        HorizontalDivider()

        // Week-day header
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 6 grid rows
        for (row in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val d = grid[row * 7 + col]
                    val inMonth = d.month == month.month
                    val isToday = d == today
                    val isSelected = d == selected
                    val applicable = tasks.count {
                        it.appliesOn(d) && !(it.task_type == "weekly_goal" && !it.show_in_upcoming)
                    }
                    val evCount = countEventsOn(events, d)
                    DayCell(
                        date = d,
                        inMonth = inMonth,
                        isToday = isToday,
                        isSelected = isSelected,
                        taskDots = applicable.coerceAtMost(3),
                        eventDots = evCount.coerceAtMost(3),
                        onClick = {
                            if (selected == d) createOnDate(d)
                            else selected = d
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        HorizontalDivider()
        // Selected day details
        SelectedDayList(
            date = selected,
            tasks = tasks,
            events = events,
            onAction = { t, a -> scope.launch { TaskRepo.toggle(t, selected, a) } },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    taskDots: Int,
    eventDots: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .height(56.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(taskDots) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary
                ))
            }
            repeat(eventDots) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF4285F4)
                ))
            }
        }
    }
}

@Composable
private fun SelectedDayList(
    date: LocalDate,
    tasks: List<Task>,
    events: List<CalendarEvent>,
    onAction: (Task, TrackerApi.ToggleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val applicable = tasks.filter { it.appliesOn(date) }
        .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))
    val dayEvents = events.filter { ev ->
        val k = if (ev.all_day) ev.start
        else runCatching { LocalDateTime.parse(ev.start, ISO_DT).toLocalDate().toString() }.getOrNull()
        k == date.toString()
    }.sortedBy { it.start }

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }}, ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (applicable.isEmpty() && dayEvents.isEmpty()) {
            Text(
                "Nothing scheduled.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(
                items = applicable,
                key = { "t${it.id}" },
            ) { t ->
                MonthTaskRow(task = t, date = date, onAction = { a -> onAction(t, a) })
            }
            items(
                items = dayEvents,
                key = { "e${it.id}" },
            ) { ev ->
                MonthEventRow(ev)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthTaskRow(task: Task, date: LocalDate, onAction: (TrackerApi.ToggleAction) -> Unit) {
    val done = task.isCompletedOn(date)
    val actionable = task.is_todo
    val isGoal = task.task_type == "weekly_goal"
    val edit = LocalEditTask.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = {
                    when {
                        isGoal -> onAction(TrackerApi.ToggleAction.ADD)
                        actionable -> onAction(TrackerApi.ToggleAction.TOGGLE)
                        else -> edit(task)
                    }
                },
                onLongClick = { edit(task) },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isGoal -> {
                IconButton(onClick = { onAction(TrackerApi.ToggleAction.REMOVE) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Subtract one")
                }
                IconButton(onClick = { onAction(TrackerApi.ToggleAction.ADD) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add one")
                }
            }
            actionable -> Checkbox(
                checked = done,
                onCheckedChange = { onAction(TrackerApi.ToggleAction.TOGGLE) },
            )
            else -> {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.displayTitle(date),
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val tline = task.start_time?.let { s ->
                task.end_time?.let { "$s–$it" } ?: s
            }
            if (tline != null) {
                Text(
                    tline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthEventRow(ev: CalendarEvent) {
    val time = if (ev.all_day) null
    else runCatching { LocalDateTime.parse(ev.start, ISO_DT).toLocalTime().toString().take(5) }.getOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x334285F4))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4285F4)))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ev.title, style = MaterialTheme.typography.bodyMedium)
            if (time != null) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun countEventsOn(events: List<CalendarEvent>, date: LocalDate): Int {
    val k = date.toString()
    return events.count { ev ->
        val evK = if (ev.all_day) ev.start
        else runCatching { LocalDateTime.parse(ev.start, ISO_DT).toLocalDate().toString() }.getOrNull()
        evK == k
    }
}

private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<T>,
    crossinline key: (T) -> Any,
    crossinline itemContent: @Composable (T) -> Unit,
) = items(items.size, key = { key(items[it]) }) { idx -> itemContent(items[idx]) }
