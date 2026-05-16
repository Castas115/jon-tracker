package com.joncas.jontracker.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.CalendarEvent
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.data.appliesOn
import com.joncas.jontracker.data.isCompletedOn
import com.joncas.jontracker.data.mondayOfWeek
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

@Composable
fun WeekScreen() {
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    val tasks by TaskRepo.tasks.collectAsState()
    val events by TaskRepo.events.collectAsState()
    val scope = rememberCoroutineScope()

    val monday = remember(anchor) { anchor.mondayOfWeek() }
    val days = remember(monday) { (0..6L).map { monday.plusDays(it) } }
    val weekNum = remember(monday) {
        monday.get(WeekFields.ISO.weekOfWeekBasedYear())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { anchor = anchor.minusWeeks(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
            }
            Text(
                "Week $weekNum · ${monday.format(LABEL)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { anchor = anchor.plusWeeks(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
            }
            TextButton(onClick = { anchor = LocalDate.now() }) { Text("This") }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEach { d ->
                val items = itemsFor(d, tasks, events)
                item(key = "h${d}") { DayHeader(d) }
                if (items.isEmpty()) {
                    item(key = "e${d}") {
                        Text(
                            "—",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                } else {
                    items.forEach { row ->
                        item(key = "${d}-${row.key}") {
                            when (row) {
                                is Row.TaskRow -> WeekTaskRow(
                                    task = row.task,
                                    date = d,
                                    onToggle = { scope.launch { TaskRepo.toggle(row.task, d) } },
                                )
                                is Row.EventRow -> WeekEventRow(title = row.title, time = row.time)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val isToday = date == LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekTaskRow(task: Task, date: LocalDate, onToggle: () -> Unit) {
    val done = task.isCompletedOn(date)
    val actionable = task.is_todo && task.task_type != "weekly_goal"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = actionable, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actionable) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
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
private fun WeekEventRow(title: String, time: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x334285F4))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF4285F4)),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
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

private sealed class Row {
    abstract val key: String
    data class TaskRow(val task: Task) : Row() { override val key = "t${task.id}" }
    data class EventRow(val id: String, val title: String, val time: String?) : Row() { override val key = "e$id" }
}

private fun itemsFor(date: LocalDate, tasks: List<Task>, events: List<CalendarEvent>): List<Row> {
    val taskRows = tasks
        .filter { it.appliesOn(date) }
        .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))
        .map { Row.TaskRow(it) }

    val k = date.toString()
    val eventRows = events.mapNotNull { ev ->
        val evK = if (ev.all_day) ev.start
        else runCatching { LocalDateTime.parse(ev.start, ISO_DT).toLocalDate().toString() }.getOrNull()
        if (evK != k) return@mapNotNull null
        val time = if (!ev.all_day) {
            runCatching { LocalDateTime.parse(ev.start, ISO_DT).toLocalTime().toString().take(5) }.getOrNull()
        } else null
        Row.EventRow(ev.id, ev.title, time)
    }.sortedBy { it.time ?: "" }

    return taskRows + eventRows
}

private val LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
