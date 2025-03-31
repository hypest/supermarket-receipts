package com.hypest.supermarketreceiptsapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.* // Import Box, Spacer, height
import androidx.compose.material3.* // Keep Button, Scaffold, Text, add CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel // Re-add hiltViewModel
import androidx.navigation.NavController
import com.hypest.supermarketreceiptsapp.navigation.Screen
import com.hypest.supermarketreceiptsapp.ui.components.QrCodeScanner
import com.hypest.supermarketreceiptsapp.ui.components.RequestCameraPermission
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel // Re-add AuthViewModel
import com.hypest.supermarketreceiptsapp.viewmodel.MainViewModel // Re-add MainViewModel
import com.hypest.supermarketreceiptsapp.viewmodel.SaveStatus // Re-add SaveStatus
import io.github.jan.supabase.auth.status.SessionStatus // Re-add SessionStatus

@Composable
fun MainScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(), // Re-inject AuthViewModel
    mainViewModel: MainViewModel = hiltViewModel() // Re-inject MainViewModel
) {
    val sessionStatus by authViewModel.sessionStatus.collectAsState() // Re-add session observation
    val hasCameraPermission by mainViewModel.hasCameraPermission // Use ViewModel state
    val saveStatus by mainViewModel.saveStatus // Use ViewModel state
    val context = LocalContext.current

    // Re-add navigation based on auth state
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.NotAuthenticated) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Main.route) { inclusive = true }
            }
        }
    }

    // Request camera permission (triggers ViewModel)
    RequestCameraPermission(
        context = context,
        onPermissionResult = { granted ->
            mainViewModel.onPermissionResult(granted) // Call ViewModel
        }
    )

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                // Show scanner based on ViewModel state
                if (saveStatus == SaveStatus.Idle || saveStatus == SaveStatus.Saving) {
                    QrCodeScanner(
                        modifier = Modifier.fillMaxSize(),
                        onQrCodeScanned = { url ->
                            mainViewModel.onQrCodeScanned(url) // Call ViewModel
                        }
                    )
                }

                // Overlay UI for status messages (using ViewModel state)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Display status based on ViewModel state
                    when (saveStatus) {
                        SaveStatus.Idle -> {
                            // Only show prompt if scanner is visible
                            if (hasCameraPermission) Text("Point camera at a QR code")
                        }
                        SaveStatus.Saving -> {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Saving URL...")
                        }
                        SaveStatus.Success -> {
                            Text("URL Saved Successfully!")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { mainViewModel.resetScanState() }) {
                                Text("Scan Another")
                            }
                        }
                        is SaveStatus.Error -> {
                            Text("Error: ${(saveStatus as SaveStatus.Error).message}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { mainViewModel.resetScanState() }) {
                                Text("Try Again")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { authViewModel.signOut() }) { // Call ViewModel for logout
                        Text("Logout")
                    }
                }
            } else {
                // Show message if permission is not granted (or still being requested)
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     // Check saveStatus for specific permission error message
                     val errorMsg = (saveStatus as? SaveStatus.Error)?.message ?: "Camera permission is required."
                     Text(errorMsg)
                     Spacer(modifier = Modifier.height(16.dp))
                     Button(onClick = { authViewModel.signOut() }) { // Call ViewModel for logout
                         Text("Logout")
                     }
                 }
            }
        }
    }
}
