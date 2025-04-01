package com.hypest.supermarketreceiptsapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hypest.supermarketreceiptsapp.ui.screens.LoginScreen
import com.hypest.supermarketreceiptsapp.ui.screens.MainScreen
// Import ViewModels later when needed

@Composable
fun NavGraph(navController: NavHostController) {
    // TODO: Determine start destination based on auth state (e.g., from AuthViewModel)
    val startDestination = Screen.Login.route // Default to Login for now

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(route = Screen.Main.route) {
            MainScreen(navController = navController)
        }
        // Add other destinations later
    }
}
