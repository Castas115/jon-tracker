package com.joncas.jontracker.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

const val CHANNEL_ID = "task_reminders"

fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Task reminders",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Reminders for tasks scheduled with a time."
    }
    manager.createNotificationChannel(channel)
}
