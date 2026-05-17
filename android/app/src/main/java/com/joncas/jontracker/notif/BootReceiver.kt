package com.joncas.jontracker.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.joncas.jontracker.api.TrackerApi
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        // Quick attempt to reload + reschedule. Network may not be available
        // yet at boot — wrapped in runCatching so the receiver never crashes.
        Thread {
            runCatching {
                runBlocking {
                    val tasks = TrackerApi.listTasks()
                    AlarmScheduler.reschedule(ctx, tasks)
                }
            }
        }.start()
    }
}
