package com.hypest.supermarketreceiptsapp

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
// Removed Text import as it's replaced by NavGraph
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel // Import hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.hypest.supermarketreceiptsapp.navigation.NavGraph // Import NavGraph
// Removed Preview import as Greeting is removed
import com.hypest.supermarketreceiptsapp.ui.theme.SupermarketReceiptsAppTheme
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject // Inject ConnectivityManager
    lateinit var connectivityManager: ConnectivityManager

    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupNetworkCallback()
        enableEdgeToEdge()
        setContent {
            SupermarketReceiptsAppTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel() // Get AuthViewModel instance
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Set up the NavHost using the NavGraph composable, passing the AuthViewModel
                    NavGraph(navController = navController, authViewModel = authViewModel)
                    // Note: innerPadding might need to be passed down into NavGraph/Screens if using Scaffold elements like TopAppBar
                }
            }
        }
    }

    private fun setupNetworkCallback() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Check if connection actually has internet capability
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (hasInternet) {
                    runOnUiThread { // Show Toast on UI thread
                        Toast.makeText(applicationContext, "Network connection available.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                runOnUiThread { // Show Toast on UI thread
                    Toast.makeText(applicationContext, "Network connection lost.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // Monitor internet capability
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister callback to prevent leaks
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
