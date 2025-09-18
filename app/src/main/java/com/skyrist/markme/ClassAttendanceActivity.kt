package com.skyrist.markme

import android.content.Intent
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
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

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

        // pass the same statusMap to the adapter so UI reflects updates
        adapter = StudentsAdapter(studentsList, statusMap, editEnabled = false)
        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.adapter = adapter

        btnStart.setOnClickListener { startAttendance() }
        btnStop.setOnClickListener { stopAttendance() }
        btnSave.setOnClickListener { saveAttendanceToFirestore() }

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
                // typical scanner sends newline; also guard against long input
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

    /**
     * Save attendance and then generate verification sampling fields in the created Firestore doc.
     * Replaces/extends earlier simple save function — creates sampleNeeded & sampleStatus after getting docId.
     */
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

        // base attendance document
        val attendanceDoc = hashMapOf<String, Any>(
            "classId" to classId,
            "subject" to subjectName,
            "teacherId" to teacherId,
            "date" to dateStr,
            "time" to timeStr,
            "students" to attendanceMap,
            "presentList" to presentList,
            "absentList" to absentList,
            "savedAt" to Timestamp.now(),
            "verificationStatus" to "pending",
            "flagged" to false
        )

        // write to Firestore
        db.collection("AttendanceSessions")
            .add(attendanceDoc)
            .addOnSuccessListener { ref ->
                val sessionKey = ref.id
                Log.d(TAG, "Attendance doc created sessionKey=$sessionKey")

                // compute sample size & pick random sample using sessionKey as seed
                val classSize = studentsList.size
                val k = pickSampleSize(classSize)
                val sample = pickRandomSample(presentList, k, sessionKey)

                // build sampleStatus map
                val sampleStatus = mutableMapOf<String, String>()
                for (s in sample) sampleStatus[s] = "pending"

                // Prepare updates to merge into the created doc
                val updates = hashMapOf<String, Any>(
                    "sampleNeeded" to sample,
                    "sampleStatus" to sampleStatus,
                    "verificationStatus" to "pending",
                    "flagged" to false,
                    "samplingGeneratedAt" to Timestamp.now()
                )

                // Merge sampling fields into same document
                db.collection("AttendanceSessions").document(sessionKey)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Sampling fields written for $sessionKey -> $sample")
                        Toast.makeText(this, "Attendance saved.", Toast.LENGTH_SHORT).show()

                        // start EditAttendanceActivity as before (send sessionKey & lists)
                        val intent = Intent(this, EditAttendanceActivity::class.java).apply {
                            putExtra("sessionKey", sessionKey)
                            putExtra("classId", classId)
                            putExtra("subjectName", subjectName)
                            putStringArrayListExtra("present", presentList)
                            putStringArrayListExtra("absent", absentList)
                        }
                        startActivity(intent)

                        btnSave.isEnabled = true
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed write sampling fields: ${e.message}")
                        Toast.makeText(this, "Saved but failed to prepare verification sample: ${e.message}", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, EditAttendanceActivity::class.java).apply {
                            putExtra("sessionKey", sessionKey)
                            putExtra("classId", classId)
                            putExtra("subjectName", subjectName)
                            putStringArrayListExtra("present", presentList)
                            putStringArrayListExtra("absent", absentList)
                        }
                        startActivity(intent)
                        btnSave.isEnabled = true
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "saveAttendanceToFirestore failed", e)
                btnSave.isEnabled = true
            }
    }

    // handle a scanned raw string from the scanner or keyboard input
    private fun handleScannedRaw(raw: String) {
        if (raw.isEmpty()) return
        val scanned = raw.trim().replace(Regex("[\\r\\n\\t]"), "")
        Log.d(TAG, "Scanned raw=[$raw] cleaned=[$scanned]")

        if (!scanning) {
            Log.d(TAG, "Ignoring scan - not in scanning mode")
            return
        }

        // record present ids before marking
        val beforePresentIds = statusMap.filterValues { it == "present" }.keys.toMutableSet()

        // call adapter to attempt to mark present by rfid (adapter will update statusMap)
        // adapter.markPresentByRfid(...) originally didn't return id in some versions,
        // so we rely on statusMap diff to find which id became present.
        adapter.markPresentByRfid(scanned)

        // also try digits-only variant in case scanner sends extra characters
        val digits = scanned.replace(Regex("[^0-9]"), "")
        if (digits != scanned && digits.isNotEmpty()) {
            adapter.markPresentByRfid(digits)
        }

        // after attempt, compute new present ids
        val afterPresentIds = statusMap.filterValues { it == "present" }.keys.toMutableSet()

        val newlyMarked = afterPresentIds.subtract(beforePresentIds)
        if (newlyMarked.isNotEmpty()) {
            // get the last one (or any) — typically one card scan marks one student
            val newId = newlyMarked.last()
            val student = studentsList.find { it.id == newId }
            val name = student?.name ?: newId
            Toast.makeText(this, "Present: $name", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Marked present id=$newId name=$name")
        } else {
            Toast.makeText(this, "Unknown card / no match", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "No student matched scanned value [$scanned]")
        }
    }

    // pick sample size based on class size
    private fun pickSampleSize(classSize: Int): Int {
        return when {
            classSize <= 30 -> 3
            classSize <= 60 -> 5
            else -> maxOf(8, (classSize / 10)) // ~10% or at least 8
        }
    }

    // pick k random students from present list using sessionSeed (docId) for variation
    private fun pickRandomSample(presentList: List<String>, k: Int, sessionSeed: String): List<String> {
        if (presentList.size <= k) return presentList
        val seed = sessionSeed.hashCode().toLong() xor System.currentTimeMillis()
        return presentList.shuffled(Random(seed)).take(k)
    }

    override fun onDestroy() {
        super.onDestroy()
        // if you registered any receivers or listeners, unregister here (none in this file)
    }
}