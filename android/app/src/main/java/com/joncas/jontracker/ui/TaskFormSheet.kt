package com.joncas.jontracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joncas.jontracker.api.TargetSegment
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TaskPayload
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
private val WEEKDAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
private val TASK_TYPES = listOf(
    "single" to "Single",
    "recurring" to "Recurring",
    "birthday" to "Birthday",
    "weekly_goal" to "Goal",
)

private data class SegmentDraft(val weekdays: MutableSet<Int>, var target: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormSheet(
    existing: Task? = null,
    onDismiss: () -> Unit,
    onSubmit: (TaskPayload) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var taskType by remember { mutableStateOf(existing?.task_type ?: "single") }
    var weekdays by remember { mutableStateOf(existing?.weekdays?.toSet() ?: emptySet()) }
    var fixedDate by remember {
        mutableStateOf(
            existing?.fixed_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        )
    }
    var startTime by remember { mutableStateOf(existing?.start_time) }
    var endTime by remember { mutableStateOf(existing?.end_time) }
    var isTodo by remember { mutableStateOf(existing?.is_todo ?: true) }
    var showInUpcoming by remember { mutableStateOf(existing?.show_in_upcoming ?: true) }
    var notifyEnabled by remember { mutableStateOf(existing?.notify_enabled ?: false) }
    var notifyMinutesBefore by remember { mutableStateOf(existing?.notify_minutes_before ?: 0) }
    var notifyAt by remember { mutableStateOf(existing?.notify_at) }
    var showNotifyAtPicker by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(existing?.start_date) }
    var endDate by remember { mutableStateOf(existing?.end_date) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Weekly-goal segments. Initial state mirrors existing task if it has
    // segments; falls back to a single segment covering all 7 days with the
    // legacy target_per_week (or 3) as target.
    val initialSegments = remember(existing) {
        when {
            existing?.target_segments?.isNotEmpty() == true ->
                existing.target_segments!!.map { SegmentDraft(it.weekdays.toMutableSet(), it.target) }
            existing?.target_per_week != null ->
                listOf(SegmentDraft((0..6).toMutableSet(), existing.target_per_week))
            else -> listOf(SegmentDraft((0..6).toMutableSet(), 3))
        }
    }
    val segments: SnapshotStateList<SegmentDraft> = remember { initialSegments.toMutableStateList() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTime by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }

    val segError: String? = remember(segments.toList()) {
        if (taskType != "weekly_goal") null
        else {
            val seen = mutableSetOf<Int>()
            var err: String? = null
            for (s in segments) {
                if (s.weekdays.isEmpty()) { err = "Each segment needs at least one weekday"; break }
                if (s.target < 1) { err = "Targets must be ≥1"; break }
                for (w in s.weekdays) {
                    if (!seen.add(w)) { err = "A weekday can only belong to one segment"; break }
                }
                if (err != null) break
            }
            err
        }
    }

    val canSubmit = title.isNotBlank() && when (taskType) {
        "recurring" -> weekdays.isNotEmpty()
        "weekly_goal" -> segments.isNotEmpty() && segError == null
        else -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (existing == null) "New task" else "Edit task",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Type", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TASK_TYPES.forEachIndexed { idx, (key, label) ->
                    SegmentedButton(
                        selected = taskType == key,
                        onClick = {
                            taskType = key
                            when (key) {
                                "birthday" -> isTodo = false
                                "weekly_goal" -> isTodo = true
                                else -> {}
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(idx, TASK_TYPES.size),
                    ) { Text(label) }
                }
            }

            if (taskType == "recurring") {
                Text("Weekdays", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WEEKDAY_LABELS.forEachIndexed { i, lbl ->
                        FilterChip(
                            selected = weekdays.contains(i),
                            onClick = {
                                weekdays = if (weekdays.contains(i)) weekdays - i else weekdays + i
                            },
                            label = { Text(lbl) },
                        )
                    }
                }
            }

            if (taskType == "weekly_goal") {
                Text("Segments", style = MaterialTheme.typography.labelMedium)
                segments.forEachIndexed { idx, seg ->
                    SegmentEditor(
                        seg = seg,
                        canRemove = segments.size > 1,
                        onToggleDay = { d ->
                            if (seg.weekdays.contains(d)) seg.weekdays.remove(d) else seg.weekdays.add(d)
                            // Force recomposition by reassigning the list slot.
                            segments[idx] = SegmentDraft(seg.weekdays.toMutableSet(), seg.target)
                        },
                        onTargetChange = { t ->
                            segments[idx] = SegmentDraft(seg.weekdays.toMutableSet(), t.coerceAtLeast(1))
                        },
                        onRemove = { segments.removeAt(idx) },
                    )
                }
                OutlinedButton(onClick = {
                    val used = segments.flatMap { it.weekdays }.toSet()
                    val free = (0..6).filterNot { used.contains(it) }.toMutableSet()
                    segments.add(SegmentDraft(if (free.isNotEmpty()) free else mutableSetOf(), 1))
                }) { Text("+ Add segment") }
                if (segError != null) {
                    Text(
                        segError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (taskType == "single" || taskType == "birthday") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date: ${fixedDate.format(ISO)}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                }
            }

            if (taskType != "weekly_goal") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start: ${startTime ?: "—"}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showStartTime = true }) { Text("Pick") }
                    if (startTime != null) {
                        TextButton(onClick = { startTime = null }) { Text("Clear") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("End: ${endTime ?: "—"}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showEndTime = true }) { Text("Pick") }
                    if (endTime != null) {
                        TextButton(onClick = { endTime = null }) { Text("Clear") }
                    }
                }
            }

            if (taskType != "birthday" && taskType != "weekly_goal") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Track completion (todo)", modifier = Modifier.weight(1f))
                    Switch(checked = isTodo, onCheckedChange = { isTodo = it })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show in upcoming", modifier = Modifier.weight(1f))
                Switch(checked = showInUpcoming, onCheckedChange = { showInUpcoming = it })
            }

            if (taskType == "recurring" || taskType == "weekly_goal") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active from: ${startDate ?: "—"}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showStartDatePicker = true }) { Text("Pick") }
                    if (startDate != null) {
                        TextButton(onClick = { startDate = null }) { Text("Clear") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Until: ${endDate ?: "—"}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showEndDatePicker = true }) { Text("Pick") }
                    if (endDate != null) {
                        TextButton(onClick = { endDate = null }) { Text("Clear") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { endDate = LocalDate.now().format(ISO) }) {
                        Text("End today")
                    }
                }
                Text(
                    "Set 'Until' instead of deleting — history and streaks stay intact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify", modifier = Modifier.weight(1f))
                Switch(checked = notifyEnabled, onCheckedChange = { notifyEnabled = it })
            }
            if (notifyEnabled) {
                if (startTime != null) {
                    Text("Notify time", style = MaterialTheme.typography.labelMedium)
                    val choices = listOf(0, 5, 10, 15, 30, 60)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        choices.forEach { m ->
                            FilterChip(
                                selected = notifyMinutesBefore == m,
                                onClick = { notifyMinutesBefore = m },
                                label = { Text(if (m == 0) "On time" else "-${m}m") },
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notify at: ${notifyAt ?: "—"}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { showNotifyAtPicker = true }) { Text("Pick") }
                        if (notifyAt != null) {
                            TextButton(onClick = { notifyAt = null }) { Text("Clear") }
                        }
                    }
                    if (notifyAt == null) {
                        Text(
                            "No time → no alarm will fire.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(0.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                    Box(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = canSubmit,
                    onClick = {
                        val isWeekly = taskType == "weekly_goal"
                        val finalSegments = if (isWeekly) {
                            segments.map { TargetSegment(it.weekdays.sorted(), it.target) }
                        } else null
                        val flatSum = finalSegments?.sumOf { it.target }
                        val payload = TaskPayload(
                            title = title.trim(),
                            task_type = taskType,
                            weekdays = if (taskType == "recurring") weekdays.sorted() else null,
                            fixed_date = if (taskType == "single" || taskType == "birthday") fixedDate.format(ISO) else null,
                            start_time = if (isWeekly) null else startTime,
                            end_time = if (isWeekly) null else endTime,
                            is_todo = when (taskType) {
                                "birthday" -> false
                                "weekly_goal" -> true
                                else -> isTodo
                            },
                            target_per_week = flatSum,
                            target_segments = finalSegments,
                            show_in_upcoming = showInUpcoming,
                            notify_enabled = notifyEnabled,
                            notify_minutes_before = notifyMinutesBefore,
                            notify_at = notifyAt,
                            start_date = if (taskType == "recurring" || taskType == "weekly_goal") startDate else null,
                            end_date = if (taskType == "recurring" || taskType == "weekly_goal") endDate else null,
                        )
                        onSubmit(payload)
                    },
                ) { Text(if (existing == null) "Create" else "Save") }
            }
            Spacer(modifier = Modifier.width(0.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fixedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        fixedDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = state) }
    }

    if (showStartDatePicker) {
        val initial = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        startDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(ISO)
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showEndDatePicker) {
        val initial = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        endDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(ISO)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showStartTime) {
        val (hh, mm) = (startTime ?: "08:00").split(":").map { it.toInt() }
        TimePickerDialog(
            initialHour = hh,
            initialMinute = mm,
            onDismiss = { showStartTime = false },
            onConfirm = { h, m ->
                startTime = "%02d:%02d".format(h, m)
                showStartTime = false
            },
        )
    }

    if (showNotifyAtPicker) {
        val (hh, mm) = (notifyAt ?: "21:00").split(":").map { it.toInt() }
        TimePickerDialog(
            initialHour = hh,
            initialMinute = mm,
            onDismiss = { showNotifyAtPicker = false },
            onConfirm = { h, m ->
                notifyAt = "%02d:%02d".format(h, m)
                showNotifyAtPicker = false
            },
        )
    }

    if (showEndTime) {
        val (hh, mm) = (endTime ?: startTime?.let {
            val parts = it.split(":")
            "%02d:%02d".format((parts[0].toInt() + 1).coerceAtMost(23), parts[1].toInt())
        } ?: "09:00").split(":").map { it.toInt() }
        TimePickerDialog(
            initialHour = hh,
            initialMinute = mm,
            onDismiss = { showEndTime = false },
            onConfirm = { h, m ->
                endTime = "%02d:%02d".format(h, m)
                showEndTime = false
            },
        )
    }
}

@Composable
private fun SegmentEditor(
    seg: SegmentDraft,
    canRemove: Boolean,
    onToggleDay: (Int) -> Unit,
    onTargetChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            WEEKDAY_LABELS.forEachIndexed { i, lbl ->
                FilterChip(
                    selected = seg.weekdays.contains(i),
                    onClick = { onToggleDay(i) },
                    label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Target", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { onTargetChange(seg.target - 1) }) { Text("−") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("${seg.target}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { onTargetChange(seg.target + 1) }) { Text("+") }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove segment")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}
