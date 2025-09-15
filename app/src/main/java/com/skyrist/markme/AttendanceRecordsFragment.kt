package com.skyrist.markme

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class AttendanceRecordsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView

    private val items = mutableListOf<SessionRecord>()
    private lateinit var adapter: SessionsAdapter

    companion object {
        fun newInstance() = AttendanceRecordsFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_attendance_records, container, false)
        rv = v.findViewById(R.id.rvSessions)
        progress = v.findViewById(R.id.progress)
        tvEmpty = v.findViewById(R.id.tvEmpty)

        adapter = SessionsAdapter(items) { sessionId ->
            val i = Intent(requireContext(), SessionDetailActivity::class.java)
            i.putExtra("sessionId", sessionId)
            startActivity(i)
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        loadSessions()
        return v
    }

    private fun loadSessions() {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        items.clear()

        db.collection("AttendanceSessions")
            .get()
            .addOnSuccessListener { snap ->
                // optional debug
                android.util.Log.d("ATT_REC", "snap size=${snap.size()}")

                for (doc in snap.documents) {
                    android.util.Log.d("ATT_REC", "doc id=${doc.id} data=${doc.data}")

                    val id = doc.id
                    val classId = doc.getString("classId") ?: ""
                    val subject = doc.getString("subject") ?: ""

                    // Most likely field in your DB from screenshot: "savedAt"
                    val ts = when {
                        doc.contains("savedAt") -> doc.getTimestamp("savedAt")
                        doc.contains("savedAtTimestamp") -> doc.getTimestamp("savedAtTimestamp")
                        doc.contains("savedAtTime") -> doc.getTimestamp("savedAtTime")
                        doc.contains("timestamp") -> doc.getTimestamp("timestamp")
                        doc.contains("time") -> doc.getTimestamp("time")
                        else -> null
                    }

                    val timeFormatted = ts?.let {
                        val date = it.toDate()
                        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        fmt.format(date)
                    } ?: (doc.getString("time") ?: "")

                    items.add(SessionRecord(id = id, classId = classId, subject = subject, date = "", timeFormatted = timeFormatted))
                }

                adapter.notifyDataSetChanged()
                progress.visibility = View.GONE
                tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                android.util.Log.e("ATT_REC", "load failed", e)
                Toast.makeText(requireContext(), "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}