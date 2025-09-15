// SessionStudentAdapter.kt
package com.skyrist.markme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView



class SessionStudentAdapter(
    private val items: List<SessionStudent>
) : RecyclerView.Adapter<SessionStudentAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIdName: TextView = view.findViewById(R.id.tvStudentName)    // your xml id
        val tvStatus: TextView = view.findViewById(R.id.tvStudentStatus) // your xml id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_student, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]

        // show "id  •  name" (if name empty, shows just id)
        val namePart = if (s.name.isNotBlank()) "  •  ${s.name}" else ""
        holder.tvIdName.text = "${s.id}$namePart"

        // status text (present/absent) — keep as is
        holder.tvStatus.text = s.status
    }

    override fun getItemCount(): Int = items.size
}