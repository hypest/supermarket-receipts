package com.hypest.supermarketreceiptsapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptItem(
    val id: String? = null, // UUID - Make nullable as it's not stored locally
    val name: String,
    val quantity: Double, // Using Double for numeric
    val price: Double, // Using Double for numeric(10, 2) - This is the total price
    val unitPrice: Double? = null // Add nullable unit price
    // created_at is not needed for display usually
)
