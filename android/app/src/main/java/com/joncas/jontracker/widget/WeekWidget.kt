package com.joncas.jontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
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

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = runCatching { withContext(Dispatchers.IO) { TrackerApi.listTasks() } }
            .getOrDefault(emptyList())
        val stats = computeStats(tasks)
        provideContent { GlanceTheme { Content(stats) } }
    }

    @Composable
    private fun Content(stats: WidgetStats) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .cornerRadius(12.dp)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    "Week ${stats.weekNumber}",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C7546)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "${stats.weekTodos.done}/${stats.weekTodos.total} done",
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "↻",
                    modifier = GlanceModifier.clickable(actionRunCallback<WeekRefreshAction>()),
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 14.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (stats.weekGoals.isEmpty()) {
                Text(
                    "No weekly goals.",
                    style = TextStyle(color = ColorProvider(Color(0xFF888888)), fontSize = 12.sp),
                )
            } else {
                stats.weekGoals.take(5).forEach { g ->
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            g.title,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = ColorProvider(
                                    if (g.hit) Color(0xFF9C7546) else Color(0xFFE5E5E5),
                                ),
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            "${g.done}/${g.target}",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFAAAAAA)),
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }
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
