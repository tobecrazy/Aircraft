package com.young.aircraft.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.preference.PreferenceManager
import com.young.aircraft.R
/**
 * Create by Young
 * 2026/3/10
 **/
class MusicService : Service() {
    private val MAX_STREAMS = 5
    private lateinit var soundPool: SoundPool
    private lateinit var soundMap: HashMap<Int, Int>
    private var bgMediaPlayer: MediaPlayer? = null
    private val mBinder = MusicBinder()

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate() {
        super.onCreate()
        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attribution: AudioAttributes =
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build()
            SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attribution).build()
        } else {
            SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, 100)
        }
        soundMap = hashMapOf()
        soundMap[0x002] = soundPool.load(this, R.raw.fire, 1)
        soundMap[0x003] = soundPool.load(this, R.raw.be_hit, 1)
        soundMap[0x004] = soundPool.load(this, R.raw.enemy_be_hit, 1)
        soundMap[0x005] = soundPool.load(this, R.raw.game_over, 1)
    }

    @Synchronized
    fun playSound(sound: Int, fSpeed: Float, loop: Int = 0) {
        val audioManager: AudioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val streamVolumeCurrent: Float =
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val streamVolumeMax: Float =
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val volume: Float = streamVolumeCurrent / streamVolumeMax as Float
        soundMap[sound]?.let { soundPool.play(it, volume, volume, 1, loop, fSpeed) }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bgMediaPlayer?.release()
        bgMediaPlayer = null
        soundPool.release()
        return super.onUnbind(intent)
    }

    fun backgroundSoundPlay() {
        if (!isBackgroundSoundEnabled()) return
        if (bgMediaPlayer == null) {
            bgMediaPlayer = MediaPlayer.create(this, R.raw.background1).apply {
                isLooping = true
                start()
            }
        } else if (bgMediaPlayer?.isPlaying == false) {
            bgMediaPlayer?.start()
        }
    }

    fun backgroundSoundStop() {
        bgMediaPlayer?.pause()
    }

    fun shotSoundPlay() {
        if (!isCombatSoundEnabled()) return
        playSound(0x002, 1.0f, 0)
    }

    fun playerHitSoundPlay() {
        if (!isCombatSoundEnabled()) return
        playSound(0x003, 1.0f, 0)
    }

    fun enemyHitSoundPlay() {
        if (!isCombatSoundEnabled()) return
        playSound(0x004, 1.0f, 0)
    }

    fun gameOverSoundPlay() {
        if (!isCombatSoundEnabled()) return
        playSound(0x005, 1.0f, 0)
    }

    private fun isBackgroundSoundEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("background_sound", true)
    }

    private fun isCombatSoundEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("combat_sound", true)
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
}


