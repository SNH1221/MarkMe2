package com.skyrist.markme

import com.google.firebase.Timestamp

data class SessionRecord(
    val id: String = "",
    val classId: String = "",
    val subject: String = "",
    val date: String = "",
    val timeFormatted: String = "",
    val savedAt: Timestamp? = null,
)