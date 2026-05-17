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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.joncas.jontracker.api.TrackerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = runCatching { withContext(Dispatchers.IO) { TrackerApi.listTasks() } }
            .getOrDefault(emptyList())
        val stats = computeStats(tasks)
        provideContent { GlanceTheme { Content(stats) } }
    }

    @Composable
    private fun Content(stats: WidgetStats) {
        val pct = if (stats.monthTodos.total > 0)
            (stats.monthTodos.done * 100 / stats.monthTodos.total)
        else 0
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    stats.monthLabel,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C7546)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "$pct%",
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "↻",
                    modifier = GlanceModifier.clickable(actionRunCallback<MonthRefreshAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 14.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                "${stats.monthTodos.done}/${stats.monthTodos.total} todos done",
                style = TextStyle(color = ColorProvider(Color(0xFFE5E5E5)), fontSize = 13.sp),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                "Week ${stats.weekNumber} · ${stats.weekTodos.done}/${stats.weekTodos.total}",
                style = TextStyle(color = ColorProvider(Color(0xFFAAAAAA)), fontSize = 12.sp),
            )
            val topGoal = stats.weekGoals.firstOrNull()
            if (topGoal != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "${topGoal.title} ${topGoal.done}/${topGoal.target}",
                    style = TextStyle(color = ColorProvider(Color(0xFFAAAAAA)), fontSize = 12.sp),
                    maxLines = 1,
                )
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
