package com.joncas.jontracker.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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

    suspend fun listIdeas(): List<Idea> =
        client.get("$BASE_URL/ideas").body()

    suspend fun getIdea(id: Int): Idea =
        client.get("$BASE_URL/ideas/$id").body()

    suspend fun createIdea(payload: IdeaCreate): Idea =
        client.post("$BASE_URL/ideas") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    suspend fun updateIdea(id: Int, payload: IdeaUpdate): Idea =
        client.patch("$BASE_URL/ideas/$id") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    suspend fun deleteIdea(id: Int) {
        client.delete("$BASE_URL/ideas/$id")
    }

    suspend fun postIdeaMessage(ideaId: Int, payload: IdeaMessageCreate): Idea =
        client.post("$BASE_URL/ideas/$ideaId/messages") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    suspend fun transcribe(audio: ByteArray, mimeType: String, filename: String): TranscribeResponse =
        client.post("$BASE_URL/ideas/transcribe") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "audio",
                            value = audio,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            },
                        )
                    }
                )
            )
        }.body()
}
