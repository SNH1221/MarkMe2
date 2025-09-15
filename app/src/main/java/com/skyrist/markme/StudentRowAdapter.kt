package com.skyrist.markme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

enum class AttStatus { PENDING, PRESENT, ABSENT }

data class StudentRow(
    val number: String,
    val name: String,
    val rfid: String?,
    var status: AttStatus = AttStatus.PENDING
)

class StudentRowAdapter(
    private val items: MutableList<StudentRow>
) : RecyclerView.Adapter<StudentRowAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgStatus)
        val name: TextView  = v.findViewById(R.id.tvName)
        val num: TextView   = v.findViewById(R.id.tvNumber)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_student_row, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = items[pos]
        h.name.text = it.name
        h.num.text  = it.number
        h.img.setImageResource(
            when (it.status) {
                AttStatus.PRESENT -> R.drawable.dot_present
                AttStatus.ABSENT  -> R.drawable.dot_absent
                else              -> R.drawable.dot_pending
            }
        )
    }

    override fun getItemCount() = items.size

    fun setPresentByNumber(number: String): Boolean {
        val idx = items.indexOfFirst { it.number == number }
        if (idx >= 0) {
            if (items[idx].status != AttStatus.PRESENT) {
                items[idx].status = AttStatus.PRESENT
                notifyItemChanged(idx)
            }
            return true
        }
        return false
    }

    fun markAbsentsForPending() {
        for (i in items.indices) {
            if (items[i].status == AttStatus.PENDING) {
                items[i].status = AttStatus.ABSENT
            }
        }
        notifyDataSetChanged()
    }

    fun presentList() = items.filter { it.status == AttStatus.PRESENT }.map { it.number }
    fun absentList()  = items.filter { it.status == AttStatus.ABSENT  }.map { it.number }
}
