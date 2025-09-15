package com.skyrist.markme

import androidx.recyclerview.widget.RecyclerView
import com.skyrist.markme.SharedPrefHelper
import com.skyrist.markme.StudentsAdapter

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ClassAttendanceActivity : AppCompatActivity() {

    private val TAG = "ClassAttendanceAct"

    private lateinit var rvStudents: RecyclerView
    private lateinit var tvHeader: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button
    private lateinit var etScannerInput: EditText

    private val db = FirebaseFirestore.getInstance()

    private val studentsList = mutableListOf<StudentItem>()
    private val statusMap = mutableMapOf<String, String>()
    private lateinit var adapter: StudentsAdapter

    private var classId: String = ""
    private var subjectName: String = ""
    private var teacherId: String = ""

    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_attendance)

        rvStudents = findViewById(R.id.rvStudents)
        tvHeader = findViewById(R.id.tvHeader)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSave = findViewById(R.id.btnSave)
        etScannerInput = findViewById(R.id.etScannerInput)

        classId = intent.getStringExtra("classId") ?: ""
        subjectName = intent.getStringExtra("subjectName") ?: ""
        teacherId = SharedPrefHelper.getTeacherId(this) ?: ""

        tvHeader.text = "$subjectName ($classId)"

        // IMPORTANT: pass the same statusMap to adapter
        adapter = StudentsAdapter(studentsList, statusMap, editEnabled = false)
        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.adapter = adapter

        btnStart.setOnClickListener { startAttendance() }
        btnStop.setOnClickListener { stopAttendance() }
        btnSave.setOnClickListener {
            saveAttendanceToFirestore() }

        setupScannerInput()
        loadStudentsForClass()
    }

    private fun setupScannerInput() {
        etScannerInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s == null) return
                val text = s.toString()
                if (text.contains("\n") || text.contains("\r") || text.length > 10) {
                    val raw = text.trim().replace(Regex("[\\r\\n\\t]"), "")
                    etScannerInput.setText("") // clear for next scan
                    handleScannedRaw(raw)
                }
            }
        })
    }

    private fun loadStudentsForClass() {
        studentsList.clear()
        statusMap.clear()

        db.collection("Users")
            .whereEqualTo("classId", classId)
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    Toast.makeText(this, "No students in $classId", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                for (doc in snap.documents) {
                    val id = doc.id
                    val name = doc.getString("name") ?: ""
                    val number = doc.getString("number") ?: ""
                    val rfid = doc.getString("rfid") ?: ""
                    studentsList.add(StudentItem(id, name, number, rfid))
                    statusMap[id] = "pending"
                }

                adapter.notifyDataSetChanged()
                adapter.setEditEnabled(false)
                adapter.setInitialAllPending()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed load students: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "loadStudentsForClass failed", e)
            }
    }

    private fun startAttendance() {
        if (studentsList.isEmpty()) {
            Toast.makeText(this, "No students to mark.", Toast.LENGTH_SHORT).show()
            return
        }
        scanning = true
        adapter.setEditEnabled(false)
        adapter.setInitialAllPending()
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        etScannerInput.requestFocus()
        Toast.makeText(this, "Attendance started. Scan cards to mark present.", Toast.LENGTH_SHORT).show()
    }

    private fun stopAttendance() {
        scanning = false
        adapter.markAllAbsentUnlessPresent()
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        etScannerInput.clearFocus()
        Toast.makeText(this, "Attendance stopped. Pending -> absent.", Toast.LENGTH_SHORT).show()
    }

    private fun saveAttendanceToFirestore() {
        // sanity
        if (studentsList.isEmpty()) {
            Toast.makeText(this, "No students to save.", Toast.LENGTH_SHORT).show()
            return
        }

        // disable save to prevent duplicate clicks
        btnSave.isEnabled = false

        // prepare meta
        val now = Date()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateStr = dateFmt.format(now)
        val timeStr = timeFmt.format(now)

        // attendance map from adapter: Map<studentId, { status: ..., timestamp: ... }>
        val attendanceMap = adapter.getAttendanceMap()

        // Build student lists (present / absent) from attendanceMap
        val presentList = ArrayList<String>()
        val absentList = ArrayList<String>()
        for ((studentId, info) in attendanceMap) {
            val status = (info["status"] ?: "").toString().lowercase(Locale.getDefault())
            if (status == "present") presentList.add(studentId) else absentList.add(studentId)
        }

        // Build document to save
        val attendanceDoc = hashMapOf<String, Any>(
            "classId" to classId,
            "subject" to subjectName,
            "teacherId" to teacherId,
            "date" to dateFmt.format(now),
            "time" to timeFmt.format(now),
            "students" to adapter.getAttendanceMap(),
            "presentList" to presentList,
            "absentList" to absentList,
            "savedAt" to com.google.firebase.Timestamp.now()
        )

        // write to Firestore
        db.collection("AttendanceSessions")
            .add(attendanceDoc)
            .addOnSuccessListener { ref ->
                // success: get sessionKey and open EditAttendanceActivity with extras
                val sessionKey = ref.id

                Toast.makeText(this, "Attendance saved.", Toast.LENGTH_SHORT).show()

                // prepare intent to EditAttendanceActivity
                val intent = android.content.Intent(this, EditAttendanceActivity::class.java).apply {
                    putExtra("sessionKey", sessionKey)
                    putExtra("classId", classId)
                    putExtra("subjectName", subjectName)
                    putStringArrayListExtra("present", presentList)
                    putStringArrayListExtra("absent", absentList)
                }
                startActivity(intent)

                // optionally keep save disabled or re-enable depending on UX
                btnSave.isEnabled = true
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = true
            }
    }

    // ✅ Toasts fixed here
    private fun handleScannedRaw(raw: String) {
        if (raw.isEmpty()) return
        val scanned = raw.trim().replace(Regex("[\\r\\n\\t]"), "")
        Log.d(TAG, "Scanned raw=[$raw] cleaned=[$scanned]")

        if (!scanning) {
            Log.d(TAG, "Ignoring scan - not in scanning mode")
            return
        }

        // Save present count before scan
        val beforePresent = statusMap.values.count { it == "present" }

        // call adapter and get the matched id
        val matchedId = adapter.markPresentByRfid(scanned)

        if (matchedId != null) {
            // find student by id from the list you maintain in the Activity
            val student = studentsList.find { it.id == matchedId }
            val name = student?.name ?: matchedId // fallback to id if name not found
            Toast.makeText(this, "Present $name", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Unknown card", Toast.LENGTH_SHORT).show()
        }
    }
}