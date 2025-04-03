package com.hypest.supermarketreceiptsapp.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login_screen")
    data object Main : Screen("main_screen") // This might host the QR scanner or lead to it
    data object Receipts : Screen("receipts_screen") // Add Receipts screen route
}
