package com.hypest.supermarketreceiptsapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.* // Import Box, Spacer, height
import androidx.compose.material3.* // Keep Button, Scaffold, Text, add CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview // Add Preview for easier UI checks
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
    val hasCameraPermission by mainViewModel.hasCameraPermission
    val saveStatus by mainViewModel.saveStatus
    val isScannerVisible by mainViewModel.isScannerVisible // Get the new state
    val context = LocalContext.current

    // Navigation based on auth state
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
                // Show scanner only if permission granted AND scanner is set to visible
                if (isScannerVisible) {
                    QrCodeScanner(
                        // modifier = Modifier.fillMaxSize(), // Modifier might not be needed as scanner takes over screen
                        onQrCodeScanned = { url ->
                            mainViewModel.onQrCodeScanned(url) // Existing call
                        },
                        onScanCancelled = {
                            mainViewModel.hideScanner() // Hide scanner if user cancels
                        },
                        onScanError = { exception ->
                            // Log error or display message if needed, then hide scanner
                            // The error state will be set by onQrCodeScanned if it fails during save
                            // This handles errors *within* the scanner component itself
                            mainViewModel.hideScanner()
                        }
                    )
                }

                // Overlay UI for status messages AND the button to show scanner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Button to show scanner if it's hidden and permission is granted
                    if (!isScannerVisible && hasCameraPermission) {
                        Button(onClick = { mainViewModel.showScanner() }) {
                            Text("Scan QR Code")
                        }
                        Spacer(modifier = Modifier.height(8.dp)) // Add space if button is shown
                    }

                    // Display status based on ViewModel state (only when scanner is not visible or saving)
                    if (!isScannerVisible || saveStatus == SaveStatus.Saving) {
                        when (saveStatus) {
                            SaveStatus.Idle -> {
                                // No text needed here anymore, button handles showing scanner
                            }
                            SaveStatus.Saving -> {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Saving URL...")
                            }
                            SaveStatus.Success -> {
                                Text("URL Saved Successfully!")
                                Spacer(modifier = Modifier.height(8.dp))
                                // resetScanState now also shows the scanner
                                Button(onClick = { mainViewModel.resetScanState() }) {
                                    Text("Scan Another")
                                }
                            }
                            is SaveStatus.Error -> {
                                Text("Error: ${(saveStatus as SaveStatus.Error).message}")
                                Spacer(modifier = Modifier.height(8.dp))
                                // resetScanState now also shows the scanner
                                Button(onClick = { mainViewModel.resetScanState() }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                    // Logout button always visible when camera permission is granted
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
