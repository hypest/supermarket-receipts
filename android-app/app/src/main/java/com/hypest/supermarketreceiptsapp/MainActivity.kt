package com.hypest.supermarketreceiptsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
// Removed Text import as it's replaced by NavGraph
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.hypest.supermarketreceiptsapp.navigation.NavGraph // Import NavGraph
// Removed Preview import as Greeting is removed
import com.hypest.supermarketreceiptsapp.ui.theme.SupermarketReceiptsAppTheme
import dagger.hilt.android.AndroidEntryPoint
// Removed SupabaseClient import
import javax.inject.Inject // Keep Inject if other things are injected, otherwise remove


@AndroidEntryPoint // Add Hilt entry point annotation
class MainActivity : ComponentActivity() {

    // Removed SupabaseClient injection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SupermarketReceiptsAppTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Set up the NavHost using the NavGraph composable
                    NavGraph(navController = navController)
                    // Note: innerPadding might need to be passed down into NavGraph/Screens if using Scaffold elements like TopAppBar
                }
            }
        }
    }
}

// Removed Greeting and GreetingPreview composables
