package com.hypest.supermarketreceiptsapp.domain.model

import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable // For potential use with Supabase serialization
data class Receipt(
    val id: Long? = null, // Use Long for bigint/int8 from Supabase
    val url: String,
    val user_id: String, // To associate with the logged-in user
    @SerialName("html_content") // Match potential snake_case column name in DB
    val htmlContent: String? = null // Add nullable HTML content field
    // Add timestamp or other fields if needed
)
