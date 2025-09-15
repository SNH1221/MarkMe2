package com.skyrist.markme.ui.chat

data class ChatMessage(
    val text: String,
    val fromUser: Boolean = false
)