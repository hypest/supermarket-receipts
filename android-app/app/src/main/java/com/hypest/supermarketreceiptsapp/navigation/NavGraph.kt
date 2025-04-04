package com.hypest.supermarketreceiptsapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.* // Import runtime functions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel // Import hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hypest.supermarketreceiptsapp.ui.screens.LoginScreen
import com.hypest.supermarketreceiptsapp.ui.screens.ReceiptsScreen // Import ReceiptsScreen
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel // Import AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus // v2 import

@Composable
fun NavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel() // Inject AuthViewModel here to check initial state
) {
    // Observe session status to determine start destination
    val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()

    // Determine the start destination based on the *initial* observed status
    val startDestination = remember(sessionStatus) {
        when (sessionStatus) {
            is SessionStatus.Authenticated -> Screen.Receipts.route
            is SessionStatus.NotAuthenticated -> Screen.Login.route
            SessionStatus.Initializing -> null // Use v2 Initializing state
            // SessionStatus.NetworkError -> Screen.Login.route // Handle other states if needed
            else -> null // Represents other states
        }
    }

    // Show loading indicator while determining start destination
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return // Don't render NavHost until destination is determined
    }

    NavHost(
        navController = navController,
        startDestination = startDestination // Use dynamically determined start destination
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        // Removed MainScreen composable
        composable(route = Screen.Receipts.route) { backStackEntry -> // Get NavBackStackEntry
            // Scope ViewModels to the NavBackStackEntry
            val scopedAuthViewModel = hiltViewModel<AuthViewModel>(backStackEntry)
            val receiptsViewModel = hiltViewModel<com.hypest.supermarketreceiptsapp
                .viewmodel.ReceiptsViewModel>(backStackEntry)

            ReceiptsScreen(
                receiptsViewModel = receiptsViewModel,
                authViewModel = scopedAuthViewModel // Pass the correctly scoped AuthViewModel
            )
        }
        // Add other destinations later
    }
}
