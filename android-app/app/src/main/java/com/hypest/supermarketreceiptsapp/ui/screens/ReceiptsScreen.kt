package com.hypest.supermarketreceiptsapp.ui.screens

import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility // Keep specific import
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.ui.components.HtmlExtractorWebView
import com.hypest.supermarketreceiptsapp.ui.components.QrCodeScanner
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsListState
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsScreenState
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    receiptsViewModel: ReceiptsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(), // Inject AuthViewModel for logout
    onReceiptClick: (String) -> Unit = {} // Callback for clicking a receipt (e.g., navigate to detail)
) {
    val receiptsListState by receiptsViewModel.receiptsListState.collectAsStateWithLifecycle()
    val receiptsScreenState by receiptsViewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Date") } // Track selected filter

    // --- Side Effects from ViewModel ---

    // Launch scanner when state becomes Scanning
    if (receiptsScreenState == ReceiptsScreenState.Scanning) {
        QrCodeScanner(
            onQrCodeScanned = { url -> receiptsViewModel.onQrCodeScanned(url) },
            onScanCancelled = { receiptsViewModel.onScanCancelled() },
            onScanError = { exception -> receiptsViewModel.onScanError(exception) }
        )
    }

    // --- UI ---

    Scaffold(
        topBar = {
            // TopAppBar is ignored for now per instructions
        },
        floatingActionButton = {
            // Keep FAB as requested
            val showFab = receiptsScreenState !is ReceiptsScreenState.Scanning // Show unless actively scanning
            if (showFab) {
                FloatingActionButton(onClick = { receiptsViewModel.startScanning() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Scan New Receipt")
                }
            }
        }
    ) { paddingValues ->
        // Use Column for overall layout: Search -> Filters -> List
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
        ) {
            // 1. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it /* TODO: Implement search logic */ },
                label = { Text("Search receipts") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp) // Rounded corners like the design
            )

            // 2. Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "Date",
                    onClick = { selectedFilter = "Date" /* TODO: Implement filter logic */ },
                    label = { Text("Date") },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Filter by Date") }
                )
                FilterChip(
                    selected = selectedFilter == "Store",
                    onClick = { selectedFilter = "Store" /* TODO: Implement filter logic */ },
                    label = { Text("Store") },
                    leadingIcon = { Icon(Icons.Filled.Store, contentDescription = "Filter by Store") }
                )
                FilterChip(
                    selected = selectedFilter == "Category",
                    onClick = { selectedFilter = "Category" /* TODO: Implement filter logic */ },
                    label = { Text("Category") },
                    leadingIcon = { Icon(Icons.Filled.Category, contentDescription = "Filter by Category") }
                )
            }

            // 3. Receipts List Area and Overlays
            Box(modifier = Modifier.fillMaxSize()) { // Base Box for content
                // Always display the list content underneath, potentially blurred
                val currentState = receiptsScreenState // Read state value into local variable for stability
                val listModifier = Modifier.then(
                    // Apply blur only during HTML extraction overlay if supported
                    if (currentState is ReceiptsScreenState.ExtractingHtml &&
                        currentState.showOverlay &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        Modifier.blur(radius = 8.dp)
                    } else Modifier
                )
                ReceiptsListContent(
                    receiptsUiState = receiptsListState,
                    modifier = listModifier,
                    onReceiptClick = onReceiptClick // Pass click handler
                )

                // --- Overlays --- Render conditionally on top ---

                // Scanner Scrim
                if (currentState == ReceiptsScreenState.Scanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Scanner Active",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                // HTML Extraction WebView and Overlay (shown only in ExtractingHtml state)
                if (currentState is ReceiptsScreenState.ExtractingHtml) {
                    // Box to contain WebView and AnimatedVisibility overlay
                    Box(modifier = Modifier.fillMaxSize()) {
                        HtmlExtractorWebView(
                            url = currentState.url,
                            onHtmlExtracted = { url, html -> receiptsViewModel.onHtmlExtracted(url, html) },
                            onError = { url, error -> receiptsViewModel.onHtmlExtractionError(url, error) },
                            onChallengeInteractionRequired = { receiptsViewModel.onChallengeInteractionRequired() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // AnimatedVisibility for the overlay - Use fully qualified name
                        androidx.compose.animation.AnimatedVisibility(
                            visible = currentState.showOverlay,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            // Content of the overlay (Box with Column)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
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

                // Processing Overlay
                if (currentState is ReceiptsScreenState.Processing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Processing receipt...")
                        }
                    }
                }

                // Error Overlay
                if (currentState is ReceiptsScreenState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                "Error: ${currentState.message}",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { receiptsViewModel.resetToReadyState() }) {
                                Text("Try Again")
                            }
                        }
                    }
                }

                // Success State Handling (using LaunchedEffect, no visual overlay needed here)
                if (currentState == ReceiptsScreenState.Success) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(1500) // Short delay before resetting
                        receiptsViewModel.resetToReadyState()
                    }
                    // TODO: Consider showing a Snackbar for "Receipt Processed Successfully!"
                }
            }
        }
    }
}

// Group receipts by date ("Today", "Yesterday", "Older")
private fun groupReceiptsByDate(receipts: List<Receipt>): Map<String, List<Receipt>> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    return receipts
        .sortedByDescending { it.receiptDate } // Sort by date descending first
        .groupBy { receipt ->
            try {
                val receiptDate = OffsetDateTime.parse(receipt.receiptDate)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate()

                when {
                    receiptDate.isEqual(today) -> "Today"
                    receiptDate.isEqual(yesterday) -> "Yesterday"
                    else -> {
                        // Format older dates, e.g., "February 14" or "2024-02-14"
                        receiptDate.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)) // Example: February 14
                    }
                }
            } catch (e: Exception) {
                Log.w("ReceiptsScreen", "Failed to parse or group date: ${receipt.receiptDate}", e)
                "Unknown Date" // Group items with invalid dates separately
            }
        }
}


// Updated composable for the receipts list area
@Composable
fun ReceiptsListContent(
    receiptsUiState: ReceiptsListState,
    modifier: Modifier = Modifier, // Allow passing modifiers (e.g., for blur)
    onReceiptClick: (String) -> Unit // Use receipt ID for click action
) {
    Box(modifier = modifier.fillMaxSize()) { // Apply modifier here
        when {
            // Initial loading state (empty list)
            receiptsUiState.isLoading && receiptsUiState.receipts.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            // Initial error state (empty list)
            receiptsUiState.error != null && receiptsUiState.receipts.isEmpty() -> {
                Text(
                    text = "Error loading receipts: ${receiptsUiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            // Empty list state (after loading, no receipts)
            receiptsUiState.receipts.isEmpty() && !receiptsUiState.isLoading -> {
                Text(
                    text = "You have no receipts yet. Scan one using the '+' button!",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            // List has receipts
            else -> {
                val groupedReceipts = remember(receiptsUiState.receipts) {
                    groupReceiptsByDate(receiptsUiState.receipts)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between items/headers
                ) {
                    // Show loading indicator subtly at the top if loading more/refreshing
                    if (receiptsUiState.isLoading) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    groupedReceipts.forEach { (dateHeader, receiptsInGroup) ->
                        // Date Header
                        item {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.titleSmall, // Or bodySmall
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp) // Add padding around header
                            )
                        }
                        // Receipt Items for this group
                        items(receiptsInGroup, key = { it.id }) { receipt ->
                            ReceiptListItem(
                                receipt = receipt,
                                onClick = { onReceiptClick(receipt.id) } // Pass ID on click
                            )
                        }
                    }
                }
            }
        }
    }
}

// Updated ReceiptListItem to match the design
@Composable
fun ReceiptListItem(
    receipt: Receipt,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), // More rounded corners like design
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Use surface color, elevation adds distinction
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Subtle elevation
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp) // Adjust padding
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically // Align items vertically
        ) {
            // Left Column: Store, Category, Date
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = receipt.storeName ?: "Unknown Store",
                    fontWeight = FontWeight.Bold, // Bold store name
                    fontSize = 16.sp, // Slightly larger font
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp)) // Small space
                Text(
                    // TODO: Replace with actual category when available
                    text = "Category Placeholder", // Placeholder
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                 Spacer(modifier = Modifier.height(4.dp)) // More space
                Text(
                    text = formatDisplayDateTime(receipt.receiptDate), // Use new date format
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Right Column: Amount and Chevron
            Row(verticalAlignment = Alignment.CenterVertically) {
                 Text(
                    text = formatCurrency(receipt.totalAmount),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, // Larger amount font
                    modifier = Modifier.padding(end = 8.dp) // Space before chevron
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant // Subtle tint
                )
            }
        }
    }
}

// --- Helper Functions ---

// Updated date formatting function for the list item
private fun formatDisplayDateTime(isoString: String?): String {
    if (isoString == null) return "N/A"
    return try {
        val offsetDateTime = OffsetDateTime.parse(isoString)
        val zonedDateTime = offsetDateTime.atZoneSameInstant(ZoneId.systemDefault())
        val today = LocalDate.now(ZoneId.systemDefault())
        val yesterday = today.minusDays(1)
        val receiptDate = zonedDateTime.toLocalDate()

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH) // e.g., 2:30 PM

        when {
            receiptDate.isEqual(today) -> "Today, ${zonedDateTime.format(timeFormatter)}"
            receiptDate.isEqual(yesterday) -> "Yesterday, ${zonedDateTime.format(timeFormatter)}"
            else -> {
                // Example: Feb 14, 4:45 PM
                val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                "${receiptDate.format(dateFormatter)}, ${zonedDateTime.format(timeFormatter)}"
            }
        }
    } catch (e: DateTimeParseException) {
        Log.w("ReceiptsScreen", "Failed to parse date for display: $isoString", e)
        isoString // Fallback
    } catch (e: Exception) {
        Log.e("ReceiptsScreen", "Error formatting date for display: $isoString", e)
        isoString // Fallback
    }
}


// Updated formatCurrency to use Greek locale
private fun formatCurrency(amount: Double?): String {
    if (amount == null) return "N/A"
    val format = NumberFormat.getCurrencyInstance(Locale("el", "GR")) // Use Greek locale for Euro (€)
    return try {
        format.format(amount)
    } catch (e: Exception) {
        Log.w("ReceiptsScreen", "Failed to format currency: $amount", e)
        amount.toString() // Fallback to plain number string
    }
}
