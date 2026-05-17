package com.joncas.jontracker.api

import kotlinx.serialization.Serializable

@Serializable
data class TargetSegment(
    val weekdays: List<Int>,
    val target: Int
)

@Serializable
data class Task(
    val id: Int,
    val title: String,
    val description: String? = null,
    val task_type: String, // "recurring" | "single" | "birthday" | "weekly_goal"
    val weekdays: List<Int>? = null,
    val fixed_date: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val is_todo: Boolean,
    val target_per_week: Int? = null,
    val target_segments: List<TargetSegment>? = null,
    val show_in_upcoming: Boolean = true,
    val notify_enabled: Boolean = false,
    val notify_minutes_before: Int = 0,
    val notify_at: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val created_at: String,
    val completed_dates: List<String> = emptyList()
)

@Serializable
data class TaskPayload(
    val title: String,
    val description: String? = null,
    val task_type: String,
    val weekdays: List<Int>? = null,
    val fixed_date: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val is_todo: Boolean? = null,
    val target_per_week: Int? = null,
    val target_segments: List<TargetSegment>? = null,
    val show_in_upcoming: Boolean? = null,
    val notify_enabled: Boolean? = null,
    val notify_minutes_before: Int? = null,
    val notify_at: String? = null,
    val start_date: String? = null,
    val end_date: String? = null
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

@Serializable
data class IdeaMessage(
    val id: Int,
    val role: String, // user | assistant
    val text: String,
    val created_at: String,
)

@Serializable
data class Idea(
    val id: Int,
    val kind: String, // task | feature | unknown
    val title: String,
    val transcript: String,
    val status: String, // new | needs_info | in_progress | done | rejected
    val linked_task_id: Int? = null,
    val created_at: String,
    val updated_at: String,
    val messages: List<IdeaMessage> = emptyList(),
)

@Serializable
data class IdeaCreate(
    val transcript: String,
    val kind: String = "unknown",
    val title: String = "",
)

@Serializable
data class IdeaMessageCreate(
    val role: String,
    val text: String,
)

@Serializable
data class IdeaUpdate(
    val kind: String? = null,
    val title: String? = null,
    val status: String? = null,
    val linked_task_id: Int? = null,
)

@Serializable
data class TranscribeResponse(val text: String)
