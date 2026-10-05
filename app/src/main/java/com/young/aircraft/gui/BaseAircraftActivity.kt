package com.young.aircraft.gui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Base class for activities that use the standard Aircraft application theme. */
abstract class BaseAircraftActivity : AppCompatActivity() {
    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeViewModel(savedInstanceState)
        if (!isFinishing) initializeUI()
    }

    protected abstract fun initializeViewModel(savedInstanceState: Bundle?)

    protected abstract fun initializeUI()
}
