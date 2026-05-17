package com.joncas.jontracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joncas.jontracker.data.IdeaRepo
import com.joncas.jontracker.data.Prefs
import com.joncas.jontracker.data.TaskRepo
import com.joncas.jontracker.ui.views.BacklogScreen
import com.joncas.jontracker.ui.views.DayScreen
import com.joncas.jontracker.ui.views.InboxScreen
import com.joncas.jontracker.ui.views.MonthScreen
import com.joncas.jontracker.ui.views.StreaksScreen
import com.joncas.jontracker.ui.views.WeekScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("day", "Day", Icons.Filled.Today),
    Tab("week", "Week", Icons.Filled.DateRange),
    Tab("month", "Month", Icons.Filled.CalendarMonth),
    Tab("backlog", "Backlog", Icons.Filled.List),
    Tab("streaks", "Streaks", Icons.Filled.Whatshot),
    Tab("inbox", "Inbox", Icons.Filled.Inbox),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onCreate: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val error by TaskRepo.error.collectAsState()
    var captureOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TaskRepo.refresh()
        TaskRepo.refreshCalendar()
        IdeaRepo.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TABS.firstOrNull { it.route == currentRoute }?.label ?: "Tracker") },
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = "New task")
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { captureOpen = true }) {
                Icon(Icons.Filled.Mic, contentDescription = "Capture idea")
            }
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { Prefs.setLastView(context, tab.route) }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = "week") {
                composable("day") { DayScreen() }
                composable("week") { WeekScreen() }
                composable("month") { MonthScreen() }
                composable("backlog") { BacklogScreen() }
                composable("streaks") { StreaksScreen() }
                composable("inbox") { InboxScreen() }
            }
            if (error != null) {
                ErrorBanner(message = error!!, onDismiss = { TaskRepo.clearError() })
            }
            if (captureOpen) {
                IdeaCaptureSheet(onDismiss = { captureOpen = false })
            }
        }
    }
}
