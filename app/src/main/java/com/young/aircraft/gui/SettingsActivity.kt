package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.young.aircraft.BuildConfig
import com.young.aircraft.gui.theme.AircraftTheme
import com.young.aircraft.providers.SettingsRepository

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AircraftTheme {
                val repository = SettingsRepository(this)
                val viewModel: SettingsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SettingsViewModel(repository) as T
                        }
                    }
                )

                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onNavigateToDeviceInfo = {
                        startActivity(Intent(this, DeviceInfoActivity::class.java))
                    },
                    onNavigateToQRCode = {
                        startActivity(Intent(this, QRCodeToolActivity::class.java))
                    },
                    onNavigateToAbout = {
                        startActivity(Intent(this, AboutAircraftActivity::class.java))
                    },
                    onNavigateToAboutMe = {
                        startActivity(Intent(this, AboutMeActivity::class.java))
                    },
                    onNavigateToPrivacy = {
                        startActivity(Intent(this, PrivacyPolicyActivity::class.java))
                    },
                    onNavigateToDevelop = {
                        startActivity(Intent(this, DevelopSettingsActivity::class.java))
                    },
                    showDevelopSettings = BuildConfig.DEBUG
                )
            }
        }
    }
}
