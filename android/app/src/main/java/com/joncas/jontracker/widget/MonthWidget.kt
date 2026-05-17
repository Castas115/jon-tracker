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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("M", "T", "W", "T", "F", "S", "S")

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
        // Monday-first leading offset: dow value MONDAY=1 → 0, ..., SUNDAY=7 → 6.
        val lead = (first.dayOfWeek.value + 6) % 7
        val gridStart = first.minusDays(lead.toLong())

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    "${ym.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${ym.year}",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C7546)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "↻",
                    modifier = GlanceModifier.clickable(actionRunCallback<MonthRefreshAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 14.sp),
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            // Day-of-week header
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { lbl ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(
                            lbl,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF666666)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            // 6 weeks × 7 days grid
            for (row in 0 until 6) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val d = gridStart.plusDays((row * 7 + col).toLong())
                        DayCell(
                            date = d,
                            inMonth = d.month == ym.month,
                            isToday = d == today,
                            hasTask = tasks.any { it.appliesOn(d) },
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DayCell(
        date: LocalDate,
        inMonth: Boolean,
        isToday: Boolean,
        hasTask: Boolean,
        modifier: GlanceModifier,
    ) {
        val fg = when {
            isToday -> Color(0xFF000000)
            !inMonth -> Color(0xFF444444)
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFB0A090)
            else -> Color(0xFFE5E5E5)
        }
        val bg = if (isToday) Color(0xFF9C7546) else Color(0x00000000)
        Box(
            modifier = modifier
                .height(22.dp)
                .padding(1.dp)
                .cornerRadius(4.dp)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = TextStyle(
                        color = ColorProvider(fg),
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
                if (hasTask && inMonth && !isToday) {
                    Box(
                        modifier = GlanceModifier
                            .height(3.dp)
                            .padding(top = 1.dp),
                    ) {
                        Text(
                            "·",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF9C7546)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
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
