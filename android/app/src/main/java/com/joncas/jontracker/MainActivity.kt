package com.joncas.jontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.joncas.jontracker.data.Prefs
import com.joncas.jontracker.ui.AppScaffold
import com.joncas.jontracker.ui.theme.JonTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val theme by Prefs.theme(context).collectAsState(initial = "dark")
            val isDark = theme != "light"

            JonTrackerTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScaffold(
                        isDark = isDark,
                        onToggleTheme = {
                            scope.launch {
                                Prefs.setTheme(context, if (isDark) "light" else "dark")
                            }
                        },
                        onCreate = { /* dialog wired in task 8 */ },
                    )
                }
            }
        }
    }
}
