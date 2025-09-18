package com.skyrist.markme

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class SampleVerificationActivity : AppCompatActivity() {

    private val TAG = "SampleVerifyAct"
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var rvSample: RecyclerView
    private lateinit var btnDone: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView

    private var sessionKey: String = ""
    private val sampleList = mutableListOf<SampleRow>() // studentId, name, status, previewBitmap
    private lateinit var adapter: SampleAdapter

    // camera launcher for thumbnail
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val tagStudent = currentCapturingStudentId
        currentCapturingStudentId = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null && tagStudent != null) {
            val bmp = result.data!!.extras?.get("data") as? Bitmap
            if (bmp != null) {
                onCapturedBitmap(tagStudent, bmp)
            } else {
                Toast.makeText(this, "No photo captured", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // keep track which student requested capture
    private var currentCapturingStudentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample_verification)

        rvSample = findViewById(R.id.rvSample)
        btnDone = findViewById(R.id.btnDone)
        tvTitle = findViewById(R.id.tvTitleSample)
        tvSub = findViewById(R.id.tvSub)

        sessionKey = intent.getStringExtra("sessionKey") ?: ""
        if (sessionKey.isBlank()) {
            Toast.makeText(this, "Missing sessionKey", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        adapter = SampleAdapter(sampleList,
            captureCallback = { studentId -> onCaptureClicked(studentId) }
        )
        rvSample.layoutManager = LinearLayoutManager(this)
        rvSample.adapter = adapter

        btnDone.setOnClickListener { onDoneClicked() }

        loadSampleList()
    }

    private fun loadSampleList() {
        // read session doc
        db.collection("AttendanceSessions").document(sessionKey).get()
            .addOnSuccessListener { doc ->
                val sample = (doc.get("sampleNeeded") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                if (sample.isEmpty()) {
                    Toast.makeText(this, "No sample list found.", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                // fetch names for each id
                sampleList.clear()
                var left = sample.size
                for (sid in sample) {
                    db.collection("Users").document(sid).get()
                        .addOnSuccessListener { st ->
                            val name = st.getString("name") ?: sid
                            sampleList.add(SampleRow(sid, name, status = st.getString("name")?.let { "pending" } ?: "pending", null))
                        }
                        .addOnCompleteListener {
                            left--
                            if (left <= 0) {
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadSampleList failed: ${e.message}")
                Toast.makeText(this, "Failed to load sample list: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun onCaptureClicked(studentId: String) {
        // check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
            // store intent to capture after permission granted
            currentCapturingStudentId = studentId
            return
        }
        // launch camera (thumbnail mode)
        currentCapturingStudentId = studentId
        val i = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            cameraLauncher.launch(i)
        } catch (ex: Exception) {
            Log.e(TAG, "Camera launch failed: ${ex.message}")
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            currentCapturingStudentId = null
        }
    }

    // called after permission result (if capture requested before)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // retry capture for stored id
                val sid = currentCapturingStudentId
                if (sid != null) onCaptureClicked(sid)
            } else {
                Toast.makeText(this, "Camera permission required.", Toast.LENGTH_LONG).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun onCapturedBitmap(studentId: String, bmp: Bitmap) {
        // show preview immediately in UI
        val idx = sampleList.indexOfFirst { it.studentId == studentId }
        if (idx >= 0) {
            sampleList[idx].preview = bmp
            sampleList[idx].status = "uploading"
            adapter.notifyItemChanged(idx)
            // upload
            uploadBitmap(studentId, bmp, idx)
        } else {
            Log.e(TAG, "Captured for unknown studentId=$studentId")
        }
    }

    private fun uploadBitmap(studentId: String, bmp: Bitmap, idx: Int) {
        // compress thumbnail
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
        val bytes = baos.toByteArray()
        val safeSession = sessionKey.replace("/", "_")
        val safeStudent = studentId.replace("/", "_")
        val path = "attendance_photos/$safeSession/$safeStudent.jpg"
        val ref = storage.reference.child(path)

        // robust upload -> then downloadUrl -> write metadata
        val uploadTask = ref.putBytes(bytes)
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: Exception("Upload failed")
            }
            ref.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val url = task.result.toString()
                Log.d(TAG, "Uploaded $studentId -> $url")

                val meta = mapOf(
                    "url" to url,
                    "uploadedAt" to Timestamp.now(),
                    "deviceId" to "device-${android.os.Build.MODEL}"
                )
                val updates = hashMapOf<String, Any>(
                    "photos.$studentId" to meta,
                    "sampleStatus.$studentId" to "uploaded",
                    "lastSampleUploadAt" to Timestamp.now()
                )
                db.collection("AttendanceSessions").document(sessionKey)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        sampleList[idx].status = "uploaded"
                        adapter.notifyItemChanged(idx)
                    }
                    .addOnFailureListener { e ->
                        sampleList[idx].status = "meta_failed"
                        adapter.notifyItemChanged(idx)
                        Log.e(TAG, "meta save failed: ${e.message}")
                    }
            } else {
                val ex = task.exception
                sampleList[idx].status = "upload_failed"
                adapter.notifyItemChanged(idx)
                Log.e(TAG, "upload or getUrl failed", ex)
                Toast.makeText(this, "Upload failed: ${ex?.message ?: "unknown"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onDoneClicked() {
        // check if any uploads are still pending
        val still = sampleList.any { it.status == "uploading" || it.status == "pending" }
        if (still) {
            Toast.makeText(this, "Some uploads pending. Wait or retry.", Toast.LENGTH_SHORT).show()
            return
        }
        // mark verificationStatus complete (optional)
        val updates = mapOf("verificationStatus" to "uploaded", "verificationUploadedAt" to Timestamp.now())
        db.collection("AttendanceSessions").document(sessionKey)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Verification finished.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Finish failed: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // ---------------- adapter & row classes ----------------

    data class SampleRow(
        val studentId: String,
        val name: String,
        var status: String = "pending",
        var preview: Bitmap? = null
    )

    class SampleAdapter(
        private val items: List<SampleRow>,
        private val captureCallback: (String) -> Unit
    ) : RecyclerView.Adapter<SampleAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvStudentName)
            val tvId: TextView = v.findViewById(R.id.tvStudentId)
            val ivPreview: ImageView = v.findViewById(R.id.ivPreview)
            val btnCapture: Button = v.findViewById(R.id.btnCapture)
            val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_sample_student, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.tvName.text = s.name
            holder.tvId.text = s.studentId
            holder.tvStatus.text = s.status
            if (s.preview != null) {
                holder.ivPreview.setImageBitmap(s.preview)
            } else {
                holder.ivPreview.setImageResource(android.R.drawable.ic_menu_camera)
            }
            holder.btnCapture.setOnClickListener {
                captureCallback(s.studentId)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}