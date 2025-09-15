package com.skyrist.markme.ui

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.skyrist.markme.R
import com.skyrist.markme.ui.chat.ChatAdapter
import com.skyrist.markme.ui.chat.ChatMessage

class ChatbotActivity : AppCompatActivity() {

    private val TAG = "ChatbotActivity"
    private lateinit var recyclerChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var adapter: ChatAdapter

    // Firestore
    private val db = FirebaseFirestore.getInstance()

    // classId to analyze - pass this when starting ChatbotActivity e.g. intent.putExtra("classId", "CSE101")
    private var classId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        recyclerChat = findViewById(R.id.recyclerChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        adapter = ChatAdapter(mutableListOf())
        recyclerChat.layoutManager = LinearLayoutManager(this)
        recyclerChat.adapter = adapter

        classId = intent.getStringExtra("classId")
        Log.d(TAG, "classId from intent = $classId")

        btnSend.setOnClickListener { onSendMessage() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSendMessage(); true
            } else false
        }
    }

    private fun onSendMessage() {
        val text = etMessage.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "Type something", Toast.LENGTH_SHORT).show()
            return
        }

        // show user message
        val userMsg = ChatMessage(text = text, fromUser = true)
        adapter.addMessage(userMsg)
        recyclerChat.scrollToPosition(adapter.itemCount - 1)
        etMessage.setText("")

        // Simple command recognition
        val cmd = text.lowercase()
        when {
            cmd.contains("lowest attendance") -> {
                adapter.addMessage(ChatMessage("Looking up lowest attendance..."))
                lookupAttendance(lowest = true)
            }
            cmd.contains("highest attendance") -> {
                adapter.addMessage(ChatMessage("Looking up highest attendance..."))
                lookupAttendance(lowest = false)
            }
            else -> {
                // default demo reply
                adapter.addMessage(ChatMessage("Bot (demo): I heard \"$text\""))
                recyclerChat.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    /**
     * Query Firestore for AttendanceSessions for classId, compute attendance percent per student,
     * and reply with top 3 lowest or highest.
     */
    private fun lookupAttendance(lowest: Boolean) {
        val useClassId = classId
        if (useClassId.isNullOrBlank()) {
            // try to pick first class in Classes collection (quick demo fallback)
            db.collection("Classes").limit(1).get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) {
                        adapter.addMessage(ChatMessage("No classId provided and no Classes found."))
                        return@addOnSuccessListener
                    }
                    val doc = snap.documents[0]
                    val id = doc.id
                    classId = id
                    adapter.addMessage(ChatMessage("Using classId: $id"))
                    doAttendanceQuery(id, lowest)
                }
                .addOnFailureListener { e ->
                    adapter.addMessage(ChatMessage("Failed to load classes: ${e.message}"))
                }
        } else {
            doAttendanceQuery(useClassId, lowest)
        }
    }

    private fun doAttendanceQuery(classId: String, lowest: Boolean) {
        // Fetch finalized sessions for this class
        db.collection("AttendanceSessions")
            .whereEqualTo("classId", classId)
            .whereEqualTo("finalized", true)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    adapter.addMessage(ChatMessage("No finalized attendance sessions found for class $classId."))
                    return@addOnSuccessListener
                }

                // map studentId -> presentCount
                val counts = mutableMapOf<String, Int>()
                var totalSessions = 0

                for (doc in snap.documents) {
                    totalSessions += 1
                    val present = doc.get("present") as? List<*>
                    present?.forEach { idObj ->
                        val id = idObj?.toString() ?: return@forEach
                        counts[id] = (counts[id] ?: 0) + 1
                    }
                }

                if (totalSessions == 0) {
                    adapter.addMessage(ChatMessage("No sessions counted for class $classId."))
                    return@addOnSuccessListener
                }

                // Now we need the roster (all students in class) so that students with 0 presents show up
                db.collection("Classes").document(classId).get()
                    .addOnSuccessListener { clsDoc ->
                        val roster = (clsDoc.get("students") as? List<*>)?.map { it.toString() } ?: emptyList()

                        // ensure every student in roster is present in counts map (0 if none)
                        roster.forEach { sid -> if (!counts.containsKey(sid)) counts[sid] = 0 }

                        // compute percentages and sort
                        val percentList = counts.map { (sid, presentCount) ->
                            val pct = (presentCount.toDouble() / totalSessions.toDouble()) * 100.0
                            Pair(sid, pct)
                        }

                        val sorted = if (lowest) percentList.sortedBy { it.second } else percentList.sortedByDescending { it.second }
                        val topN = sorted.take(5) // show up to 5 results

                        if (topN.isEmpty()) {
                            adapter.addMessage(ChatMessage("No students found for class $classId."))
                            return@addOnSuccessListener
                        }

                        // fetch names for these topN ids
                        val idsToFetch = topN.map { it.first }
                        fetchNamesAndReply(idsToFetch, topN, totalSessions, lowest)
                    }
                    .addOnFailureListener { e ->
                        adapter.addMessage(ChatMessage("Failed to load class roster: ${e.message}"))
                    }
            }
            .addOnFailureListener { e ->
                adapter.addMessage(ChatMessage("Failed to load sessions: ${e.message}"))
            }
    }

    private fun fetchNamesAndReply(ids: List<String>, data: List<Pair<String, Double>>, totalSessions: Int, lowest: Boolean) {
        if (ids.isEmpty()) {
            adapter.addMessage(ChatMessage("No student ids to look up."))
            return
        }

        // batch fetch from Users collection
        val usersRef = db.collection("Users")
        // Firestore doesn't have multi-get with whereIn > 10 elements; this demo uses <= 10 (we requested 5)
        usersRef.whereIn(FieldPath.documentId(), ids).get()
            .addOnSuccessListener { snap ->
                val nameMap = mutableMapOf<String, String>()
                for (d in snap.documents) {
                    nameMap[d.id] = d.getString("name") ?: d.id
                }

                // prepare reply text
                val sb = StringBuilder()
                val title = if (lowest) "Lowest attendance" else "Highest attendance"
                sb.append("$title (based on $totalSessions sessions):\n")
                for ((id, pct) in data) {
                    val nm = nameMap[id] ?: id
                    sb.append("${nm} (${id}): ${"%.1f".format(pct)}%\n")
                }
                adapter.addMessage(ChatMessage(sb.toString()))
                recyclerChat.scrollToPosition(adapter.itemCount - 1)
            }
            .addOnFailureListener { e ->
                adapter.addMessage(ChatMessage("Failed to lookup user names: ${e.message}"))
            }
    }
}