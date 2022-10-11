package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.service.MusicService
import com.young.aircraft.viewmodel.MainActivityViewModel
import kotlin.properties.Delegates
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var mService: MusicService
    private var exitTime: Long = 0
    private var lastX = 0
    private var lastY = 0
    private var maxRight by Delegates.notNull<Int>()
    private var maxBottom by Delegates.notNull<Int>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            mService = binder.getService()
            viewModel.updateSoundServiceStatus(true)
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            viewModel.updateSoundServiceStatus(false)
        }

    }

    @SuppressLint("ClickableViewAccessibility", "SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val coreView = GameCoreView(this)
        setContentView(coreView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        viewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(MainActivityViewModel::class.java)

        viewModel.isReadToPlaySound.observe(this, Observer {
            if (it) {
                Log.d("YoungTest", "===> to play background sound")
                Looper.myLooper()?.let { looper ->
                    Handler(looper).postDelayed({
                        mService.backgroundSoundPlay()
                    }, 200)
                }

            }
        })
//        binding.root.setOnClickListener {
//            if (viewModel.isReadToPlaySound.value == true) {
//                mService.shotSoundPlay()
//            }
//        }
//        binding.jetPlane.setOnTouchListener { view, event ->
//            //get original x/y
//            val eventX = event.rawX.toInt()
//            val eventY = event.rawY.toInt()
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    //Gte right/bottom of parent
//                    maxRight = binding.container.right
//                    maxBottom = binding.container.bottom
//                    //record lastX/lastY
//                    lastX = eventX
//                    lastY = eventY
//                }
//                MotionEvent.ACTION_MOVE -> {
//                    //calculate offset
//                    val dx: Int = eventX - lastX
//                    val dy: Int = eventY - lastY
//                    //using offset set imageView
//                    var left: Int = binding.jetPlane.left + dx
//                    var top: Int = binding.jetPlane.top + dy
//                    var right: Int = binding.jetPlane.right + dx
//                    var bottom: Int = binding.jetPlane.bottom + dy
//
//                    //set left  >=0
//                    if (left < 0) {
//                        right += -left
//                        left = 0
//                    }
//                    //set top
//                    if (top < 0) {
//                        bottom += -top
//                        top = 0
//                    }
//                    //set right <=maxRight
//                    if (right > maxRight) {
//                        left -= right - maxRight
//                        right = maxRight
//                    }
//                    //set bottom <=maxBottom
//                    if (bottom > maxBottom) {
//                        top -= bottom - maxBottom
//                        bottom = maxBottom
//                    }
//                    binding.jetPlane.layout(left, top, right, bottom)
//                    lastX = eventX
//                    lastY = eventY
//                }
//                else -> {}
//            }
//            true
//        }
    }

    private fun addEnemy(number: Int) {
        val enemy_back = mutableListOf<Int>()
        enemy_back.add(R.drawable.enemy_1)
        enemy_back.add(R.drawable.enemy_2)
        enemy_back.add(R.drawable.enemy_3)
        for (i in 1..number) {
            val enemy = ImageView(this)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.width = 150
            params.height = 150
            enemy.layoutParams = params
            enemy.rotation = 180.0f
            enemy.scaleType = ImageView.ScaleType.FIT_CENTER
            enemy.setImageDrawable(AppCompatResources.getDrawable(this, enemy_back[i % 3]))
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


    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d("YoungTest", "===> $event")
        return true
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        viewModel.updateSoundServiceStatus(false)
    }

}