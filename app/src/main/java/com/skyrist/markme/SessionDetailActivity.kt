package com.skyrist.markme

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class SessionDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvStudents: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView

    private val students = mutableListOf<SessionStudent>()
    private lateinit var adapter: SessionStudentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail) // create layout with recycler/progress/empty

        rvStudents = findViewById(R.id.rvStudents)
        progress = findViewById(R.id.progress)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = SessionStudentAdapter(students)
        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.adapter = adapter

        val sessionId = intent.getStringExtra("sessionId")
        if (sessionId.isNullOrEmpty()) {
            Toast.makeText(this, "No session id", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        loadSession(sessionId)
    }

    private fun loadSession(sessionId: String) {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        students.clear()
        adapter.notifyDataSetChanged()

        db.collection("AttendanceSessions").document(sessionId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    progress.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // collect present ids (Map or List)
                val presentAny = doc.get("present")
                if (presentAny is Map<*, *>) {
                    for ((k, v) in presentAny) {
                        val sid = k?.toString() ?: continue
                        val status = v?.toString() ?: "present"
                        students.add(SessionStudent(id = sid, name = "", status = status))
                    }
                } else if (presentAny is List<*>) {
                    for (it in presentAny) {
                        val sid = it?.toString() ?: continue
                        students.add(SessionStudent(id = sid, name = "", status = "present"))
                    }
                }

                // absentees (array) - add absent if not already present
                val absAny = doc.get("absentees")
                if (absAny is List<*>) {
                    for (it in absAny) {
                        val sid = it?.toString() ?: continue
                        val exists = students.any { s -> s.id == sid }
                        if (!exists) students.add(SessionStudent(id = sid, name = "", status = "absent"))
                    }
                }

                // optional sort: present first (alphabetically present < absent)
                students.sortBy { it.status }

                // update adapter so we at least show IDs quickly
                adapter.notifyDataSetChanged()

                // Now fetch names for each student id asynchronously
                for ((index, s) in students.withIndex()) {
                    val sid = s.id
                    // fetch user document
                    db.collection("Users").document(sid).get()
                        .addOnSuccessListener { userDoc ->
                            // if user exists and name present, update that entry
                            val name = userDoc.getString("name") ?: ""
                            if (name.isNotBlank()) {
                                // update list item (create new object)
                                students[index] = students[index].copy(name = name)
                                // notify item changed so view updates
                                adapter.notifyItemChanged(index)
                            } else {
                                // optionally show id as name fallback or keep blank
                                // students[index] = students[index].copy(name = sid)
                                // adapter.notifyItemChanged(index)
                            }
                        }
                        .addOnFailureListener {
                            // ignore failures for individual lookups (or log)
                        }
                }

                // final UI state
                progress.visibility = View.GONE
                tvEmpty.visibility = if (students.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}