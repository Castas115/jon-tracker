package com.joncas.jontracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.data.Prefs
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.notif.AlarmScheduler
import com.joncas.jontracker.notif.ensureNotificationChannel
import com.joncas.jontracker.ui.AppScaffold
import com.joncas.jontracker.ui.LocalEditTask
import com.joncas.jontracker.ui.TaskFormSheet
import com.joncas.jontracker.ui.theme.JonTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val theme by Prefs.theme(context).collectAsState(initial = "dark")
            val isDark = theme != "light"
            val tasks by TaskRepo.tasks.collectAsState()

            val notifPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* User decision; alarms are scheduled regardless — notif is best-effort. */ }

            LaunchedEffect(Unit) {
                ensureNotificationChannel(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Re-arm alarms whenever the task list changes (initial load + after
            // any create/update/delete via TaskRepo).
            LaunchedEffect(tasks) {
                AlarmScheduler.reschedule(context, tasks)
            }

            var creating by remember { mutableStateOf(false) }
            var editing by remember { mutableStateOf<Task?>(null) }

            JonTrackerTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalEditTask provides { task -> editing = task }) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppScaffold(
                            isDark = isDark,
                            onToggleTheme = {
                                scope.launch {
                                    Prefs.setTheme(context, if (isDark) "light" else "dark")
                                }
                            },
                            onCreate = { creating = true },
                        )
                        if (creating) {
                            TaskFormSheet(
                                onDismiss = { creating = false },
                                onSubmit = { payload ->
                                    scope.launch {
                                        TaskRepo.create(payload)
                                        creating = false
                                    }
                                },
                            )
                        }
                        val editTarget = editing
                        if (editTarget != null) {
                            TaskFormSheet(
                                existing = editTarget,
                                onDismiss = { editing = null },
                                onSubmit = { payload ->
                                    scope.launch {
                                        TaskRepo.update(editTarget.id, payload)
                                        editing = null
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        TaskRepo.remove(editTarget.id)
                                        editing = null
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
