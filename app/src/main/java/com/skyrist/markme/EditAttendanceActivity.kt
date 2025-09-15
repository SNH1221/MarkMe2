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

        // roster load करके switch set करें
        loadRows(present.toSet(), absent.toSet())

        btnConfirm.setOnClickListener { saveFinal() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun loadRows(present: Set<String>, absent: Set<String>) {
        // class roster
        db.collection("Classes").document(classId).get()
            .addOnSuccessListener { d ->
                val nums = (d.get("students") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (nums.isEmpty()) { toast("No students in $classId"); return@addOnSuccessListener }

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
                            if (--left == 0) adapter.notifyDataSetChanged()
                        }
                }
            }
            .addOnFailureListener { toast("Load failed: ${it.message}") }
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

        // Save using the docId we have/created
        db.collection("AttendanceSessions").document(docId)
            .set(data)
            .addOnSuccessListener {
                toast("Attendance saved")
                Log.d(TAG, "Attendance saved: session=$docId present=${presentList.size} absent=${absentList.size}")

                // --- SEND SMS TO PARENTS FOR ABSENTEES ---
                // Make sure AttendanceSender is implemented (ApiClient + SmsApi etc.)
                if (absentList.isNotEmpty()) {
                    val message = "Your child was marked ABSENT for $subjectName ($classId) on ${now.toDate()}."
                    Log.d(TAG, "Calling AttendanceSender for absentees: $absentList")
                    AttendanceSender.sendForAbsentees(absentList, message)
                } else {
                    Log.d(TAG, "No absentees to notify.")
                }

                // --- OPEN EDIT ATTENDANCE SCREEN for final edits (if you want) ---
                // If you want the "Edit attendance" window immediately after save (so teacher can review),
                // start an activity (SessionDetailActivity or an Edit screen) and pass present/absent lists.
                // Replace SessionDetailActivity below with your edit screen if different.
                val i = Intent(this, SessionDetailActivity::class.java).apply {
                    putExtra("sessionId", docId)
                }
                startActivity(i)

                // finish current edit activity
                finish()
            }
            .addOnFailureListener { e ->
                toast("Save failed: ${e.message}")
                Log.e(TAG, "Save failed", e)
            }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}