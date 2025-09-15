package com.skyrist.markme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * StudentsAdapter
 * - items: MutableList<StudentItem>
 * - statusMap: external MutableMap passed from Activity (studentId -> "pending"/"present"/"absent")
 * - editEnabled: when true user can toggle items by tapping; otherwise tapping does nothing
 */
class StudentsAdapter(
    private val items: MutableList<StudentItem>,
    private val statusMap: MutableMap<String, String>, // use the same map instance passed by Activity
    private var editEnabled: Boolean = false
) : RecyclerView.Adapter<StudentsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvStudentName)
        val tvNumber: TextView = v.findViewById(R.id.tvStudentNumber)
        val ivDot: ImageView = v.findViewById(R.id.ivStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.tvName.text = s.name
        holder.tvNumber.text = s.number

        // read status from the shared map (default pending)
        val st = statusMap[s.id] ?: "pending"
        val dot = when (st) {
            "present" -> R.drawable.dot_present
            "absent" -> R.drawable.dot_absent
            else -> R.drawable.dot_pending
        }
        holder.ivDot.setImageResource(dot)

        // if edit mode enabled allow toggling by tapping
        holder.ivDot.setOnClickListener {
            if (!editEnabled) return@setOnClickListener
            val cur = statusMap[s.id] ?: "pending"
            val next = when (cur) {
                "pending" -> "present"
                "present" -> "absent"
                else -> "pending"
            }
            statusMap[s.id] = next
            notifyItemChanged(position)
        }

        holder.itemView.setOnClickListener {
            if (!editEnabled) return@setOnClickListener
            val cur = statusMap[s.id] ?: "pending"
            val next = when (cur) {
                "pending" -> "present"
                "present" -> "absent"
                else -> "pending"
            }
            statusMap[s.id] = next
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * Called from Activity when an RFID/value is scanned.
     * We try several heuristics to match the scanned string to a student's stored rfid:
     * - exact raw match (after trimming newlines)
     * - digits-only match
     * - endsWith (scanner may provide suffix/prefix)
     * - startsWith
     * - reversed (some readers send reversed bytes)
     * - left-pad with zeros to typical rfid lengths (if stored had leading zeros)
     *
     * If found, mark the student present and notify that item changed.
     */
    // inside StudentsAdapter.kt

    /**
     * Try to find student from scannedRaw and mark them present.
     * Returns the matched student id if found, otherwise null.
     */
    fun markPresentByRfid(scannedRaw: String): String? {
        val raw = scannedRaw.trim()
        if (raw.isEmpty()) return null

        // helper checks
        fun matchCandidate(candidate: String): StudentItem? {
            return items.find { it.rfid.isNotEmpty()
                    && (it.rfid == candidate
                    || it.rfid.contains(candidate)
                    || candidate.contains(it.rfid)
                    || it.rfid.endsWith(candidate)
                    || it.rfid.startsWith(candidate))
            }
        }

        // 1) exact raw match (case)
        var found = matchCandidate(raw)

        // 2) digits-only
        if (found == null) {
            val digits = raw.replace(Regex("[^0-9]"), "")
            if (digits.isNotEmpty()) found = matchCandidate(digits)
        }

        // 3) try reversed
        if (found == null) {
            val rev = raw.reversed()
            found = matchCandidate(rev)
        }

        // 4) try left-pad zeros to common lengths (8,10,12)
        if (found == null) {
            val digits = raw.replace(Regex("[^0-9]"), "")
            if (digits.isNotEmpty()) {
                val lengths = listOf(8, 10, 12)
                for (len in lengths) {
                    if (digits.length < len) {
                        val padded = digits.padStart(len, '0')
                        found = matchCandidate(padded)
                        if (found != null) break
                    }
                }
            }
        }

        // 5) fallback: try any item whose rfid contains the scanned digits (loose)
        if (found == null) {
            val digits = raw.replace(Regex("[^0-9]"), "")
            if (digits.length >= 4) { // don't be too broad for short values
                found = items.find { it.rfid.contains(digits) }
            }
        }

        // mark if found and return id
        found?.let {
            statusMap[it.id] = "present"
            val idx = items.indexOf(it)
            if (idx >= 0) notifyItemChanged(idx) else notifyDataSetChanged()
            return it.id
        }

        return null
    }
    fun markAllAbsentUnlessPresent() {
        for (s in items) {
            val cur = statusMap[s.id] ?: "pending"
            if (cur != "present") statusMap[s.id] = "absent"
        }
        notifyDataSetChanged()
    }

    fun getAttendanceMap(): Map<String, Map<String, Any>> {
        val out = mutableMapOf<String, Map<String, Any>>()
        val now = System.currentTimeMillis()
        for (s in items) {
            val st = statusMap[s.id] ?: "absent"
            out[s.id] = mapOf("status" to st, "timestamp" to now)
        }
        return out
    }

    fun setInitialAllPending() {
        for (s in items) statusMap[s.id] = "pending"
        notifyDataSetChanged()
    }

    fun setEditEnabled(enabled: Boolean) {
        editEnabled = enabled
        notifyDataSetChanged()
    }

    fun setStatus(studentId: String, status: String) {
        statusMap[studentId] = status
        val idx = items.indexOfFirst { it.id == studentId }
        if (idx >= 0) notifyItemChanged(idx) else notifyDataSetChanged()
    }
}