package com.young.aircraft.gui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.R

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        supportActionBar?.apply {
            title = getString(R.string.history_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
