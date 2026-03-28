package com.young.aircraft.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import com.young.aircraft.R
import com.young.aircraft.providers.SettingsRepository
/**
 * Create by Young
 * 2026/3/10
 **/
class MusicService : Service() {
    private val MAX_STREAMS = 5
    private lateinit var soundPool: SoundPool
    private lateinit var soundMap: HashMap<Int, Int>
    private lateinit var settingsRepository: SettingsRepository
    private var bgMediaPlayer: MediaPlayer? = null
    private var backgroundSoundEnabled = true
    private var combatSoundEnabled = true
    private val mBinder = MusicBinder()
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SettingsRepository.KEY_BACKGROUND_SOUND -> {
                backgroundSoundEnabled = settingsRepository.isBackgroundSoundEnabled()
                if (!backgroundSoundEnabled) {
                    backgroundSoundStop()
                }
            }

            SettingsRepository.KEY_COMBAT_SOUND -> {
                combatSoundEnabled = settingsRepository.isCombatSoundEnabled()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        backgroundSoundEnabled = settingsRepository.isBackgroundSoundEnabled()
        combatSoundEnabled = settingsRepository.isCombatSoundEnabled()
        settingsRepository.registerListener(settingsListener)
        val attribution: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build()
        soundPool = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attribution).build()
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
        backgroundSoundStop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        settingsRepository.unregisterListener(settingsListener)
        bgMediaPlayer?.release()
        bgMediaPlayer = null
        soundPool.release()
        super.onDestroy()
    }

    fun backgroundSoundPlay() {
        if (!backgroundSoundEnabled) return
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
        if (!combatSoundEnabled) return
        playSound(0x002, 1.0f, 0)
    }

    fun playerHitSoundPlay() {
        if (!combatSoundEnabled) return
        playSound(0x003, 1.0f, 0)
    }

    fun enemyHitSoundPlay() {
        if (!combatSoundEnabled) return
        playSound(0x004, 1.0f, 0)
    }

    fun gameOverSoundPlay() {
        if (!combatSoundEnabled) return
        playSound(0x005, 1.0f, 0)
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
}

