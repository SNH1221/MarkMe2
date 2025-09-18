package com.skyrist.markme

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.skyrist.markme.network.AttendanceSender
import kotlin.random.Random

class EditAttendanceActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "EditAttendanceActivity"

    private lateinit var tvTitle: TextView
    private lateinit var rv: RecyclerView
    private lateinit var btnConfirm: Button
    private lateinit var btnCancel: Button

    private lateinit var adapter: EditAttendanceAdapter
    private val rows = mutableListOf<EditRow>()

    private var sessionKey = ""
    private var classId = ""
    private var subjectName = ""
    private var teacherId = ""   // optional

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_attendance)

        tvTitle = findViewById(R.id.tvTitle)
        rv = findViewById(R.id.rvEdit)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnCancel = findViewById(R.id.btnCancel)

        sessionKey = intent.getStringExtra("sessionKey") ?: ""
        classId = intent.getStringExtra("classId") ?: ""
        subjectName = intent.getStringExtra("subjectName") ?: ""
        teacherId = intent.getStringExtra("teacherId") ?: ""

        val present = intent.getStringArrayListExtra("present") ?: arrayListOf()
        val absent  = intent.getStringArrayListExtra("absent") ?: arrayListOf()

        tvTitle.text = "Edit • $subjectName ($classId)"

        adapter = EditAttendanceAdapter(rows)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnConfirm.setOnClickListener { saveFinal() }
        btnCancel.setOnClickListener { finish() }

        // load roster and the session's present/absent state:
        loadRows(present.toSet(), absent.toSet())
    }

    private fun loadRows(present: Set<String>, absent: Set<String>) {
        // load class roster from Classes collection
        db.collection("Classes").document(classId).get()
            .addOnSuccessListener { d ->
                val nums = (d.get("students") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (nums.isEmpty()) {
                    toast("No students in $classId")
                    return@addOnSuccessListener
                }

                rows.clear()
                var left = nums.size
                nums.forEach { roll ->
                    db.collection("Users").document(roll).get()
                        .addOnSuccessListener { u ->
                            val name = u.getString("name") ?: roll
                            val isPresent = when {
                                present.contains(roll) -> true
                                absent.contains(roll)  -> false
                                else -> false
                            }
                            rows.add(EditRow(roll, name, isPresent))
                        }
                        .addOnCompleteListener {
                            if (--left == 0) {
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                toast("Load failed: ${e.message}")
                Log.e(TAG, "loadRows failed", e)
            }
    }

    private fun saveFinal() {
        // get lists from adapter
        val presentList = adapter.presentList()
        val absentList  = adapter.absentList()

        // ensure we have a session key (document id). If empty, generate one.
        var docId = sessionKey
        if (docId.isBlank()) {
            docId = db.collection("AttendanceSessions").document().id
            Log.d(TAG, "Generated new sessionKey = $docId")
        }

        val now = Timestamp.now()
        val data = hashMapOf(
            "sessionKey" to docId,
            "classId" to classId,
            "subjectName" to subjectName,
            "present" to presentList,
            "absentees" to absentList,
            "finalized" to true,
            "savedAt" to now,
            "teacherId" to teacherId
        )

        // Save attendance doc
        db.collection("AttendanceSessions").document(docId)
            .set(data)
            .addOnSuccessListener {
                toast("Attendance saved")
                Log.d(TAG, "Attendance saved: session=$docId present=${presentList.size} absent=${absentList.size}")

                // --- SEND SMS TO PARENTS FOR ABSENTEES (if implemented) ---
                if (absentList.isNotEmpty()) {
                    val message = "Your child was marked ABSENT for $subjectName ($classId) on ${now.toDate()}."
                    Log.d(TAG, "Calling AttendanceSender for absentees: $absentList")
                    try {
                        AttendanceSender.sendForAbsentees(absentList, message)
                    } catch (ex: Exception) {
                        Log.e(TAG, "AttendanceSender error: ${ex.message}")
                    }
                } else {
                    Log.d(TAG, "No absentees to notify.")
                }

                // --- NOW ENSURE sampleNeeded EXISTS (if not, create it) ---
                ensureSampleAndProceed(docId, presentList)
            }
            .addOnFailureListener { e ->
                toast("Save failed: ${e.message}")
                Log.e(TAG, "Save failed", e)
            }
    }

    /**
     * Ensure the attendance session doc contains sampleNeeded & sampleStatus.
     * If already present, just launch verification. Otherwise generate sample and merge it.
     */
    private fun ensureSampleAndProceed(docId: String, presentList: List<String>) {
        Log.d(TAG, "Ensuring sampleNeeded for $docId")
        db.collection("AttendanceSessions").document(docId).get()
            .addOnSuccessListener { doc ->
                val existingSample = (doc.get("sampleNeeded") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                if (existingSample.isNotEmpty()) {
                    Log.d(TAG, "sampleNeeded already present: $existingSample")
                    // launch verification directly
                    launchVerification(docId)
                    return@addOnSuccessListener
                }

                // compute sample size using roster size (rows)
                val classSize = rows.size.takeIf { it > 0 } ?: presentList.size
                val k = pickSampleSize(classSize)
                val sample = pickRandomSample(presentList, k, docId)

                // build sampleStatus map (all pending)
                val sampleStatus = mutableMapOf<String, String>()
                for (s in sample) sampleStatus[s] = "pending"

                val updates = hashMapOf<String, Any>(
                    "sampleNeeded" to sample,
                    "sampleStatus" to sampleStatus,
                    "verificationStatus" to "pending",
                    "samplingGeneratedAt" to Timestamp.now()
                )

                db.collection("AttendanceSessions").document(docId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "sampleNeeded created for $docId -> $sample")
                        toast("Verification sample generated (${sample.size})")
                        launchVerification(docId)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to write sampleNeeded: ${e.message}")
                        toast("Saved but sample generation failed: ${e.message}")
                        // fallback: open session detail
                        openSessionDetail(docId)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to reload session after save: ${e.message}")
                toast("Saved but cannot verify: ${e.message}")
                openSessionDetail(docId)
            }
    }

    private fun launchVerification(docId: String) {
        val i = Intent(this, SampleVerificationActivity::class.java).apply {
            putExtra("sessionKey", docId)
        }
        startActivity(i)
        finish()
    }

    private fun openSessionDetail(docId: String) {
        val i = Intent(this, SessionDetailActivity::class.java).apply {
            putExtra("sessionId", docId)
        }
        startActivity(i)
        finish()
    }

    // Choose sample size based on class size
    private fun pickSampleSize(classSize: Int): Int {
        return when {
            classSize <= 20 -> 2
            classSize <= 30 -> 3
            classSize <= 60 -> 5
            else -> maxOf(8, classSize / 10) // ~10% or at least 8
        }
    }

    // Pick k random studentIds from presentList deterministically-ish using session seed
    private fun pickRandomSample(presentList: List<String>, k: Int, sessionSeed: String): List<String> {
        if (presentList.size <= k) return presentList
        val seed = sessionSeed.hashCode().toLong() xor System.currentTimeMillis()
        return presentList.shuffled(Random(seed)).take(k)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}