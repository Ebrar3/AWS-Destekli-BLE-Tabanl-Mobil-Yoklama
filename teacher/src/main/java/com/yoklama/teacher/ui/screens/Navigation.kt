package com.yoklama.teacher.ui.screens

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yoklama.teacher.viewmodel.TeacherViewModel

@Composable
fun Navigation(viewModel: TeacherViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {

        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onStartSession = {
                    navController.navigate("active_session")
                },
                onViewReport = { courseCode ->
                    navController.navigate("report/$courseCode")
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("active_session") {
            ActiveSessionScreen(
                viewModel = viewModel,
                onSessionEnded = {
                    val courseCode = viewModel.currentCourseCode ?: "BLM"
                    viewModel.resetSession()
                    navController.navigate("report/$courseCode") {
                        popUpTo("active_session") { inclusive = true }
                    }
                },
                onBack = {
                    // BLE ve session'u temizle
                    viewModel.stopAutoCheckin()
                    viewModel.stopBle()
                    viewModel.resetSession()
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("report/{courseCode}") { backStackEntry ->
            val courseCode = backStackEntry.arguments?.getString("courseCode") ?: ""
            ReportScreen(
                viewModel = viewModel,
                courseCode = courseCode,
                onBack = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
