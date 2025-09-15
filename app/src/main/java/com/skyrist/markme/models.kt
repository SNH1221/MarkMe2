// models.kt
package com.skyrist.markme

import com.google.firebase.Timestamp

data class UserModel(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val classId: String = "",
    val phone: String = "",
    val address: String = "",
    val motherName: String = "",
    val motherPhone: String = "",
    val fatherName: String = "",
    val fatherPhone: String = "",
    val role: String = "student",
    val password: String = ""
)




