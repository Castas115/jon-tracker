package com.joncas.jontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TrackerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val todayK = today.toString()
        val tasks: List<Task> = runCatching { withContext(Dispatchers.IO) { TrackerApi.listTasks() } }
            .getOrDefault(emptyList())

        val checklist = tasks
            .filter { matchesToday(it, today) && it.is_todo && it.task_type != "weekly_goal" }
            .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))

        provideContent {
            GlanceTheme {
                WidgetContent(checklist, todayK)
            }
        }
    }

    @Composable
    private fun WidgetContent(checklist: List<Task>, todayK: String) {
        val done = checklist.count { todayK in it.completed_dates }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C7546)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "$done/${checklist.size}",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF888888)),
                        fontSize = 12.sp
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "↻",
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 14.sp)
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (checklist.isEmpty()) {
                Text(
                    "Nothing to tick off.",
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 13.sp)
                )
            } else {
                checklist.take(5).forEach { t ->
                    val isDone = todayK in t.completed_dates
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable(
                                actionRunCallback<ToggleTaskAction>(
                                    parameters = actionParametersOf(
                                        ToggleTaskAction.taskIdKey to t.id,
                                        ToggleTaskAction.dateKey to todayK
                                    )
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(14.dp)
                                .height(14.dp)
                                .cornerRadius(3.dp)
                                .background(if (isDone) Color(0xFF9C7546) else Color(0xFF222222))
                        ) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            text = (if (t.start_time != null) "${t.start_time}  " else "") + t.title,
                            style = TextStyle(
                                color = ColorProvider(if (isDone) Color(0xFF555555) else Color(0xFFE5E5E5)),
                                fontSize = 13.sp,
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                        )
                    }
                }
                if (checklist.size > 5) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        "+${checklist.size - 5} more",
                        style = TextStyle(color = ColorProvider(Color(0xFF666666)), fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

private fun matchesToday(t: Task, today: LocalDate): Boolean {
    val k = today.toString()
    return when (t.task_type) {
        "recurring" -> (t.weekdays ?: emptyList()).contains(weekdayMonFirst(today))
        "single" -> t.fixed_date == k
        "birthday" -> t.fixed_date?.let {
            val bd = LocalDate.parse(it)
            bd.monthValue == today.monthValue && bd.dayOfMonth == today.dayOfMonth
        } ?: false
        else -> false
    }
}

private fun weekdayMonFirst(d: LocalDate): Int = when (d.dayOfWeek) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY -> 4
    DayOfWeek.SATURDAY -> 5
    DayOfWeek.SUNDAY -> 6
}
