package com.young.aircraft.gui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.young.aircraft.gui.theme.AircraftTheme

class StoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                StoreScreen(onBackClick = { finish() })
            }
        }
    }
}
