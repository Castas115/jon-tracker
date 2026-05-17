package com.joncas.jontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.data.goalStatus
import com.joncas.jontracker.data.segmentLabel
import com.joncas.jontracker.data.streakInfo
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksSheet(onDismiss: () -> Unit) {
    val tasks by TaskRepo.tasks.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()

    val goals = tasks
        .filter { it.task_type == "weekly_goal" }
        .map { t ->
            val streak = t.streakInfo(today)
            val status = t.goalStatus(today)
            GoalRow(t, streak.current, streak.best, status.done, status.target, status.hit)
        }
        .sortedWith(compareByDescending<GoalRow> { it.current }.thenByDescending { it.best }.thenBy { it.task.title })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Goal streaks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (goals.isEmpty()) {
                Text(
                    "No weekly goals yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items = goals, key = { it.task.id }) { row ->
                        StreakRow(row)
                    }
                }
            }
            Box(modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private data class GoalRow(
    val task: Task,
    val current: Int,
    val best: Int,
    val weekDone: Int,
    val weekTarget: Int,
    val weekHit: Boolean,
)

@Composable
private fun StreakRow(row: GoalRow) {
    val segs = row.task.target_segments ?: emptyList()
    val sub = if (segs.isEmpty()) "${row.task.target_per_week ?: 0}/wk"
    else segs.joinToString(" + ") { "${it.target}x ${segmentLabel(it)}" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${flame(row.current)} ${row.current}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "best ${row.best}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${row.weekDone}/${row.weekTarget} this week",
                style = MaterialTheme.typography.labelSmall,
                color = if (row.weekHit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (row.weekHit) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

private fun flame(n: Int): String = when {
    n == 0 -> "·"
    n >= 12 -> "🔥🔥🔥"
    n >= 6 -> "🔥🔥"
    else -> "🔥"
}
