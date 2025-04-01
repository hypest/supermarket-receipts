package com.hypest.supermarketreceiptsapp.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

@Composable
fun RequestCameraPermission(
    context: Context,
    onPermissionResult: (Boolean) -> Unit
) {
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
            onPermissionResult(granted)
        }
    )

    LaunchedEffect(Unit) { // Request permission when composable enters composition
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            // Already granted, notify the caller
            onPermissionResult(true)
        }
    }

    // You might want to show UI while permission is being requested or if denied,
    // but for now, this composable just handles the logic.
    // The caller (e.g., MainScreen) will decide what UI to show based on the result.
}
