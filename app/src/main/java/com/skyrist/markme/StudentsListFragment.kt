package com.skyrist.markme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class StudentsListFragment : Fragment() {
    companion object { fun newInstance() = StudentsListFragment() }
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(android.R.layout.simple_list_item_1, c, false)
        val tv = v.findViewById<TextView>(android.R.id.text1)
        tv.text = "All students list goes here (click item -> open details)"
        return v
    }
}
