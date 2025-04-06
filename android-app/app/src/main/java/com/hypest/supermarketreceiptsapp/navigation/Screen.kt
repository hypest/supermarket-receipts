package com.hypest.supermarketreceiptsapp.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login_screen")
    data object Main : Screen("main_screen") // This might host the QR scanner or lead to it
    data object Receipts : Screen("receipts_screen") // Receipts list screen route

    // Add Receipt Detail screen route with argument
    data object ReceiptDetail : Screen("receipt_detail_screen/{receiptId}") {
        // Helper function to create the route with a specific ID
        fun createRoute(receiptId: String) = "receipt_detail_screen/$receiptId"
    }
}
