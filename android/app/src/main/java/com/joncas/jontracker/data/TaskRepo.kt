package com.joncas.jontracker.data

import com.joncas.jontracker.api.CalendarEvent
import com.joncas.jontracker.api.Task
import com.joncas.jontracker.api.TaskPayload
import com.joncas.jontracker.api.TrackerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Singleton state for tasks + calendar. UI observes the flows; mutations go through here. */
object TaskRepo {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _calendarConfigured = MutableStateFlow(false)
    val calendarConfigured: StateFlow<Boolean> = _calendarConfigured.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val mutex = Mutex()

    suspend fun refresh() = mutex.withLock {
        _loading.value = true
        _error.value = null
        try {
            _tasks.value = TrackerApi.listTasks()
        } catch (e: Throwable) {
            _error.value = e.message ?: e.toString()
        } finally {
            _loading.value = false
        }
    }

    suspend fun refreshCalendar() {
        runCatching {
            val status = TrackerApi.calendarStatus()
            _calendarConfigured.value = status.configured
            if (status.configured) {
                val now = LocalDate.now()
                val from = now.withDayOfMonth(1).minusDays(14)
                val to = now.plusMonths(2).withDayOfMonth(1).minusDays(1)
                _events.value = TrackerApi.calendarEvents(from, to)
            } else {
                _events.value = emptyList()
            }
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    suspend fun create(payload: TaskPayload) {
        runCatching {
            val t = TrackerApi.create(payload)
            _tasks.value = _tasks.value + t
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    suspend fun update(id: Int, payload: TaskPayload) {
        runCatching {
            val t = TrackerApi.update(id, payload)
            _tasks.value = _tasks.value.map { if (it.id == id) t else it }
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    suspend fun remove(id: Int) {
        runCatching {
            TrackerApi.remove(id)
            _tasks.value = _tasks.value.filter { it.id != id }
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    suspend fun toggle(
        task: Task,
        date: LocalDate,
        action: TrackerApi.ToggleAction = TrackerApi.ToggleAction.TOGGLE,
    ) {
        runCatching {
            val t = TrackerApi.toggle(task.id, date, action)
            _tasks.value = _tasks.value.map { if (it.id == task.id) t else it }
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    fun clearError() {
        _error.value = null
    }
}
