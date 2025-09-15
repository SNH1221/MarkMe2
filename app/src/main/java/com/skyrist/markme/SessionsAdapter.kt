package com.skyrist.markme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionsAdapter(
    private val items: List<SessionRecord>,
    private val onClick: (sessionId: String) -> Unit
) : RecyclerView.Adapter<SessionsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvSessionTitle)
        val tvId: TextView = view.findViewById(R.id.tvSessionId)
        val tvTime: TextView = view.findViewById(R.id.tvSessionTime)
        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(items[pos].id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_session_record, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.tvTitle.text = (s.subject.ifEmpty { s.classId }).let { if (it.isEmpty()) "Session" else it }
        holder.tvId.text = "ID: ${s.id}"
        holder.tvTime.text = s.timeFormatted
    }

    override fun getItemCount(): Int = items.size
}