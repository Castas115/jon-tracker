package com.joncas.jontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import android.content.Intent
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.joncas.jontracker.MainActivity
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TrackerApi
import com.joncas.jontracker.data.Prefs
import com.joncas.jontracker.data.appliesOn
import com.joncas.jontracker.data.displayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val DAY_LABEL_FMT = DateTimeFormatter.ofPattern("EEE")

private data class WeekItem(
    val date: LocalDate,
    val task: Task,
    val done: Boolean,
)

class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = runCatching { withContext(Dispatchers.IO) { TrackerApi.listTasks() } }
            .getOrDefault(emptyList())
        val today = LocalDate.now()
        val offset = Prefs.weekOffset(context)
        val monday = today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
        val items = mutableListOf<WeekItem>()
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val k = d.toString()
            tasks
                .filter { it.appliesOn(d) }
                .filter { !(it.task_type == "weekly_goal" && !it.show_in_upcoming) }
                .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))
                .forEach {
                    val done = if (it.task_type == "weekly_goal") false else k in it.completed_dates
                    items += WeekItem(d, it, done)
                }
        }
        val weekNum = monday.get(WeekFields.ISO.weekOfWeekBasedYear())
        provideContent { GlanceTheme { Content(weekNum, today, items) } }
    }

    @Composable
    private fun Content(weekNumber: Int, today: LocalDate, items: List<WeekItem>) {
        // Bring incomplete-and-upcoming tasks to the top, but still show the
        // full week below them so the widget reflects the whole schedule.
        val sorted = items.sortedWith(
            compareBy(
                { it.done },
                { it.date },
                { it.task.start_time ?: "99:99" },
            ),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    "‹",
                    modifier = GlanceModifier.clickable(actionRunCallback<WeekPrevAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 16.sp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "Week $weekNumber",
                    modifier = GlanceModifier.clickable(actionRunCallback<WeekTodayAction>()),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C7546)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "${items.count { it.done }}/${items.size}",
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "›",
                    modifier = GlanceModifier.clickable(actionRunCallback<WeekNextAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 16.sp),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (sorted.isEmpty()) {
                Text(
                    "Nothing scheduled this week.",
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 12.sp),
                )
            } else {
                sorted.take(8).forEach { item ->
                    WeekRow(item, isToday = item.date == today)
                }
                if (sorted.size > 8) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        "+${sorted.size - 8} more",
                        style = TextStyle(color = ColorProvider(Color(0xFF666666)), fontSize = 11.sp),
                    )
                }
            }
        }
    }

    @Composable
    private fun WeekRow(item: WeekItem, isToday: Boolean) {
        val context = androidx.glance.LocalContext.current
        val dayLabel = item.date.format(DAY_LABEL_FMT)
        val time = item.task.start_time
        val title = item.task.displayTitle(item.date)
        val openAtDay = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FOCUS_DATE, item.date.toString())
                putExtra(EXTRA_VIEW, "day")
            },
        )
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clickable(openAtDay),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .width(28.dp)
                    .cornerRadius(4.dp)
                    .background(if (isToday) Color(0xFF9C7546) else Color(0x00000000))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dayLabel.take(3),
                    style = TextStyle(
                        color = ColorProvider(
                            if (isToday) Color(0xFF000000) else Color(0xFFAAAAAA),
                        ),
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            if (time != null) {
                Text(
                    time,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF888888)),
                        fontSize = 11.sp,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(
                title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(
                        if (item.done) Color(0xFF555555) else Color(0xFFE5E5E5),
                    ),
                    fontSize = 12.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

class WeekRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WeekWidget().updateAll(context)
    }
}

class WeekPrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Prefs.setWeekOffset(context, Prefs.weekOffset(context) - 1)
        WeekWidget().updateAll(context)
    }
}

class WeekNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Prefs.setWeekOffset(context, Prefs.weekOffset(context) + 1)
        WeekWidget().updateAll(context)
    }
}

class WeekTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Prefs.setWeekOffset(context, 0)
        WeekWidget().updateAll(context)
    }
}
