package com.twinmind.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.twinmind.app.ui.dashboard.DashboardScreen
import com.twinmind.app.ui.recording.RecordingScreen
import com.twinmind.app.ui.summary.SummaryScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val RECORDING = "recording/{sessionId}"
    const val SUMMARY = "summary/{sessionId}"

    fun recording(sessionId: Long) = "recording/$sessionId"
    fun summary(sessionId: Long) = "summary/$sessionId"
}

@Composable
fun TwinMindNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onStartRecording = { sessionId ->
                    navController.navigate(Routes.recording(sessionId))
                },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.recording(sessionId))
                },
                onOpenSummary = { sessionId ->
                    navController.navigate(Routes.summary(sessionId))
                }
            )
        }

        composable(
            route = Routes.RECORDING,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
            RecordingScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() },
                onViewSummary = { sid ->
                    navController.navigate(Routes.summary(sid))
                }
            )
        }

        composable(
            route = Routes.SUMMARY,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
            SummaryScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
