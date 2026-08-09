package com.young.aircraft.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RawRes
import com.young.aircraft.R
import com.young.aircraft.data.SettingsRepository
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
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private val audioFocusListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    backgroundSoundStop()
                    abandonAudioFocus()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    backgroundSoundStop()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    if (backgroundSoundEnabled) {
                        backgroundSoundPlay()
                    }
                }
            }
        }
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

            SettingsRepository.KEY_BGM_FORMAT -> {
                // Live-swap the BGM track when the user changes format from Settings.
                val wasPlaying = bgMediaPlayer != null
                bgMediaPlayer?.release()
                bgMediaPlayer = null
                if (wasPlaying && backgroundSoundEnabled) {
                    backgroundSoundPlay()
                }
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
        audioFocusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attribution)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(true)
                .build()
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
        abandonAudioFocus()
        bgMediaPlayer?.release()
        bgMediaPlayer = null
        soundPool.release()
        super.onDestroy()
    }

    fun backgroundSoundPlay() {
        if (!backgroundSoundEnabled) return
        if (!requestAudioFocus()) return
        if (bgMediaPlayer == null) {
            bgMediaPlayer = createBgMediaPlayer()?.apply {
                isLooping = true
                start()
            }
        } else if (bgMediaPlayer?.isPlaying == false) {
            bgMediaPlayer?.start()
        }
    }

    fun backgroundSoundStop() {
        bgMediaPlayer?.takeIf { it.isPlaying }?.pause()
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

    /**
     * Picks the configured BGM track. When OGG is selected but the asset cannot be
     * opened (e.g. missing during integration), falls back to the MP3 track so the
     * game still has music.
     */
    private fun createBgMediaPlayer(): MediaPlayer? {
        val preferredRes = currentBgmRawRes()
        if (preferredRes != 0) {
            MediaPlayer.create(this, preferredRes)?.let { return it }
        }
        if (preferredRes != FALLBACK_BGM_RES) {
            Log.w(TAG, "Failed to load BGM res=$preferredRes, falling back to MP3")
            return MediaPlayer.create(this, FALLBACK_BGM_RES)
        }
        return null
    }

    @RawRes
    private fun currentBgmRawRes(): Int =
        when (settingsRepository.getBgmFormat()) {
            SettingsRepository.BGM_FORMAT_OGG -> {
                // Runtime lookup so the project still compiles before bgm_main.ogg is added.
                val id = resources.getIdentifier(OGG_BGM_NAME, "raw", packageName)
                if (id == 0) FALLBACK_BGM_RES else id
            }
            else -> FALLBACK_BGM_RES
        }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val request = audioFocusRequest ?: return true
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(request)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val request = audioFocusRequest ?: return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocusRequest(request)
        hasAudioFocus = false
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    companion object {
        private const val TAG = "MusicService"
        private const val OGG_BGM_NAME = "bgm_main"

        @RawRes
        private val FALLBACK_BGM_RES = R.raw.background1
    }
}
