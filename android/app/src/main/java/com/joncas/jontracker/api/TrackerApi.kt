package com.joncas.jontracker.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TrackerApi {

    // TODO: move to a setting / DataStore. Default to local LAN via Tailscale MagicDNS.
    private const val BASE_URL = "http://pi:8000"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun listTasks(): List<Task> =
        client.get("$BASE_URL/tasks").body()

    suspend fun create(payload: TaskPayload): Task =
        client.post("$BASE_URL/tasks") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    suspend fun update(id: Int, payload: TaskPayload): Task =
        client.patch("$BASE_URL/tasks/$id") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    suspend fun remove(id: Int) {
        client.delete("$BASE_URL/tasks/$id")
    }

    enum class ToggleAction(val wire: String) {
        TOGGLE("toggle"),
        ADD("add"),
        REMOVE("remove"),
    }

    suspend fun toggle(
        taskId: Int,
        date: LocalDate,
        action: ToggleAction = ToggleAction.TOGGLE,
    ): Task {
        val k = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return client.post("$BASE_URL/tasks/$taskId/toggle") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("completed_on" to k, "action" to action.wire))
        }.body()
    }

    suspend fun calendarStatus(): CalendarStatus =
        client.get("$BASE_URL/calendar/status").body()

    suspend fun calendarEvents(from: LocalDate, to: LocalDate): List<CalendarEvent> =
        client.get("$BASE_URL/calendar/events") {
            parameter("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
            parameter("to", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }.body()
}
