package com.skyrist.markme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AttendanceRecordsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // simple container layout jisme fragment dalenge
        setContentView(R.layout.activity_attendance_records)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.records_container, AttendanceRecordsFragment.newInstance())
                .commit()
        }
    }
}
