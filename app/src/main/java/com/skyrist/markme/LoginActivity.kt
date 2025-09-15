package com.skyrist.markme

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var etNumber: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvAdminLink: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etNumber = findViewById(R.id.etNumber)
        etPassword = findViewById(R.id.etPassword)
        btnLogin   = findViewById(R.id.btnLogin)
        tvAdminLink = findViewById(R.id.tvAdminLink)

        btnLogin.setOnClickListener {
            val number = etNumber.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (number.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginStudent(number, password)
        }

        tvAdminLink.setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }
    }

    private fun loginStudent(number: String, password: String) {
        // number == documentId (e.g., "1001")
        db.collection("Users").document(number).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "No user found with number: $number", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val dbPassword = doc.getString("password") ?: ""
                val role = doc.getString("role") ?: ""

                if (dbPassword == password && role.equals("student", ignoreCase = true)) {
                    // Pass a few details forward if needed
                    val i = Intent(this, StudentDashboardActivity::class.java).apply {
                        putExtra("studentId", number)
                        putExtra("name", doc.getString("name") ?: "")
                        putExtra("classId", doc.getString("classId") ?: "")
                    }
                    startActivity(i)
                    finish()
                } else if (dbPassword == password && role.equals("teacher", ignoreCase = true)) {
                    Toast.makeText(this, "Teacher account! Use Admin Login.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Invalid Student Credentials", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
