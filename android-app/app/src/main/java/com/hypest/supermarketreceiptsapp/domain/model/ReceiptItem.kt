package com.hypest.supermarketreceiptsapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptItem(
    val id: String, // UUID
    val name: String,
    val quantity: Double, // Using Double for numeric
    val price: Double // Using Double for numeric(10, 2)
    // created_at is not needed for display usually
)
