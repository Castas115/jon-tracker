package com.joncas.jontracker.data

import com.joncas.jontracker.api.FeatureRequest
import com.joncas.jontracker.api.FeatureRequestPayload
import com.joncas.jontracker.api.TrackerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FeatureRepo {

    private val _features = MutableStateFlow<List<FeatureRequest>>(emptyList())
    val features: StateFlow<List<FeatureRequest>> = _features.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh() {
        runCatching { _features.value = TrackerApi.listFeatures() }
            .onFailure { _error.value = it.message ?: it.toString() }
    }

    suspend fun update(id: Int, payload: FeatureRequestPayload): FeatureRequest? = runCatching {
        val updated = TrackerApi.updateFeature(id, payload)
        _features.value = _features.value.map { if (it.id == id) updated else it }
        updated
    }.onFailure { _error.value = it.message ?: it.toString() }.getOrNull()

    suspend fun remove(id: Int) {
        runCatching {
            TrackerApi.deleteFeature(id)
            _features.value = _features.value.filter { it.id != id }
        }.onFailure { _error.value = it.message ?: it.toString() }
    }

    fun clearError() {
        _error.value = null
    }
}
