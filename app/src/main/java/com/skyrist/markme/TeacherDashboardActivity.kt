package com.skyrist.markme

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.skyrist.markme.ui.ChatbotActivity
import de.hdodenhof.circleimageview.CircleImageView
import java.util.*

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var ivProfile: CircleImageView
    private lateinit var tvWelcome: TextView
    private lateinit var btnEditProfile: ImageButton
    private lateinit var btnClasses: Button
    private lateinit var btnStudents: Button
    private lateinit var btnRecords: Button
    private lateinit var btnChatbot: Button
    private lateinit var btnPortal: Button

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val teacherId = "2001" // replace dynamically from login if available

    // Uri for captured photo
    private var photoUri: Uri? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() else Toast.makeText(this, "Camera denied", Toast.LENGTH_SHORT).show()
    }

    private val pickGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uri = data?.data
            if (uri != null) uploadProfileToStorage(uri)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoUri?.let { uploadProfileToStorage(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)

        ivProfile = findViewById(R.id.ivProfile)
        tvWelcome = findViewById(R.id.tvWelcome)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnClasses = findViewById(R.id.btnClasses)
        btnStudents = findViewById(R.id.btnStudents)
        btnRecords = findViewById(R.id.btnRecords)
        btnChatbot = findViewById(R.id.btnChatbot)
        btnPortal = findViewById(R.id.btnPortal)

        // Load teacher basic data from Firestore (example)
        loadTeacherInfo()

        btnEditProfile.setOnClickListener { showImagePickDialog() }

        btnClasses.setOnClickListener {
            // load classes fragment or list into contentContainer
            supportFragmentManager.commit {
                replace(R.id.contentContainer, ClassesListFragment.newInstance(teacherId))
            }
        }

        btnStudents.setOnClickListener {
            supportFragmentManager.commit {
                replace(R.id.contentContainer, StudentsListFragment.newInstance())
            }
        }


        btnRecords.setOnClickListener {
            val i = Intent(this, AttendanceRecordsActivity::class.java)
            startActivity(i)
        }



        btnChatbot.setOnClickListener {
            val i = Intent(this, ChatbotActivity::class.java)
            i.putExtra("classId", "CSE101") // pass the class id the teacher is seeing
            startActivity(i)

        }

        btnPortal.setOnClickListener {
            // short visual feedback
            Toast.makeText(this, "Attendance sent to Government portal", Toast.LENGTH_SHORT).show()

            // optional: disable button for a while so user doesn't press multiple times
            btnPortal.isEnabled = false

            // optional: re-enable after 2s (demo)
            btnPortal.postDelayed({ btnPortal.isEnabled = true},2000L)

        }


    }

    private fun loadTeacherInfo() {
        // read teacher doc and populate ui
        db.collection("Users").document(teacherId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: "Teacher"
                    val number = doc.getString("number") ?: teacherId
                    val photo = doc.getString("photoUrl")
                    tvWelcome.text = "Welcome, $name ($number)"
                    if (!photo.isNullOrEmpty()) {
                        Glide.with(this).load(photo).into(ivProfile)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load teacher", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showImagePickDialog() {
        val options = arrayOf("Pick from Gallery", "Take Photo")
        AlertDialog.Builder(this)
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }.show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickGalleryLauncher.launch(intent)
    }

    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "temp_pic_" + System.currentTimeMillis())
        photoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        takePhotoLauncher.launch(cameraIntent)
    }

    private fun uploadProfileToStorage(uri: Uri) {
        val fileRef = storage.reference.child("profile_photos/${teacherId}_${UUID.randomUUID()}.jpg")
        val upload = fileRef.putFile(uri)
        upload.addOnSuccessListener {
            fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                // save URL to Firestore User document
                db.collection("Users").document(teacherId)
                    .update("photoUrl", downloadUri.toString())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                        Glide.with(this).load(downloadUri).into(ivProfile)
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed save URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
