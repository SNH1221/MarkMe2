package com.skyrist.markme.network

data class SmsResponse(
    val success: Boolean,
    val sid: String? = null,
    val error: String? = null
)