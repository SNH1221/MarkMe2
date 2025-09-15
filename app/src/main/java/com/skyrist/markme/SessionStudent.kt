package com.skyrist.markme

data class SessionStudent(
    val id: String,
    val name: String = "",
    val status: String = "" // "present" / "absent"
)