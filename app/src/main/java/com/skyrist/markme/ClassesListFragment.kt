package com.skyrist.markme

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ClassesListFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView

    private val db = FirebaseFirestore.getInstance()
    private val classesList = mutableListOf<ClassItem>()
    private lateinit var adapter: ClassesAdapter

    private var passedTeacherId: String? = null

    companion object {
        // call with ClassesListFragment.newInstance("2001")
        fun newInstance(teacherId: String?): ClassesListFragment {
            val f = ClassesListFragment()
            val b = Bundle()
            if (teacherId != null) b.putString("teacherId", teacherId)
            f.arguments = b
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passedTeacherId = arguments?.getString("teacherId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val v = inflater.inflate(R.layout.fragment_classes_list, container, false)
        rv = v.findViewById(R.id.rvClasses)
        progress = v.findViewById(R.id.progress)
        tvEmpty = v.findViewById(R.id.tvEmpty)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ClassesAdapter(classesList) { classItem ->
            val i = Intent(requireContext(), ClassAttendanceActivity::class.java)
            i.putExtra("classId", classItem.classId)
            i.putExtra("subjectName", classItem.subjectName)
            startActivity(i)
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        loadClassesForTeacher()
    }

    private fun loadClassesForTeacher() {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        classesList.clear()
        adapter.notifyDataSetChanged()

        // priority: passed arg -> SharedPrefHelper -> show error
        val teacherId = if (!passedTeacherId.isNullOrEmpty()) {
            passedTeacherId
        } else {
            SharedPrefHelper.getTeacherId(requireContext()) // make sure this method exists
        }

        if (teacherId.isNullOrEmpty()) {
            progress.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Teacher ID not found."
            return
        }

        db.collection("Classes")
            .whereEqualTo("teacherId", teacherId)
            .get()
            .addOnSuccessListener { snap ->
                progress.visibility = View.GONE
                if (snap == null || snap.isEmpty) {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "No classes assigned."
                    return@addOnSuccessListener
                }

                for (doc in snap.documents) {
                    val classId = doc.id
                    val subjectName = doc.getString("subjectName") ?: ""
                    val time = doc.getString("time") ?: ""
                    classesList.add(ClassItem(classId, subjectName, time))
                }

                if (classesList.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "No classes assigned."
                } else {
                    tvEmpty.visibility = View.GONE
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Error loading classes"
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
