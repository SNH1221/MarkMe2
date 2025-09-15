package com.skyrist.markme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ClassItem(val classId: String, val subjectName: String, val time: String)

class ClassesAdapter(
    private val items: List<ClassItem>,
    private val onClick: (ClassItem) -> Unit
) : RecyclerView.Adapter<ClassesAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvClassTitle)
        val time: TextView = v.findViewById(R.id.tvClassTime)
        init {
            v.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_class, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.title.text = "${it.classId} • ${it.subjectName}"
        holder.time.text = it.time
    }

    override fun getItemCount(): Int = items.size
}
