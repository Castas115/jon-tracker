package com.joncas.jontracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joncas.jontracker.audio.AudioRecorder
import com.joncas.jontracker.data.IdeaRepo
import com.joncas.jontracker.data.Prefs
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.ui.views.BacklogScreen
import com.joncas.jontracker.ui.views.DayScreen
import com.joncas.jontracker.ui.views.FeaturesScreen
import com.joncas.jontracker.ui.views.InboxScreen
import com.joncas.jontracker.ui.views.MonthScreen
import com.joncas.jontracker.ui.views.StreaksScreen
import com.joncas.jontracker.ui.views.WeekScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("day", "Day", Icons.Filled.Today),
    Tab("week", "Week", Icons.Filled.DateRange),
    Tab("month", "Month", Icons.Filled.CalendarMonth),
    Tab("backlog", "Backlog", Icons.Filled.List),
    Tab("streaks", "Streaks", Icons.Filled.Whatshot),
    Tab("inbox", "Inbox", Icons.Filled.Inbox),
    Tab("features", "Features", Icons.Filled.AutoAwesome),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onCreate: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val error by TaskRepo.error.collectAsState()
    var captureOpen by remember { mutableStateOf(false) }
    var initialTranscript by remember { mutableStateOf("") }
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMic = granted }

    LaunchedEffect(Unit) {
        TaskRepo.refresh()
        TaskRepo.refreshCalendar()
        IdeaRepo.refresh()
        if (!hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TABS.firstOrNull { it.route == currentRoute }?.label ?: "Tracker") },
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = "New task")
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            HoldToRecordFab(
                enabled = hasMic && !transcribing,
                recording = recording,
                transcribing = transcribing,
                onTap = {
                    // Short tap opens the typed-entry sheet.
                    initialTranscript = ""
                    captureOpen = true
                },
                onPressStart = {
                    if (!hasMic) {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@HoldToRecordFab
                    }
                    captureError = null
                    runCatching { recorder.start() }
                        .onSuccess { recording = true }
                        .onFailure {
                            captureError = it.message ?: it.toString()
                            Log.e("FAB", "recorder failed", it)
                        }
                },
                onReleased = { cancelled ->
                    val wasRecording = recording
                    val file = recorder.stop()
                    recording = false
                    if (!wasRecording || file == null || file.length() < 1024) {
                        file?.delete()
                        return@HoldToRecordFab
                    }
                    transcribing = true
                    scope.launch {
                        val res = uploadAndTranscribe(file)
                        file.delete()
                        res.onSuccess { text ->
                            val trimmed = text.trim()
                            if (trimmed.isEmpty()) {
                                transcribing = false
                                captureError = "Empty transcription — try again."
                                return@launch
                            }
                            val created = IdeaRepo.create(
                                com.joncas.jontracker.api.IdeaCreate(transcript = trimmed),
                            )
                            transcribing = false
                            if (created != null) {
                                IdeaRepo.requestSelection(created.id)
                                nav.navigate("inbox") {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                // Backend rejected — fall back to the sheet so
                                // the user can edit and retry.
                                initialTranscript = trimmed
                                captureOpen = true
                            }
                        }.onFailure {
                            transcribing = false
                            Log.e("FAB", "transcribe failed", it)
                            captureError = it.message ?: it.toString()
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { Prefs.setLastView(context, tab.route) }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = "week") {
                composable("day") { DayScreen() }
                composable("week") { WeekScreen() }
                composable("month") { MonthScreen() }
                composable("backlog") { BacklogScreen() }
                composable("streaks") { StreaksScreen() }
                composable("inbox") { InboxScreen() }
                composable("features") { FeaturesScreen() }
            }
            if (error != null) {
                ErrorBanner(message = error!!, onDismiss = { TaskRepo.clearError() })
            }
            if (captureOpen) {
                IdeaCaptureSheet(
                    onDismiss = {
                        captureOpen = false
                        initialTranscript = ""
                    },
                    initialTranscript = initialTranscript,
                )
            }
            if (recording) {
                RecordingOverlay()
            }
            captureError?.let { err ->
                ErrorBanner(message = err, onDismiss = { captureError = null })
            }
        }
    }
}

@Composable
private fun HoldToRecordFab(
    enabled: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    onTap: () -> Unit,
    onPressStart: () -> Unit,
    onReleased: (cancelled: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val bg = when {
        recording -> MaterialTheme.colorScheme.error
        transcribing -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val fg = when {
        recording -> MaterialTheme.colorScheme.onError
        transcribing -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val infinite = rememberInfiniteTransition(label = "rec-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val scale by animateFloatAsState(
        targetValue = if (recording) pulse else 1f,
        label = "fab-scale",
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        // The recorder starts immediately on every press; if the
                        // touch turns out to be a short tap (<250 ms) we discard
                        // the file and treat it as a typed-entry tap instead.
                        val pressTime = System.currentTimeMillis()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPressStart()
                        val released = tryAwaitRelease()
                        val duration = System.currentTimeMillis() - pressTime
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (duration < 250) {
                            onReleased(true) // discard short fragment
                            onTap()
                        } else {
                            onReleased(!released)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (transcribing) {
            CircularProgressIndicator(color = fg, modifier = Modifier.size(28.dp))
        } else {
            Icon(
                Icons.Filled.Mic,
                contentDescription = if (recording) "Recording…" else "Hold to record",
                tint = fg,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun RecordingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 140.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Text(
                "Recording — release to finish, slide away to cancel",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
