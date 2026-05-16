package com.joncas.jontracker.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.joncas.jontracker.api.Task

/** Triggers the edit form for an existing task. Provided at the activity root. */
val LocalEditTask = staticCompositionLocalOf<(Task) -> Unit> {
    error("LocalEditTask not provided")
}
