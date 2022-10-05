package com.young.aircraft.gui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityMainBinding
import com.young.aircraft.service.MusicService
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mService: MusicService
    private var mBound: Boolean = false
    private var exitTime: Long = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            mBound = false
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val textView = TextView(this)
        textView.text = getString(R.string.app_name)
        binding.container.addView(textView)
        binding.play.setOnClickListener {
            if (mBound) {
                mService.soundPlay()
            }
            binding.play.visibility = View.GONE
        }

        binding.root.setOnClickListener {
            if (mBound) {
                mService.soundPlayShot()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                exitApp()
                return false
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exitApp() {
        if ((System.currentTimeMillis() - exitTime) > 2000) {
            Toast.makeText(
                this, getString(R.string.exit_warning_msg),
                Toast.LENGTH_SHORT
            ).show();
            exitTime = System.currentTimeMillis();
        } else {
            finish();
            exitProcess(0);
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }


    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }
}