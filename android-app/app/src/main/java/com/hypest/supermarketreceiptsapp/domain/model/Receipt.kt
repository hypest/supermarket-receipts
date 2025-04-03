package com.hypest.supermarketreceiptsapp.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Receipt(
    val id: String, // UUID from receipts table

    @SerialName("receipt_date")
    val receiptDate: String? = null, // ISO 8601 timestamp string

    @SerialName("total_amount")
    val totalAmount: Double? = null,

    @SerialName("store_name")
    val storeName: String? = null,

    val uid: String? = null, // Government service UID

    @SerialName("created_at")
    val createdAt: String, // ISO 8601 timestamp string for when it was scanned/processed initially

    @SerialName("receipt_items") // Match the nested select name from the query
    val items: List<ReceiptItem> = emptyList() // List of items
)
