package com.hypest.supermarketreceiptsapp.domain.model

import kotlinx.serialization.Serializable

@Serializable // For potential use with Supabase serialization
data class Receipt(
    val id: Int? = null, // Assuming auto-incrementing ID from Supabase
    val url: String,
    val user_id: String // To associate with the logged-in user
    // Add timestamp or other fields if needed
)
