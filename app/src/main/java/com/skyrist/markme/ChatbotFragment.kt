package com.skyrist.markme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ChatbotFragment : Fragment() {

    companion object {
        fun newInstance() = ChatbotFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
        val tv = v.findViewById<TextView>(android.R.id.text1)
        tv.text = "🤖 Chatbot feature coming soon!\n(Ask queries like 'lowest attendance student')"
        return v
    }
}
