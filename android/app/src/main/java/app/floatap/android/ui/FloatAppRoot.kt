package app.floatap.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow

object OpenBus {
    val editJobId = MutableStateFlow<Long?>(null)
}

@Composable
fun FloatAppRoot(startOnGuide: Boolean) {
    val nav = rememberNavController()
    val pendingEdit by OpenBus.editJobId.collectAsState()
    LaunchedEffect(pendingEdit, startOnGuide) {
        val id = pendingEdit ?: return@LaunchedEffect
        OpenBus.editJobId.value = null
        if (startOnGuide) return@LaunchedEffect
        nav.navigate("edit/$id") {
            launchSingleTop = true
            popUpTo("home") { inclusive = false }
        }
    }
    NavHost(navController = nav, startDestination = if (startOnGuide) "guide" else "home") {
        composable("guide") {
            GuideScreen {
                nav.navigate("home") { popUpTo("guide") { inclusive = true } }
            }
        }
        composable("home") {
            HomeScreen(
                onCreate = { nav.navigate("edit/0") },
                onEdit = { nav.navigate("edit/$it") },
                onSettings = { nav.navigate("settings") },
                onRuns = { nav.navigate("runs") },
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            EditScreen(jobId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("runs") {
            RunsScreen(onBack = { nav.popBackStack() })
        }
    }
}
