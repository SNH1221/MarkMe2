package com.skyrist.markme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class EditRow(val number: String, val name: String, var present: Boolean)

class EditAttendanceAdapter(
    private val items: MutableList<EditRow>
) : RecyclerView.Adapter<EditAttendanceAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvNumber: TextView = v.findViewById(R.id.tvNumber)
        val sw: Switch = v.findViewById(R.id.switchPresent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_edit_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = items[pos]
        h.tvName.text = it.name
        h.tvNumber.text = it.number
        h.sw.setOnCheckedChangeListener(null)
        h.sw.isChecked = it.present
        h.sw.setOnCheckedChangeListener { _, isChecked -> it.present = isChecked }
    }

    override fun getItemCount() = items.size

    fun presentList() = items.filter { it.present }.map { it.number }
    fun absentList()  = items.filter { !it.present }.map { it.number }
}
