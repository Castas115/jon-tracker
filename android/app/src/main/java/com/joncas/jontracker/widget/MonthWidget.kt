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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("M", "T", "W", "T", "F", "S", "S")

private data class CellEntry(val time: String?, val title: String)

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

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    "${ym.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${ym.year}",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C7546)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "↻",
                    modifier = GlanceModifier.clickable(actionRunCallback<MonthRefreshAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 13.sp),
                )
            }
            Spacer(GlanceModifier.height(2.dp))
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
            Spacer(GlanceModifier.height(1.dp))
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
            .sortedWith(compareBy({ it.start_time ?: "99:99" }, { it.title }))
            .map { CellEntry(it.start_time, it.displayTitle(d)) }
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
            isToday -> Color(0xFFFFC080)
            !inMonth -> Color(0xFF444444)
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFB0A090)
            else -> Color(0xFFE5E5E5)
        }
        // Keep view count low: a Column with at most 3 Text children. Glance
        // turns each composable into a RemoteView and the host has a hard cap.
        Column(modifier = modifier.padding(1.dp)) {
            Text(
                date.dayOfMonth.toString(),
                style = TextStyle(
                    color = ColorProvider(numColor),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (inMonth) {
                val first = entries.getOrNull(0)
                if (first != null) {
                    Text(
                        first.title,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFE0CFB4)),
                            fontSize = 8.sp,
                        ),
                        maxLines = 1,
                    )
                }
                if (entries.size >= 2) {
                    val label = if (entries.size == 2) entries[1].title else "+${entries.size - 1}"
                    Text(
                        label,
                        style = TextStyle(
                            color = ColorProvider(
                                if (entries.size == 2) Color(0xFFE0CFB4) else Color(0xFF888888),
                            ),
                            fontSize = 8.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
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
