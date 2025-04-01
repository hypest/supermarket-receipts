package com.hypest.supermarketreceiptsapp.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

// Note: @SuppressLint("MissingPermission") is removed as Google Code Scanner handles its own UI/permissions temporarily.
// However, the calling composable should still ensure camera permission is granted *before* showing this scanner.
@Composable
fun QrCodeScanner(
    modifier: Modifier = Modifier, // Modifier might not be needed anymore unless wrapping Box is desired
    onQrCodeScanned: (String) -> Unit,
    onScanError: (Exception) -> Unit = { Log.e("QrCodeScanner", "Scan failed", it) }, // Optional error handler
    onScanCancelled: () -> Unit = { Log.d("QrCodeScanner", "Scan cancelled by user") } // Optional cancellation handler
) {
    val context = LocalContext.current
    var hasFiredScan by remember { mutableStateOf(false) } // Prevent multiple scan starts

    // Optional: Check for Google Play Services availability and install scanner module if needed
    LaunchedEffect(Unit) {
        val moduleInstall = ModuleInstall.getClient(context)
        moduleInstall.areModulesAvailable(GmsBarcodeScanning.getClient(context))
            .addOnSuccessListener {
                if (!it.areModulesAvailable()) {
                    Log.d("QrCodeScanner", "Scanner module not available, requesting install.")
                    val moduleInstallRequest = ModuleInstallRequest.newBuilder()
                        .addApi(GmsBarcodeScanning.getClient(context))
                        .build()
                    moduleInstall.installModules(moduleInstallRequest)
                        .addOnSuccessListener { Log.d("QrCodeScanner", "Scanner module installed.") }
                        .addOnFailureListener { e -> Log.e("QrCodeScanner", "Scanner module install failed.", e) }
                } else {
                    Log.d("QrCodeScanner", "Scanner module already available.")
                }
            }
            .addOnFailureListener { e -> Log.e("QrCodeScanner", "Could not check module availability.", e) }
    }


    // Configure the scanner
    val options = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom() // Recommended for better scanning
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, options) }

    // Trigger the scan when the composable enters the composition
    LaunchedEffect(key1 = scanner, key2 = hasFiredScan) {
        if (!hasFiredScan) {
            hasFiredScan = true // Ensure scan starts only once per composition instance
            Log.d("QrCodeScanner", "Starting scan...")
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    // Handle successful scan
                    val rawValue = barcode.rawValue
                    if (rawValue != null) {
                        // Basic URL validation
                        if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
                            Log.d("QrCodeScanner", "QR Code Scanned: $rawValue")
                            onQrCodeScanned(rawValue)
                        } else {
                            Log.w("QrCodeScanner", "Scanned value is not a URL: $rawValue")
                            // Optionally call onError or onCancelled if non-URL is considered an error/cancellation
                            onScanError(IllegalArgumentException("Scanned value is not a valid URL"))
                        }
                    } else {
                        Log.w("QrCodeScanner", "Scan successful but no raw value found.")
                        onScanError(IllegalArgumentException("Scan successful but no value found"))
                    }
                }
                .addOnFailureListener { e ->
                    // Handle scan failure (e.g., camera error, module not available)
                    Log.e("QrCodeScanner", "Scan failed", e)
                    onScanError(e)
                }
                .addOnCanceledListener {
                    // Handle scan cancellation by the user
                    Log.d("QrCodeScanner", "Scan cancelled by user.")
                    onScanCancelled()
                }
        }
    }

    // No UI needed here as the scanner provides its own activity.
    // You might want a placeholder or loading indicator while the scanner initializes/starts.
    // Box(modifier = modifier) { /* Optional placeholder UI */ }
}
