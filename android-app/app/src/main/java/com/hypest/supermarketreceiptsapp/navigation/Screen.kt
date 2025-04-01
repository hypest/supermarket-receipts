package com.hypest.supermarketreceiptsapp.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login_screen")
    object Main : Screen("main_screen") // This might host the QR scanner or lead to it
    // Add other screens like a list view if needed later
}
