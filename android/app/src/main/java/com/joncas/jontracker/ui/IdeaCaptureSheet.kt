package com.joncas.jontracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.joncas.jontracker.api.IdeaCreate
import com.joncas.jontracker.api.TrackerApi
import com.joncas.jontracker.audio.AudioRecorder
import com.joncas.jontracker.data.IdeaRepo
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaCaptureSheet(
    onDismiss: () -> Unit,
    initialTranscript: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf(initialTranscript) }
    var kind by remember { mutableStateOf("unknown") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Capture idea",
                style = MaterialTheme.typography.titleMedium,
            )

            // Big mic / stop button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    enabled = hasPermission && !transcribing && !submitting,
                    onClick = {
                        if (recording) {
                            val file = recorder.stop()
                            recording = false
                            if (file != null && file.length() > 0) {
                                transcribing = true
                                scope.launch {
                                    val res = uploadAndTranscribe(file)
                                    transcribing = false
                                    res.onSuccess { transcript = it }
                                        .onFailure { error = it.message ?: it.toString() }
                                    file.delete()
                                }
                            }
                        } else {
                            error = null
                            runCatching { recorder.start() }
                                .onSuccess { recording = true }
                                .onFailure { error = it.message ?: it.toString() }
                        }
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    if (transcribing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (recording) "Stop recording" else "Start recording",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            if (!hasPermission) {
                Text(
                    "Microphone permission required.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = transcript,
                onValueChange = { transcript = it },
                label = { Text("Transcript") },
                placeholder = { Text("Tap the mic, or type here") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Text("Type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = kind == "unknown",
                    onClick = { kind = "unknown" },
                    label = { Text("Let AI decide") },
                )
                FilterChip(
                    selected = kind == "task",
                    onClick = { kind = "task" },
                    label = { Text("Task") },
                )
                FilterChip(
                    selected = kind == "feature",
                    onClick = { kind = "feature" },
                    label = { Text("Feature") },
                )
            }

            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = transcript.isNotBlank() && !submitting && !recording && !transcribing,
                    onClick = {
                        submitting = true
                        scope.launch {
                            val created = IdeaRepo.create(
                                IdeaCreate(transcript = transcript.trim(), kind = kind),
                            )
                            submitting = false
                            if (created != null) onDismiss()
                        }
                    },
                ) { Text(if (submitting) "Saving…" else "Save") }
            }
        }
    }
}

suspend fun uploadAndTranscribe(file: File): Result<String> = runCatching {
    val bytes = file.readBytes()
    val res = TrackerApi.transcribe(bytes, "audio/mp4", file.name)
    res.text
}
