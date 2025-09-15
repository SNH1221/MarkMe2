package com.skyrist.markme

import android.content.Context
import android.content.SharedPreferences

object SharedPrefHelper {
    private const val PREFS_NAME = "markme_prefs"
    private const val KEY_TEACHER_ID = "key_teacher_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Save teacher id (call this after teacher login)
    fun setTeacherId(context: Context, teacherId: String) {
        prefs(context).edit().putString(KEY_TEACHER_ID, teacherId).apply()
    }

    // Read teacher id (returns empty string if not saved)
    fun getTeacherId(context: Context): String =
        prefs(context).getString(KEY_TEACHER_ID, "") ?: ""
}
