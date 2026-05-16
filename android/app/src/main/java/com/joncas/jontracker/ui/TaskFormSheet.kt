package com.joncas.jontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TaskPayload
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val TASK_TYPES = listOf(
    "single" to "Single",
    "recurring" to "Recurring",
    "birthday" to "Birthday",
    "weekly_goal" to "Goal",
)

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
    var targetPerWeek by remember { mutableStateOf(existing?.target_per_week ?: 3) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTime by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }

    val canSubmit = title.isNotBlank() && when (taskType) {
        "recurring" -> weekdays.isNotEmpty()
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

            if (taskType == "weekly_goal") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target per week", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (targetPerWeek > 1) targetPerWeek-- }) { Text("−") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$targetPerWeek", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { if (targetPerWeek < 14) targetPerWeek++ }) { Text("+") }
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
                        val payload = TaskPayload(
                            title = title.trim(),
                            task_type = taskType,
                            weekdays = if (taskType == "recurring") weekdays.sorted() else null,
                            fixed_date = if (taskType == "single" || taskType == "birthday") fixedDate.format(ISO) else null,
                            start_time = startTime,
                            end_time = endTime,
                            is_todo = when (taskType) {
                                "birthday" -> false
                                "weekly_goal" -> true
                                else -> isTodo
                            },
                            target_per_week = if (taskType == "weekly_goal") targetPerWeek else null,
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
