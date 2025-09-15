package com.skyrist.markme.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.skyrist.markme.R

class ChatAdapter(private val items: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_BOT = 2
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvText: TextView = itemView.findViewById(R.id.tvText)
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].fromUser) TYPE_USER else TYPE_BOT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_bot
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tvText.text = items[position].text
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(m: ChatMessage) {
        items.add(m)
        notifyItemInserted(items.size - 1)
    }
}