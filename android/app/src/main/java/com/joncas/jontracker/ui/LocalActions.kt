package com.joncas.jontracker.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.joncas.jontracker.api.Task
import java.time.LocalDate

/** Triggers the edit form for an existing task. Provided at the activity root. */
val LocalEditTask = staticCompositionLocalOf<(Task) -> Unit> {
    error("LocalEditTask not provided")
}

/** Opens the create-task form prefilled with the given date. */
val LocalCreateOnDate = staticCompositionLocalOf<(LocalDate) -> Unit> {
    error("LocalCreateOnDate not provided")
}
