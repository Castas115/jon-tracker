package com.joncas.jontracker.data

import com.joncas.jontracker.api.Idea
import com.joncas.jontracker.api.IdeaCreate
import com.joncas.jontracker.api.IdeaMessageCreate
import com.joncas.jontracker.api.IdeaUpdate
import com.joncas.jontracker.api.TrackerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Singleton state for ideas + their conversation threads. */
object IdeaRepo {

    private val _ideas = MutableStateFlow<List<Idea>>(emptyList())
    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh() {
        runCatching { _ideas.value = TrackerApi.listIdeas() }
            .onFailure { _error.value = it.message ?: it.toString() }
    }

    suspend fun create(payload: IdeaCreate): Idea? = runCatching {
        val i = TrackerApi.createIdea(payload)
        _ideas.value = (listOf(i) + _ideas.value).distinctBy { it.id }
        i
    }.onFailure { _error.value = it.message ?: it.toString() }.getOrNull()

    suspend fun postMessage(ideaId: Int, payload: IdeaMessageCreate): Idea? = runCatching {
        val i = TrackerApi.postIdeaMessage(ideaId, payload)
        _ideas.value = _ideas.value.map { if (it.id == ideaId) i else it }
        i
    }.onFailure { _error.value = it.message ?: it.toString() }.getOrNull()

    suspend fun update(id: Int, payload: IdeaUpdate): Idea? = runCatching {
        val i = TrackerApi.updateIdea(id, payload)
        _ideas.value = _ideas.value.map { if (it.id == id) i else it }
        i
    }.onFailure { _error.value = it.message ?: it.toString() }.getOrNull()

    suspend fun remove(id: Int) {
        runCatching {
            TrackerApi.deleteIdea(id)
            _ideas.value = _ideas.value.filter { it.id != id }
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    fun clearError() {
        _error.value = null
    }
}
