package app.floatap.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun FloatAppRoot(startOnGuide: Boolean, onLeaveToTarget: () -> Unit) {
    val nav = rememberNavController()
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
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            EditScreen(jobId = id, onBack = { nav.popBackStack() }, onLeaveToTarget = onLeaveToTarget)
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
