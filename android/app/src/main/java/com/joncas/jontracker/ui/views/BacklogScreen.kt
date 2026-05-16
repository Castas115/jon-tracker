package com.joncas.jontracker.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TaskPayload
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.data.completionsThisWeek
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen() {
    val tasks by TaskRepo.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear())

    val singles = remember(tasks) {
        tasks
            .filter { it.task_type == "single" && it.fixed_date.isNullOrEmpty() && it.is_todo && it.completed_dates.isEmpty() }
            .sortedByDescending { it.created_at }
    }
    val goals = remember(tasks) {
        tasks.filter { it.task_type == "weekly_goal" }.sortedBy { it.title }
    }

    var schedulingTask by remember { mutableStateOf<Task?>(null) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (goals.isNotEmpty()) {
            item { SectionLabelBacklog("Weekly goals · W$weekNumber") }
            items(goals, key = { "g${it.id}" }) { g ->
                GoalRow(
                    goal = g,
                    today = today,
                    onTick = { scope.launch { TaskRepo.toggle(g, today) } },
                    onDelete = { pendingDelete = g },
                )
            }
        }
        item { SectionLabelBacklog("Backlog · ${singles.size}") }
        if (singles.isEmpty()) {
            item {
                Text(
                    "Nothing pending. Create undated singles to populate this list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        } else {
            items(singles, key = { "s${it.id}" }) { t ->
                BacklogRow(
                    task = t,
                    onComplete = { scope.launch { TaskRepo.toggle(t, today) } },
                    onSchedule = { schedulingTask = t },
                    onDelete = { pendingDelete = t },
                )
            }
        }
    }

    val taskToSchedule = schedulingTask
    if (taskToSchedule != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { schedulingTask = null },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) {
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        scope.launch {
                            TaskRepo.update(
                                taskToSchedule.id,
                                TaskPayload(
                                    title = taskToSchedule.title,
                                    task_type = taskToSchedule.task_type,
                                    fixed_date = d.toString(),
                                    weekdays = taskToSchedule.weekdays,
                                    start_time = taskToSchedule.start_time,
                                    end_time = taskToSchedule.end_time,
                                    is_todo = taskToSchedule.is_todo,
                                    target_per_week = taskToSchedule.target_per_week,
                                ),
                            )
                        }
                    }
                    schedulingTask = null
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { schedulingTask = null }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete task?") },
            text = { Text("Delete \"${toDelete.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { TaskRepo.remove(toDelete.id) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionLabelBacklog(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun BacklogRow(
    task: Task,
    onComplete: () -> Unit,
    onSchedule: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Empty checkbox-style button to complete the task with today's date.
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onComplete),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            task.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onSchedule) {
            Icon(Icons.Filled.DateRange, contentDescription = "Schedule")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete")
        }
    }
}

@Composable
private fun GoalRow(
    goal: Task,
    today: LocalDate,
    onTick: () -> Unit,
    onDelete: () -> Unit,
) {
    val done = goal.completionsThisWeek(today)
    val target = goal.target_per_week ?: 0
    val todayDone = goal.completed_dates.contains(today.toString())
    val hit = target > 0 && done >= target
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (hit) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (todayDone) MaterialTheme.colorScheme.primary
                    else Color.Transparent
                )
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onTick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (todayDone) "✓" else "+",
                color = if (todayDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(goal.title, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val total = maxOf(target, done)
                repeat(total) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(end = 2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    i >= done -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    i >= target -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            ),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "$done/$target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete")
        }
    }
}
