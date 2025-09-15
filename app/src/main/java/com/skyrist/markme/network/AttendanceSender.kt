package com.skyrist.markme.network

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object AttendanceSender {
    private const val TAG = "AttendanceSender"
    private val api = ApiClient.smsApi

    fun sendForAbsentees(absentees: List<String>, message: String) {
        val db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Will send SMS for absentees: $absentees")

        for (studentId in absentees) {
            db.collection("Users").document(studentId).get()
                .addOnSuccessListener { doc ->
                    val parentPhone = doc.getString("parentPhone")
                    if (!parentPhone.isNullOrBlank()) {
                        Log.d(TAG, "Found parentPhone for $studentId -> $parentPhone")
                        sendSingle(parentPhone, message)
                    } else {
                        Log.w(TAG, "No parentPhone found for $studentId")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching user $studentId: ${e.message}", e)
                }
        }
    }

    private fun sendSingle(parentPhone: String, message: String) {
        Log.d(TAG, "Sending SMS to $parentPhone: \"$message\"")
        val req = SmsRequest(to = parentPhone, body = message)

        api.sendSms(req).enqueue(object : Callback<SmsResponse> {
            override fun onResponse(call: Call<SmsResponse>, response: Response<SmsResponse>) {
                Log.d(TAG, "Response code: ${response.code()}")
                if (response.isSuccessful) {
                    Log.d(TAG, "SMS sent: ${response.body()}")
                } else {
                    Log.e(TAG, "Error: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<SmsResponse>, t: Throwable) {
                Log.e(TAG, "Network error: ${t.message}", t)
            }
        })
    }
}