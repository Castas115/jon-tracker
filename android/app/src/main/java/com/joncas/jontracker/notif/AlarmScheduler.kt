package com.joncas.jontracker.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.data.appliesOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    /**
     * Cancel and re-arm every alarm derived from [tasks]. Schedules the next
     * fire-time per task within the next 48 hours so the alarm queue stays
     * small. Called after every successful task refresh.
     */
    fun reschedule(context: Context, tasks: List<Task>) {
        ensureNotificationChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        val horizon = now.plusHours(48)

        for (task in tasks) {
            val pi = pendingIntent(context, task, create = true)
            am.cancel(pi)
            if (!task.notify_enabled) continue
            val fireAt = nextFireTime(task, now, horizon) ?: continue
            val triggerMillis = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = buildIntent(context, task)
            val firePI = PendingIntent.getBroadcast(
                context,
                task.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // No exact-alarm permission yet → fall back to a windowed alarm.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, firePI)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, firePI)
            }
        }
    }

    private fun buildIntent(context: Context, task: Task): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_TITLE, task.title)
            putExtra(
                AlarmReceiver.EXTRA_SUBTITLE,
                task.start_time?.let { s ->
                    task.end_time?.let { "$s – $it" } ?: s
                },
            )
        }

    private fun pendingIntent(context: Context, task: Task, create: Boolean): PendingIntent {
        val flags = (PendingIntent.FLAG_IMMUTABLE) or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, task.id, buildIntent(context, task), flags)
    }

    private fun nextFireTime(
        task: Task,
        now: LocalDateTime,
        horizon: LocalDateTime,
    ): LocalDateTime? {
        val baseTime = task.start_time?.let { parseHm(it) }
        val notifyAt = task.notify_at?.let { parseHm(it) }
        val time = baseTime ?: notifyAt ?: return null
        val offsetMinutes = if (baseTime != null) task.notify_minutes_before else 0

        // Walk forward day by day until we find one the task applies on and the
        // resulting fire time is still in the [now, horizon] window.
        var day: LocalDate = now.toLocalDate()
        while (!day.isAfter(horizon.toLocalDate())) {
            if (task.appliesOn(day)) {
                val fire = LocalDateTime.of(day, time).minusMinutes(offsetMinutes.toLong())
                if (fire.isAfter(now) && !fire.isAfter(horizon)) return fire
            }
            day = day.plusDays(1)
        }
        return null
    }

    private fun parseHm(s: String): LocalTime? =
        runCatching { LocalTime.parse(s) }.getOrNull()
}
