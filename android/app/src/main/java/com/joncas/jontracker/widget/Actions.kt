package com.joncas.jontracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.joncas.jontracker.api.TrackerApi
import java.time.LocalDate

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TodayWidget().updateAll(context)
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[taskIdKey] ?: return
        val dateStr = parameters[dateKey] ?: LocalDate.now().toString()
        runCatching { TrackerApi.toggle(id, LocalDate.parse(dateStr)) }
        TodayWidget().updateAll(context)
    }

    companion object {
        val taskIdKey = ActionParameters.Key<Int>("task_id")
        val dateKey = ActionParameters.Key<String>("date")
    }
}
