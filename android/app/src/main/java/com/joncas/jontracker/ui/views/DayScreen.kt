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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
private val ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

@Composable
fun DayScreen() {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val tasks by TaskRepo.tasks.collectAsState()
    val events by TaskRepo.events.collectAsState()
    val scope = rememberCoroutineScope()

    val applicable = remember(tasks, date) { tasks.filter { it.appliesOn(date) } }
    val birthdays = applicable.filter { it.task_type == "birthday" }
    val allDay = applicable
        .filter { it.start_time == null && it.task_type != "birthday" }
        .sortedBy { it.title }
    val timed = applicable
        .filter { it.start_time != null }
        .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))

    val dayEvents = remember(events, date) { eventsOn(events, date) }
    val bdayEvents = dayEvents.filter { it.kind == "birthday" }
    val allDayEvents = dayEvents.filter { it.all_day && it.kind != "birthday" }
    val timedEvents = dayEvents
        .filter { !it.all_day }
        .sortedBy { it.start }

    Column(modifier = Modifier.fillMaxSize()) {
        DateHeader(
            date = date,
            onPrev = { date = date.minusDays(1) },
            onNext = { date = date.plusDays(1) },
            onToday = { date = LocalDate.now() },
        )
        HorizontalDivider()
        if (birthdays.isNotEmpty() || bdayEvents.isNotEmpty()) {
            val titles = (birthdays.map { it.title } + bdayEvents.map { it.title }).joinToString(" · ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33C97A8A))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎂 ", style = MaterialTheme.typography.bodyLarge)
                Text(titles, style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (allDay.isNotEmpty() || allDayEvents.isNotEmpty()) {
                item { SectionLabel("All day") }
                items(allDay, key = { "t${it.id}" }) { t ->
                    TaskRow(
                        task = t,
                        date = date,
                        onToggle = { scope.launch { TaskRepo.toggle(t, date) } },
                    )
                }
                items(allDayEvents, key = { "e${it.id}" }) { ev ->
                    EventRow(title = ev.title, time = null)
                }
            }
            if (timed.isNotEmpty() || timedEvents.isNotEmpty()) {
                item { SectionLabel("Scheduled") }
                items(timed, key = { "t${it.id}" }) { t ->
                    TaskRow(
                        task = t,
                        date = date,
                        onToggle = { scope.launch { TaskRepo.toggle(t, date) } },
                    )
                }
                items(timedEvents, key = { "e${it.id}" }) { ev ->
                    val s = runCatching { LocalDateTime.parse(ev.start, ISO_DT) }.getOrNull()
                    val time = s?.toLocalTime()?.toString()?.take(5)
                    EventRow(title = ev.title, time = time)
                }
            }
            if (applicable.isEmpty() && dayEvents.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nothing scheduled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val isToday = date == LocalDate.now()
    val label = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }}, ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
        }
        TextButton(onClick = onToday) { Text("Today") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun TaskRow(task: Task, date: LocalDate, onToggle: () -> Unit) {
    val done = task.isCompletedOn(date)
    val actionable = task.is_todo && task.task_type != "weekly_goal"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = actionable, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val tline = buildString {
                if (task.start_time != null) {
                    append(task.start_time)
                    if (task.end_time != null) {
                        append('–').append(task.end_time)
                    }
                }
            }
            if (tline.isNotEmpty()) {
                Text(
                    tline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventRow(title: String, time: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x334285F4))
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (time != null) {
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "G",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun eventsOn(events: List<CalendarEvent>, date: LocalDate): List<CalendarEvent> {
    val k = date.format(ISO)
    return events.filter { ev ->
        val evK = if (ev.all_day) ev.start
        else runCatching { LocalDateTime.parse(ev.start, ISO_DT).toLocalDate().format(ISO) }.getOrNull()
        evK == k
    }
}
