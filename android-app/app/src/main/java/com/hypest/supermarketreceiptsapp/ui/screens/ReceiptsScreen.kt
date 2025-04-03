package com.hypest.supermarketreceiptsapp.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.model.ReceiptItem
import com.hypest.supermarketreceiptsapp.viewmodel.ReceiptsViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class) // Add OptIn for Scaffold, TopAppBar
@Composable
fun ReceiptsScreen(
    viewModel: ReceiptsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedReceipt by remember { mutableStateOf<Receipt?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Receipts") })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                uiState.receipts.isEmpty() -> {
                    Text(
                        text = "You have no receipts yet.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                else -> {
                    ReceiptsContent(
                        receipts = uiState.receipts,
                        selectedReceipt = selectedReceipt,
                        onReceiptSelected = { selectedReceipt = it }
                    )
                }
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
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault()) // Use system default timezone
        formatter.format(instant)
    } catch (e: Exception) {
        Log.w("ReceiptsScreen", "Failed to parse date: $isoString", e)
        isoString // Fallback to raw string
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
