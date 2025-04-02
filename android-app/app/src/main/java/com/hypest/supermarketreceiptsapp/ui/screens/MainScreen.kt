package com.hypest.supermarketreceiptsapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hypest.supermarketreceiptsapp.navigation.Screen
import com.hypest.supermarketreceiptsapp.ui.components.HtmlExtractorWebView // Import the new WebView component
import com.hypest.supermarketreceiptsapp.ui.components.QrCodeScanner
import com.hypest.supermarketreceiptsapp.ui.components.RequestCameraPermission
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel
import com.hypest.supermarketreceiptsapp.viewmodel.MainScreenState
import com.hypest.supermarketreceiptsapp.viewmodel.MainViewModel
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun MainScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
    val screenState by mainViewModel.screenState.collectAsStateWithLifecycle() // Collect the unified state
    val context = LocalContext.current

    // --- Side Effects ---

    // Navigate to Login if not authenticated
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.NotAuthenticated) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Main.route) { inclusive = true }
            }
        }
    }

    // Request camera permission (only needs to be *present* in composition)
    // The result is handled by the ViewModel's onPermissionResult
    RequestCameraPermission(
        context = context,
        onPermissionResult = { granted ->
            mainViewModel.onPermissionResult(granted)
        }
    )

    // Launch scanner when state becomes Scanning
    // Note: QrCodeScanner manages its own lifecycle via LaunchedEffect internally
    if (screenState == MainScreenState.Scanning) {
        QrCodeScanner(
            onQrCodeScanned = { url -> mainViewModel.onQrCodeScanned(url) },
            onScanCancelled = { mainViewModel.onScanCancelled() },
            onScanError = { exception -> mainViewModel.onScanError(exception) }
        )
    }

    // --- UI ---

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center // Center content like messages/buttons
        ) {
            // Main UI logic driven by screenState
            when (val state = screenState) { // Use 'state' for easier access inside when
                MainScreenState.CheckingPermission -> {
                    // Optionally show a loading indicator while checking permission
                    CircularProgressIndicator()
                }
                is MainScreenState.NoPermission -> {
                    // Show permission denied message and Logout button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, modifier = Modifier.padding(16.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { authViewModel.signOut() }) {
                            Text("Logout")
                        }
                        // Optionally add a button to re-request permission or go to settings
                    }
                }
                MainScreenState.ReadyToScan -> {
                    // Show "Scan QR Code" and Logout buttons
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { mainViewModel.startScanning() }) {
                            Text("Scan QR Code")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { authViewModel.signOut() }) {
                            Text("Logout")
                        }
                    }
                }
                MainScreenState.Scanning -> {
                    // The QrCodeScanner is launched via the side-effect above.
                    // We might show a subtle background or placeholder here,
                    // but the scanner UI itself takes over.
                    // Text("Launching Scanner...") // Or just an empty Box
                }
                is MainScreenState.ExtractingHtml -> {
                    // Show the WebView fullscreen while extracting
                    // The WebView itself will show loading progress/page content
                    HtmlExtractorWebView(
                        url = state.url,
                        onHtmlExtracted = { url, html -> mainViewModel.onHtmlExtracted(url, html) },
                        onError = { url, error -> mainViewModel.onHtmlExtractionError(url, error) },
                        modifier = Modifier.fillMaxSize() // Make WebView fill the screen
                    )
                }
                is MainScreenState.Processing -> {
                    // Show loading indicator while submitting data
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing receipt...") // Updated text
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { authViewModel.signOut() }, enabled = false) {
                            Text("Logout") // Keep logout disabled
                        }
                    }
                }
                MainScreenState.Success -> {
                    // Show success message, "Scan Another", and Logout buttons
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Receipt Processed Successfully!") // Updated text
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { mainViewModel.resetToReadyState() }) {
                            Text("Scan Another")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { authViewModel.signOut() }) {
                            Text("Logout")
                        }
                    }
                }
                is MainScreenState.Error -> {
                    // Show error message, "Try Again", and Logout buttons
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.message}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { mainViewModel.resetToReadyState() }) {
                            Text("Try Again")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { authViewModel.signOut() }) {
                            Text("Logout")
                        }
                    }
                }
            }
        }
    }
}
