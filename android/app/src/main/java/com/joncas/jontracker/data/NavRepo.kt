package com.joncas.jontracker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Cross-screen "open me at this date / on this tab" hint. The widget
 *  populates it when the user taps a day; views consume it once and clear. */
object NavRepo {

    private val _pendingDate = MutableStateFlow<LocalDate?>(null)
    val pendingDate: StateFlow<LocalDate?> = _pendingDate.asStateFlow()

    private val _pendingView = MutableStateFlow<String?>(null)
    val pendingView: StateFlow<String?> = _pendingView.asStateFlow()

    fun request(date: LocalDate?, view: String?) {
        _pendingDate.value = date
        _pendingView.value = view
    }

    fun consumeView(): String? {
        val v = _pendingView.value
        _pendingView.value = null
        return v
    }

    fun consumeDate(): LocalDate? {
        val d = _pendingDate.value
        _pendingDate.value = null
        return d
    }
}
