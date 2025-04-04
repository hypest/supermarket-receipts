package com.hypest.supermarketreceiptsapp.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add // For FAB icon
import androidx.compose.material.icons.filled.ExitToApp // For Logout icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.model.ReceiptItem
import com.hypest.supermarketreceiptsapp.ui.components.HtmlExtractorWebView
import com.hypest.supermarketreceiptsapp.ui.components.QrCodeScanner
import com.hypest.supermarketreceiptsapp.ui.components.RequestCameraPermission
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsScreenState
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsListState
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsViewModel
import java.text.NumberFormat
import java.time.OffsetDateTime // Use OffsetDateTime for parsing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException // Import exception
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    receiptsViewModel: ReceiptsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel() // Inject AuthViewModel for logout
) {
    val receiptsListState by receiptsViewModel.receiptsListState.collectAsStateWithLifecycle()
    val receiptsScreenState by receiptsViewModel.screenState.collectAsStateWithLifecycle()
    var selectedReceipt by remember { mutableStateOf<Receipt?>(null) }
    val context = LocalContext.current

    // --- Side Effects from MainViewModel ---

    // Request camera permission
    RequestCameraPermission(
        context = context,
        onPermissionResult = { granted ->
            receiptsViewModel.onPermissionResult(granted)
        }
    )

    // Launch scanner when state becomes Scanning
    if (receiptsScreenState == ReceiptsScreenState.Scanning) {
        QrCodeScanner(
            onQrCodeScanned = { url -> receiptsViewModel.onQrCodeScanned(url) },
            onScanCancelled = { receiptsViewModel.resetToReadyState() },
            onScanError = { exception -> receiptsViewModel.onScanError(exception) }
        )
    }

    // --- UI ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Receipts") },
                actions = {
                    // Add Logout Button
                    IconButton(onClick = { authViewModel.signOut() }) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            // Show FAB only when ready to scan or if showing receipts list
            if (receiptsScreenState == ReceiptsScreenState.ReadyToScan || receiptsScreenState
                        is
                        ReceiptsScreenState.Success || receiptsScreenState is
                        ReceiptsScreenState.Error || receiptsScreenState ==
                ReceiptsScreenState.CheckingPermission || receiptsScreenState is
                        ReceiptsScreenState.NoPermission) {
                 FloatingActionButton(onClick = { receiptsViewModel.startScanning() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Scan New Receipt")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Content based on MainViewModel's state takes priority if active
            when (val state = receiptsScreenState) {
                ReceiptsScreenState.CheckingPermission -> {
                    // Show receipts list underneath? Or just loading? Let's show loading centered.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                        Text("Checking permissions...", modifier = Modifier.padding(top = 60.dp))
                    }
                }
                is ReceiptsScreenState.NoPermission -> {
                    // Show permission denied message over the receipts list
                     ReceiptsListContent(receiptsListState, selectedReceipt) {
                         selectedReceipt = it } // Show list behind
                     Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium).padding(16.dp)) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { /* TODO: Maybe link to app settings? */ }) {
                                Text("Open Settings") // Placeholder
                            }
                             Spacer(modifier = Modifier.height(8.dp))
                             Button(onClick = { receiptsViewModel.resetToReadyState() }) {
                                 // Allow retry/dismiss
                                Text("Dismiss")
                            }
                        }
                    }
                }
                ReceiptsScreenState.Scanning -> {
                    // Scanner overlay is handled by the QrCodeScanner composable launched via side effect
                    // Show the receipts list underneath subtly
                    ReceiptsListContent(receiptsListState, selectedReceipt) {
                        selectedReceipt = it }
                    // Optionally add a scrim/message:
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Text("Scanner Active", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                 is ReceiptsScreenState.ExtractingHtml -> {
                    // Show WebView for extraction, potentially blurring the background receipts
                    ReceiptsListContent(receiptsListState, selectedReceipt, modifier =
                    Modifier.then(
                        if (state.showOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(radius = 8.dp)
                        } else Modifier
                    )) { selectedReceipt = it } // Show list blurred behind

                    Box(modifier = Modifier.fillMaxSize()) { // Box for WebView and overlay
                        HtmlExtractorWebView(
                            url = state.url,
                            onHtmlExtracted = { url, html -> receiptsViewModel
                                .onHtmlExtracted(url, html) },
                            onError = { url, error -> receiptsViewModel.onHtmlExtractionError(url, error) },
                            onChallengeInteractionRequired = { receiptsViewModel.onChallengeInteractionRequired() },
                            modifier = Modifier.fillMaxSize() // WebView fills the box
                        )

                        // Overlay for loading/challenge state
                        AnimatedVisibility(
                            visible = state.showOverlay,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)), // Darker overlay
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Loading receipt...", color = Color.White)
                                }
                            }
                        }
                    }
                }
                is ReceiptsScreenState.Processing -> {
                    // Show processing indicator over the receipts list
                    ReceiptsListContent(receiptsListState, selectedReceipt) { selectedReceipt = it } // Show list behind
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Processing receipt...")
                        }
                    }
                }
                ReceiptsScreenState.Success -> {
                    // Show the main receipts list. A Snackbar could show success.
                    // Or briefly show a success message overlay? Let's stick to Snackbar for less intrusion.
                    // We need a ScaffoldState for Snackbar. Add later if needed.
                    LaunchedEffect(Unit) { // Reset state after success automatically
                        kotlinx.coroutines.delay(1500) // Show success briefly maybe? No, just reset.
                        receiptsViewModel.resetToReadyState()
                    }
                     ReceiptsListContent(receiptsListState, selectedReceipt) {
                         selectedReceipt = it }
                     // TODO: Add Snackbar for "Receipt Processed Successfully!"
                }
                is ReceiptsScreenState.Error -> {
                    // Show error message over the receipts list
                    ReceiptsListContent(receiptsListState, selectedReceipt) {
                        selectedReceipt = it } // Show list behind
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium).padding(16.dp)) {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { receiptsViewModel.resetToReadyState() }) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                // Default case: ReadyToScan or other idle states - show the receipts list
                else -> {
                    ReceiptsListContent(receiptsListState, selectedReceipt) {
                        selectedReceipt = it }
                }
            }
        }
    }
}

// Extracted composable for the actual list/detail view
@Composable
fun ReceiptsListContent(
    receiptsUiState: ReceiptsListState,
    selectedReceipt: Receipt?,
    modifier: Modifier = Modifier, // Allow passing modifiers (e.g., for blur)
    onReceiptSelected: (Receipt?) -> Unit
) {
     Box(modifier = modifier.fillMaxSize()) { // Apply modifier here
        when {
            receiptsUiState.isLoading && receiptsUiState.receipts.isEmpty() -> { // Show loading only if list is empty initially
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            receiptsUiState.error != null && receiptsUiState.receipts.isEmpty() -> { // Show error only if list is empty
                Text(
                    text = "Error loading receipts: ${receiptsUiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
            receiptsUiState.receipts.isEmpty() -> {
                Text(
                    text = "You have no receipts yet. Scan one using the '+' button!",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
            else -> {
                // Show loading indicator subtly at the top if loading more/refreshing
                if (receiptsUiState.isLoading) {
                     LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                ReceiptsContent( // The Row with List and Detail panes
                    receipts = receiptsUiState.receipts,
                    selectedReceipt = selectedReceipt,
                    onReceiptSelected = onReceiptSelected
                )
            }
        }
    }
}

@Composable
fun ReceiptsContent(
    receipts: List<Receipt>,
    selectedReceipt: Receipt?,
    onReceiptSelected: (Receipt?) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Receipts List Pane (Left)
        LazyColumn(
            modifier = Modifier
                .weight(1f) // Takes 1/3 of the width on larger screens implicitly
                .padding(8.dp)
                .fillMaxHeight()
        ) {
            items(receipts) { receipt ->
                ReceiptListItem(
                    receipt = receipt,
                    isSelected = receipt.id == selectedReceipt?.id,
                    onClick = { onReceiptSelected(receipt) }
                )
                HorizontalDivider()
            }
        }

        // Receipt Detail Pane (Right)
        Box(
            modifier = Modifier
                .weight(2f) // Takes 2/3 of the width
                .padding(8.dp)
                .fillMaxHeight()
        ) {
            if (selectedReceipt != null) {
                ReceiptDetailView(receipt = selectedReceipt)
            } else {
                Text(
                    "Select a receipt to view details",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun ReceiptListItem(
    receipt: Receipt,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = receipt.storeName ?: "Unknown Store",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = formatDisplayDate(receipt.receiptDate),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatCurrency(receipt.totalAmount),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ReceiptDetailView(receipt: Receipt) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text(receipt.storeName ?: "Receipt Details", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Date: ${formatDisplayDate(receipt.receiptDate)}", style = MaterialTheme.typography.bodyMedium)
        Text("Total: ${formatCurrency(receipt.totalAmount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        receipt.uid?.let { Text("UID: $it", style = MaterialTheme.typography.bodySmall) }
        Text("Scanned: ${formatDisplayDate(receipt.createdAt)}", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Items (${receipt.items.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (receipt.items.isNotEmpty()) {
            LazyColumn {
                items(receipt.items) { item ->
                    ReceiptItemRow(item = item)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        } else {
            Text("No items found for this receipt.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ReceiptItemRow(item: ReceiptItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(item.name, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 2)
        Text("${item.quantity} x ${formatCurrency(item.price / item.quantity)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp)) // Show unit price if possible
        Text(formatCurrency(item.price), fontWeight = FontWeight.Medium)
    }
}


// --- Helper Functions ---

private fun formatDisplayDate(isoString: String?): String {
    if (isoString == null) return "N/A"
    return try {
        // Parse using OffsetDateTime to handle the timezone offset correctly
        val offsetDateTime = OffsetDateTime.parse(isoString)
        // Define the desired output format
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            // No need to explicitly set zone here if we format the OffsetDateTime directly,
            // but converting to local time is usually desired for display.
            // Convert to the system's default time zone before formatting
            .withZone(ZoneId.systemDefault())
        // Format the ZonedDateTime representation in the system's default zone
        formatter.format(offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()))
    } catch (e: DateTimeParseException) {
        Log.w("ReceiptsScreen", "Failed to parse date: $isoString", e)
        isoString // Fallback to raw string if parsing fails
    } catch (e: Exception) {
        // Catch other potential exceptions during formatting or zone conversion
        Log.e("ReceiptsScreen", "Error formatting date: $isoString", e)
        isoString // Fallback
    }
}

private fun formatCurrency(amount: Double?): String {
    if (amount == null) return "N/A"
    val format = NumberFormat.getCurrencyInstance(Locale("el", "GR")) // Greek locale for Euro
    return try {
        format.format(amount)
    } catch (e: Exception) {
        Log.w("ReceiptsScreen", "Failed to format currency: $amount", e)
        amount.toString() // Fallback
    }
}
