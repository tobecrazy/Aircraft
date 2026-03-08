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
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        binding.gameHistory.setOnClickListener {
            //TODO
        }

        binding.gameSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}