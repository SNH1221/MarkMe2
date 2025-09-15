package com.skyrist.markme

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class StudentDashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvHeaderName: TextView
    private lateinit var tvHeaderClass: TextView
    private lateinit var btnEditProfile: TextView
    private lateinit var pieChart: PieChart
    private lateinit var tvLegend: TextView

    private var listener: ListenerRegistration? = null
    private var studentId: String = ""
    private var studentClassId: String = ""
    private var studentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        tvHeaderName = findViewById(R.id.tvHeaderName)
        tvHeaderClass = findViewById(R.id.tvHeaderClass)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        pieChart = findViewById(R.id.pieChartAttendance)
        tvLegend = findViewById(R.id.tvLegend)

        // configure basic pie chart look
        setupPieChart()

        // the login should pass student id in intent under "studentId"
        studentId = intent.getStringExtra("studentId") ?: intent.getStringExtra("number") ?: ""
        if (studentId.isEmpty()) {
            // fallback: show blank or finish
            finish(); return
        }

        btnEditProfile.setOnClickListener {
            val i = Intent(this, EditProfileActivity::class.java)
            i.putExtra("studentId", studentId)
            startActivity(i)
        }

        loadUserAndStartListening(studentId)
    }

    private fun setupPieChart() {
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.setDrawHoleEnabled(true)
        pieChart.holeRadius = 55f
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        pieChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        pieChart.legend.orientation = Legend.LegendOrientation.HORIZONTAL
        pieChart.legend.isEnabled = true
    }

    private fun loadUserAndStartListening(sid: String) {
        db.collection("Users").document(sid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    finish(); return@addOnSuccessListener
                }
                studentName = doc.getString("name") ?: sid
                studentClassId = doc.getString("classId") ?: ""
                tvHeaderName.text = studentName
                tvHeaderClass.text = "Class: ${studentClassId.ifEmpty { "-" }}"

                // start realtime listener to compute attendance stats
                startAttendanceListener()
            }
            .addOnFailureListener {
                // handle if needed
            }
    }

    private fun startAttendanceListener() {
        // remove previous if any
        listener?.remove()

        // We will listen to sessions for this student's class (if classId is set).
        // If classId is empty, listen to all sessions and filter by presence of studentId.
        val baseCollection = db.collection("AttendanceSessions")
        val query: Query = if (studentClassId.isNotEmpty()) {
            baseCollection.whereEqualTo("classId", studentClassId)
                .orderBy("savedAt", Query.Direction.DESCENDING)
        } else {
            baseCollection.orderBy("savedAt", Query.Direction.DESCENDING)
        }

        listener = query.addSnapshotListener { snap, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            var totalSessions = 0
            var presentCount = 0

            if (snap != null) {
                for (d in snap.documents) {
                    // count only sessions where student was included (either present or absent),
                    // but if classId is used the session belongs to same class.
                    val presentAny = d.get("present")
                    val absenteesAny = d.get("absentees")

                    // If session has present list (array)
                    if (presentAny is List<*>) {
                        // If student is in either present or absentees we should count session.
                        val isPresent = presentAny.any { it?.toString() == studentId }
                        val counted = isPresent || (absenteesAny is List<*> && absenteesAny.any { it?.toString() == studentId })
                        if (counted) {
                            totalSessions++
                            if (isPresent) presentCount++
                        } else {
                            // if neither list contains student, maybe session was for other groups -> skip
                            // this avoids counting unrelated sessions when classId is empty
                        }
                    } else if (presentAny is Map<*, *>) {
                        // old format: map of id->something
                        val isPresent = presentAny.keys.any { it?.toString() == studentId }
                        val counted = isPresent || (absenteesAny is List<*> && absenteesAny.any { it?.toString() == studentId })
                        if (counted) {
                            totalSessions++
                            if (isPresent) presentCount++
                        }
                    } else {
                        // fallback: maybe present stored as array under "present"
                        if (absenteesAny is List<*>) {
                            val isAbsent = absenteesAny.any { it?.toString() == studentId }
                            if (isAbsent) {
                                totalSessions++
                                // absent -> presentCount unchanged
                            } else {
                                // check present array missing: maybe presence saved in another field -> try "present" string list earlier
                                val presentList = d.get("present") as? List<*>
                                val isPresent = presentList?.any { it?.toString() == studentId } ?: false
                                if (isPresent) {
                                    totalSessions++; presentCount++
                                }
                            }
                        } else {
                            // nothing known -> try simple contains in 'present' string field
                            val p = d.getString("present")
                            if (!p.isNullOrEmpty() && p.contains(studentId)) {
                                totalSessions++; presentCount++
                            }
                        }
                    }
                }
            }

            val absentCount = totalSessions - presentCount
            updateChart(presentCount, absentCount)
        }
    }

    private fun updateChart(present: Int, absent: Int) {
        val entries = ArrayList<PieEntry>()
        val total = present + absent
        if (total == 0) {
            // show empty state as 0 present
            entries.add(PieEntry(1f, "No data"))
            val set = PieDataSet(entries, "")
            set.colors = listOf(Color.LTGRAY)
            set.valueTextSize = 14f
            val pd = PieData(set)
            pieChart.data = pd
            pieChart.centerText = "No data"
            pieChart.invalidate()
            return
        }

        entries.add(PieEntry(present.toFloat(), "Present"))
        entries.add(PieEntry(absent.toFloat(), "Absent"))

        val set = PieDataSet(entries, "Attendance")
        set.sliceSpace = 3f
        set.selectionShift = 5f
        set.colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336")) // green, red
        set.valueTextSize = 14f

        val data = PieData(set)
        data.setValueTextSize(12f)
        data.setValueTextColor(Color.BLACK)

        pieChart.data = data
        val percent = if (total > 0) (present * 100f / total) else 0f
        pieChart.centerText = "Present\n${"%.1f".format(percent)}%"
        pieChart.animateY(600, Easing.EaseInOutQuad)
        pieChart.invalidate()

        // update legend text if you want
        tvLegend.text = "Present: $present    Absent: $absent"
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
