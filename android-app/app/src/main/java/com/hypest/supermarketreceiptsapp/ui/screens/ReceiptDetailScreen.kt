package com.hypest.supermarketreceiptsapp.ui.screens

import android.util.Log
import androidx.compose.foundation.background // Add this import
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.model.ReceiptItem
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptDetailState
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptDetailViewModel
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    receiptId: String, // Passed from navigation
    navController: NavHostController,
    viewModel: ReceiptDetailViewModel = hiltViewModel()
) {
    val state by viewModel.receiptDetailState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Implement Share action */ }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        },
        // Bottom bar for Delete/Edit buttons
        bottomBar = {
            if (state is ReceiptDetailState.Success) { // Show buttons only when receipt loaded
                ReceiptDetailBottomBar(
                    onDeleteClick = { /* TODO: Implement Delete */ viewModel.deleteReceipt() },
                    onEditClick = { /* TODO: Implement Edit */ viewModel.editReceipt() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (val currentState = state) {
                is ReceiptDetailState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReceiptDetailState.Error -> {
                    Text(
                        text = "Error: ${currentState.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                is ReceiptDetailState.Success -> {
                    ReceiptDetailContent(receipt = currentState.receipt)
                }
            }
        }
    }
}

@Composable
fun ReceiptDetailContent(receipt: Receipt) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Spacing between sections
    ) {
        // Header Section
        item {
            ReceiptDetailHeader(receipt)
        }

        // Divider
        item { HorizontalDivider() }

        // Items List Section
        items(receipt.items) { item ->
            ReceiptDetailItemRow(item)
        }

        // Divider
        item { HorizontalDivider() }

        // Summary Section
        item {
            ReceiptDetailSummary(receipt)
        }

        // Divider
        item { HorizontalDivider() }

        // Info Card Section
        item {
            ReceiptDetailInfoCard(receipt)
        }

        // Spacer to push content above bottom bar if needed (LazyColumn handles scroll)
        item { Spacer(modifier = Modifier.height(60.dp)) } // Adjust height as needed
    }
}

@Composable
fun ReceiptDetailHeader(receipt: Receipt) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top // Align items to the top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = receipt.storeName ?: "Unknown Store",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDetailDateTime(receipt.receiptDate), // Use specific format
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Assuming receipt number is not directly available, using UID or ID as placeholder
            Text(
                text = "Receipt #${receipt.uid ?: receipt.id.take(8)}", // Placeholder
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
             Text(
                text = formatCurrency(receipt.totalAmount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Category Chip (Placeholder)
            SuggestionChip(
                onClick = { /* TODO: Handle category click? */ },
                label = { Text("Groceries") } // Placeholder category
            )
        }
    }
}

@Composable
fun ReceiptDetailItemRow(item: ReceiptItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                // Format quantity and unit price if possible
                text = formatQuantityAndPrice(item.quantity, item.price),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = formatCurrency(item.price),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReceiptDetailSummary(receipt: Receipt) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Calculate Subtotal (sum of item prices)
        val subtotal = receipt.items.sumOf { it.price ?: 0.0 }
        // Calculate Tax (assuming total includes tax)
        val tax = (receipt.totalAmount ?: 0.0) - subtotal
        // TODO: Get actual tax rate if available in data model

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
            Text(formatCurrency(subtotal), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tax (Calculated)", style = MaterialTheme.typography.bodyMedium) // Placeholder label
            Text(formatCurrency(tax), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(formatCurrency(receipt.totalAmount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReceiptDetailInfoCard(receipt: Receipt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = "Store Location",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Store Location", style = MaterialTheme.typography.titleSmall)
                    Text(
                        // TODO: Add actual store location if available
                        "123 Main Street, New York, NY 10001", // Placeholder
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = "Payment Method",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Payment Method", style = MaterialTheme.typography.titleSmall)
                     Text(
                        // TODO: Add actual payment method if available
                        "Visa ending in 4589", // Placeholder
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptDetailBottomBar(onDeleteClick: () -> Unit, onEditClick: () -> Unit) {
    Surface(shadowElevation = 4.dp) { // Add elevation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface) // Match surface color
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Delete")
            }
            Button(
                onClick = onEditClick,
                modifier = Modifier.weight(1f)
            ) {
                 Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(ButtonDefaults.IconSize))
                 Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                 Text("Edit")
            }
        }
    }
}


// --- Helper Functions ---

// Specific date/time format for detail header
private fun formatDetailDateTime(isoString: String?): String {
    if (isoString == null) return "N/A"
    return try {
        val offsetDateTime = OffsetDateTime.parse(isoString)
        val zonedDateTime = offsetDateTime.atZoneSameInstant(ZoneId.systemDefault())
        // Example: Jan 15, 2025 • 2:30 PM
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy '•' h:mm a", Locale.ENGLISH)
        formatter.format(zonedDateTime)
    } catch (e: DateTimeParseException) {
        Log.w("ReceiptDetailScreen", "Failed to parse date for detail: $isoString", e)
        isoString // Fallback
    } catch (e: Exception) {
        Log.e("ReceiptDetailScreen", "Error formatting date for detail: $isoString", e)
        isoString // Fallback
    }
}

// Format quantity and unit price for item row
private fun formatQuantityAndPrice(quantity: Double?, price: Double?): String {
    if (quantity == null || price == null || quantity == 0.0) return "" // Return empty if data missing
    val unitPrice = price / quantity
    // Basic formatting, could be improved (e.g., handling whole numbers for quantity)
    return "${quantity} x ${formatCurrency(unitPrice)}"
}


// Re-use currency formatting function (could be moved to a common util file)
private fun formatCurrency(amount: Double?): String {
    if (amount == null) return "N/A"
    val format = NumberFormat.getCurrencyInstance(Locale("el", "GR")) // Use Greek locale for Euro (€)
    return try {
        format.format(amount)
    } catch (e: Exception) {
        Log.w("ReceiptDetailScreen", "Failed to format currency: $amount", e)
        amount.toString() // Fallback to plain number string
    }
}
