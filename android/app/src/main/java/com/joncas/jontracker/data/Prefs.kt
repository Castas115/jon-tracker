package com.joncas.jontracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jontracker_prefs")

object Prefs {
    private val THEME = stringPreferencesKey("theme") // "dark" | "light"
    private val LAST_VIEW = stringPreferencesKey("view") // "day"|"week"|"month"|"backlog"
    private val MONTH_OFFSET = intPreferencesKey("widget_month_offset")
    private val WEEK_OFFSET = intPreferencesKey("widget_week_offset")

    fun theme(context: Context): Flow<String> =
        context.dataStore.data.map { it[THEME] ?: "dark" }

    fun lastView(context: Context): Flow<String> =
        context.dataStore.data.map { it[LAST_VIEW] ?: "week" }

    suspend fun setTheme(context: Context, value: String) {
        context.dataStore.edit { it[THEME] = value }
    }

    suspend fun setLastView(context: Context, value: String) {
        context.dataStore.edit { it[LAST_VIEW] = value }
    }

    suspend fun monthOffset(context: Context): Int =
        context.dataStore.data.map { it[MONTH_OFFSET] ?: 0 }.first()

    suspend fun setMonthOffset(context: Context, value: Int) {
        context.dataStore.edit { it[MONTH_OFFSET] = value }
    }

    suspend fun weekOffset(context: Context): Int =
        context.dataStore.data.map { it[WEEK_OFFSET] ?: 0 }.first()

    suspend fun setWeekOffset(context: Context, value: Int) {
        context.dataStore.edit { it[WEEK_OFFSET] = value }
    }
}
