// EditProfileActivity.kt
package com.skyrist.markme

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var etName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etPhone: EditText
    private lateinit var etMotherName: EditText
    private lateinit var etMotherPhone: EditText
    private lateinit var etFatherName: EditText
    private lateinit var etFatherPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var progress: ProgressBar

    private var studentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile) // create this layout or reuse

        etName = findViewById(R.id.etName)
        etAddress = findViewById(R.id.etAddress)
        etPhone = findViewById(R.id.etPhone)
        etMotherName = findViewById(R.id.etMotherName)
        etMotherPhone = findViewById(R.id.etMotherPhone)
        etFatherName = findViewById(R.id.etFatherName)
        etFatherPhone = findViewById(R.id.etFatherPhone)
        btnSave = findViewById(R.id.btnSaveProfile)
        progress = findViewById(R.id.progress)

        studentId = intent.getStringExtra("studentId") ?: ""
        if (studentId.isEmpty()) finish()

        loadProfile()

        btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadProfile() {
        progress.visibility = View.VISIBLE
        db.collection("Users").document(studentId).get()
            .addOnSuccessListener { doc ->
                etName.setText(doc.getString("name") ?: "")
                etAddress.setText(doc.getString("address") ?: "")
                etPhone.setText(doc.getString("phone") ?: "")
                etMotherName.setText(doc.getString("motherName") ?: "")
                etMotherPhone.setText(doc.getString("motherPhone") ?: "")
                etFatherName.setText(doc.getString("fatherName") ?: "")
                etFatherPhone.setText(doc.getString("fatherPhone") ?: "")
                progress.visibility = View.GONE
            }
            .addOnFailureListener {
                progress.visibility = View.GONE
            }
    }

    private fun saveProfile() {
        progress.visibility = View.VISIBLE
        val map = mapOf(
            "name" to etName.text.toString(),
            "address" to etAddress.text.toString(),
            "phone" to etPhone.text.toString(),
            "motherName" to etMotherName.text.toString(),
            "motherPhone" to etMotherPhone.text.toString(),
            "fatherName" to etFatherName.text.toString(),
            "fatherPhone" to etFatherPhone.text.toString()
        )
        db.collection("Users").document(studentId)
            .set(map, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                progress.visibility = View.GONE
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
