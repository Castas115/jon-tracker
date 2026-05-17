package com.joncas.jontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TrackerApi
import com.joncas.jontracker.data.appliesOn
import com.joncas.jontracker.data.displayTitle
import com.joncas.jontracker.data.isCompletedOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

private data class CellEntry(
    val time: String?,
    val title: String,
    val kind: ChipKind,
    val done: Boolean,
)

private enum class ChipKind { TASK, BIRTHDAY, GOAL }

class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = runCatching { withContext(Dispatchers.IO) { TrackerApi.listTasks() } }
            .getOrDefault(emptyList())
        val today = LocalDate.now()
        provideContent { GlanceTheme { Content(today, tasks) } }
    }

    @Composable
    private fun Content(today: LocalDate, tasks: List<Task>) {
        val ym = YearMonth.from(today)
        val first = ym.atDay(1)
        val lead = (first.dayOfWeek.value + 6) % 7
        val gridStart = first.minusDays(lead.toLong())
        val weekNum = today.get(WeekFields.ISO.weekOfWeekBasedYear())
        val title = "${ym.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${ym.year} · W$weekNum"

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(6.dp),
        ) {
            // Header: centered title + refresh on the right.
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        title,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFE5E5E5)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        "↻",
                        modifier = GlanceModifier.clickable(actionRunCallback<MonthRefreshAction>()),
                        style = TextStyle(color = ColorProvider(Color(0xFF666666)), fontSize = 12.sp),
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { lbl ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(
                            lbl,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF666666)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            for (row in 0 until 6) {
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (col in 0 until 7) {
                        val d = gridStart.plusDays((row * 7 + col).toLong())
                        DayCell(
                            date = d,
                            inMonth = d.month == ym.month,
                            isToday = d == today,
                            entries = entriesFor(tasks, d),
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }

    private fun entriesFor(tasks: List<Task>, d: LocalDate): List<CellEntry> {
        return tasks
            .filter { it.appliesOn(d) }
            .filter { !(it.task_type == "weekly_goal" && !it.show_in_upcoming) }
            .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))
            .map {
                val kind = when {
                    it.task_type == "birthday" -> ChipKind.BIRTHDAY
                    it.task_type == "weekly_goal" -> ChipKind.GOAL
                    else -> ChipKind.TASK
                }
                val done = it.task_type != "weekly_goal" && it.isCompletedOn(d)
                CellEntry(it.start_time, it.displayTitle(d), kind, done)
            }
    }

    @Composable
    private fun DayCell(
        date: LocalDate,
        inMonth: Boolean,
        isToday: Boolean,
        entries: List<CellEntry>,
        modifier: GlanceModifier,
    ) {
        val numColor = when {
            isToday -> Color(0xFFFFFFFF)
            !inMonth -> Color(0xFF444444)
            else -> Color(0xFFE5E5E5)
        }
        val cellBg = if (isToday) Color(0x334CAF50) else Color(0x00000000)
        Column(
            modifier = modifier
                .padding(1.dp)
                .cornerRadius(4.dp)
                .background(cellBg)
                .padding(horizontal = 2.dp, vertical = 2.dp),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = TextStyle(
                    color = ColorProvider(numColor),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (inMonth) {
                val take = entries.take(2)
                take.forEach { e -> ChipRow(e) }
                if (entries.size > 2) {
                    Text(
                        "+${entries.size - 2}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF888888)),
                            fontSize = 8.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun ChipRow(e: CellEntry) {
        val bg = when (e.kind) {
            ChipKind.BIRTHDAY -> Color(0x55C97A8A)
            ChipKind.GOAL -> Color(0x559C7546)
            ChipKind.TASK -> Color(0x559C7546)
        }
        val label = buildString {
            if (e.kind == ChipKind.BIRTHDAY) append("🎂 ")
            if (e.time != null) append("${e.time} ")
            append(e.title)
        }
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .cornerRadius(2.dp)
                .background(bg)
                .padding(horizontal = 2.dp, vertical = 1.dp),
        ) {
            Text(
                label,
                style = TextStyle(
                    color = ColorProvider(
                        if (e.done) Color(0xFF888888) else Color(0xFFE5E5E5),
                    ),
                    fontSize = 8.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

class MonthRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MonthWidget().updateAll(context)
    }
}
