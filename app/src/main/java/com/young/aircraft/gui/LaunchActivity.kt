package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.databinding.ActivityLaunchBinding

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {
    lateinit var binding: ActivityLaunchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.startGame.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.gameHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.gameSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}