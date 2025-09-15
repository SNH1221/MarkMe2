package com.skyrist.markme

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var etNumber: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        etNumber = findViewById(R.id.etAdminNumber)
        etPassword = findViewById(R.id.etAdminPassword)
        btnLogin = findViewById(R.id.btnAdminLogin)

        btnLogin.setOnClickListener {
            val number = etNumber.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (number.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginTeacher(number, password)
        }
    }

    private fun loginTeacher(number: String, password: String) {
        db.collection("Users").document(number).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "No user found with number: $number", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val dbPassword = doc.getString("password") ?: ""
                val role = doc.getString("role") ?: ""

                if (dbPassword == password && role.equals("teacher", ignoreCase = true)) {

                    SharedPrefHelper.setTeacherId(this, number) // or the real teacher id read from Firestore

                    val i = Intent(this, TeacherDashboardActivity::class.java).apply {
                        putExtra("number", number)
                        putExtra("name", doc.getString("name") ?: "")
                    }
                    startActivity(i)
                    finish()
                } else if (dbPassword == password && role.equals("student", ignoreCase = true)) {
                    Toast.makeText(this, "Student account! Use Student Login.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Invalid Teacher Credentials", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
