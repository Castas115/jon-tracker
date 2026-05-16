package com.joncas.jontracker.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DayScreen() = Placeholder("Day")

@Composable
fun WeekScreen() = Placeholder("Week")

@Composable
fun MonthScreen() = Placeholder("Month")

@Composable
fun BacklogScreen() = Placeholder("Backlog")

@Composable
private fun Placeholder(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name view — coming")
    }
}
