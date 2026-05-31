package com.young.aircraft.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.young.aircraft.R
import com.young.aircraft.gui.FlashlightActivity
import com.young.aircraft.viewmodel.FlashlightViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the camera torch.
 *
 * Three-layer keep-alive architecture:
 *  1. Foreground service (camera type) — highest non-system process priority.
 *  2. Persistent notification with a "turn off" action — required by the OS, also gives
 *     the user a one-tap escape so they don't force-stop the app.
 *  3. PARTIAL_WAKE_LOCK while SOS is active — keeps CPU running so coroutine `delay()`
 *     stays accurate when the screen is off (otherwise dose pacing drifts hundreds of ms).
 *
 * State flow back to UI: torch on/off is observed via [CameraManager.TorchCallback] in the
 * ViewModel, so the service does not need to push it. SOS mode (a fast on/off loop) is
 * not derivable from torch state, so it is exposed via [isSosRunning].
 */
class FlashlightService : Service() {

    private val cameraManager: CameraManager by lazy {
        getSystemService(CAMERA_SERVICE) as CameraManager
    }
    private val cameraId: String? by lazy { findFlashCameraId() }

    private var wakeLock: PowerManager.WakeLock? = null
    private var sosJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // System-restart with null intent (after START_STICKY kill): nothing to recover —
        // the user can re-enable from the Activity. Stop quietly.
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground BEFORE doing torch work — required within 5s of startForegroundService.
        startInForeground(notificationFor(intent.action))

        val brightness = intent.getFloatExtra(EXTRA_BRIGHTNESS, currentBrightness).coerceIn(0f, 1f)
        currentSosUnitMs = intent.getLongExtra(EXTRA_SOS_UNIT_MS, currentSosUnitMs)

        when (intent.action) {
            ACTION_TORCH_ON -> {
                stopSosInternal()
                setTorch(true, brightness)
                _isSosRunning.value = false
            }
            ACTION_TORCH_OFF -> {
                stopSosInternal()
                setTorch(false, brightness)
                _isSosRunning.value = false
                stopSelf()
            }
            ACTION_SOS_ON -> startSos()
            ACTION_SOS_OFF -> {
                stopSosInternal()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startSos() {
        sosJob?.cancel()
        acquireWakeLock()
        _isSosRunning.value = true
        sosJob = scope.launch {
            while (isActive) {
                for (pulse in FlashlightViewModel.SOS_PATTERN) {
                    if (!isActive) break
                    setTorch(pulse.torchOn, currentBrightness)
                    delay(pulse.units * currentSosUnitMs)
                }
                if (!isActive) break
                setTorch(false, currentBrightness)
                delay(SOS_WORD_GAP_UNITS * currentSosUnitMs)
            }
        }
    }

    private fun stopSosInternal() {
        sosJob?.cancel()
        sosJob = null
        _isSosRunning.value = false
        releaseWakeLock()
        // Ensure torch is off after a SOS cancellation — the loop may have left it on mid-pulse.
        setTorch(false, currentBrightness)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aircraft::FlashlightSos").apply {
            // 10-minute safety ceiling; the lock is released proactively in stopSosInternal/onDestroy.
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun setTorch(enabled: Boolean, brightness: Float) {
        val id = cameraId ?: return
        currentBrightness = brightness
        runCatching {
            if (
                enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                maxBrightnessLevel(id) > 1
            ) {
                cameraManager.turnOnTorchWithStrengthLevel(
                    id,
                    FlashlightViewModel.brightnessToStrengthLevel(brightness, maxBrightnessLevel(id))
                )
            } else {
                cameraManager.setTorchMode(id, enabled)
            }
        }
    }

    private fun findFlashCameraId(): String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private fun maxBrightnessLevel(id: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return runCatching {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                ?: 1
        }.getOrDefault(1).coerceAtLeast(1)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.flashlight_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.flashlight_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun notificationFor(action: String?): Notification {
        val contentText = getString(
            if (action == ACTION_SOS_ON) R.string.flashlight_notification_sos_on
            else R.string.flashlight_notification_torch_on
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, FlashlightActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Turn off" action — the escape hatch that prevents users from force-stopping the app.
        val offIntent = PendingIntent.getService(
            this,
            REQUEST_OFF,
            Intent(this, FlashlightService::class.java).apply { this.action = ACTION_TORCH_OFF },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_flashlight)
            .setContentTitle(getString(R.string.flashlight_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.flashlight_notification_action_off), offIntent)
            .build()
    }

    override fun onDestroy() {
        sosJob?.cancel()
        setTorch(false, currentBrightness)
        releaseWakeLock()
        scope.cancel()
        _isSosRunning.value = false
        super.onDestroy()
    }

    /** Most recent brightness — service is the source of truth so it survives kill+restart. */
    private var currentBrightness: Float = 1f
    /** Most recent SOS unit duration in ms (pushed in via SOS_ON intent extra). */
    private var currentSosUnitMs: Long = FlashlightViewModel.DEFAULT_SOS_UNIT_MS_LONG

    companion object {
        const val ACTION_TORCH_ON = "com.young.aircraft.flashlight.TORCH_ON"
        const val ACTION_TORCH_OFF = "com.young.aircraft.flashlight.TORCH_OFF"
        const val ACTION_SOS_ON = "com.young.aircraft.flashlight.SOS_ON"
        const val ACTION_SOS_OFF = "com.young.aircraft.flashlight.SOS_OFF"
        const val EXTRA_BRIGHTNESS = "brightness"
        const val EXTRA_SOS_UNIT_MS = "sos_unit_ms"

        private const val CHANNEL_ID = "flashlight_channel"
        private const val NOTIFICATION_ID = 0xF1A511
        private const val REQUEST_OFF = 1
        private const val WAKELOCK_TIMEOUT_MS = 10L * 60L * 1000L
        private const val SOS_WORD_GAP_UNITS = 7

        private val _isSosRunning = MutableStateFlow(false)

        /** SOS coroutine running state — observed by FlashlightViewModel. */
        val isSosRunning: StateFlow<Boolean> = _isSosRunning.asStateFlow()
    }
}
