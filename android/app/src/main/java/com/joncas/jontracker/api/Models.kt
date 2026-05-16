package com.joncas.jontracker.api

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: Int,
    val title: String,
    val task_type: String, // "recurring" | "single" | "birthday" | "weekly_goal"
    val weekdays: List<Int>? = null,
    val fixed_date: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val is_todo: Boolean,
    val target_per_week: Int? = null,
    val created_at: String,
    val completed_dates: List<String> = emptyList()
)

@Serializable
data class TaskPayload(
    val title: String,
    val task_type: String,
    val weekdays: List<Int>? = null,
    val fixed_date: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val is_todo: Boolean? = null,
    val target_per_week: Int? = null
)

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val all_day: Boolean,
    val kind: String, // "event" | "birthday"
    val location: String? = null,
    val description: String? = null
)

@Serializable
data class CalendarStatus(val configured: Boolean)
