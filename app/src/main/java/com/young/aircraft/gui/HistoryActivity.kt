package com.young.aircraft.gui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.young.aircraft.gui.theme.AircraftTheme
import com.young.aircraft.providers.DatabaseProvider

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val dao = DatabaseProvider.getDatabase(this).playerGameDataDao()
        
        setContent {
            AircraftTheme {
                val viewModel: HistoryViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return HistoryViewModel(dao) as T
                        }
                    }
                )
                
                HistoryScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
