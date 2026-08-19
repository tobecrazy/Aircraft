package com.young.aircraft.gui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.databinding.ActivityDevelopSettingsBinding
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.DevelopSettingsViewModel

class DevelopSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevelopSettingsBinding
    private lateinit var viewModel: DevelopSettingsViewModel
    private var clickCount = 0

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrToolNotification()
        } else {
            Toast.makeText(this, R.string.develop_settings_notification_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }

        title = getString(R.string.develop_settings_title)
        binding = ActivityDevelopSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this, DevelopSettingsViewModel.Factory(this))[DevelopSettingsViewModel::class.java]

        binding.btnBack.setOnClickListener { finish() }
        binding.tvBuildBadge.text = getString(R.string.develop_settings_debug_badge)
        binding.tvVersionBadge.text = getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
        binding.tvVersionBadge.setOnClickListener {
            if (++clickCount % 8 == 0) {
                binding.switchInvincible.toggle()
            }
        }

        binding.tvSummary.text = getString(R.string.develop_settings_banner_summary)
        setupInvincibleMode()
        setupSupperBanner()
        binding.btnTestCrash.setOnClickListener {
            throw RuntimeException("Test Crash") // Force a crash
        }

        binding.btnTestRichText.setOnClickListener {
            startActivity(Intent(this, RichTextEditorActivity::class.java))
        }
        binding.btnAndroidDevAssistantTools.setOnClickListener {
            startActivity(Intent(this, AndroidDevAssistantToolsActivity::class.java))
        }
        binding.btnNotification.setOnClickListener {
            showNotificationConfirmationDialog()
        }
    }

    private fun showNotificationConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.develop_settings_notification_dialog_title)
            .setMessage(R.string.develop_settings_notification_dialog_message)
            .setPositiveButton(R.string.develop_settings_notification_dialog_ok) { _, _ ->
                createQrToolNotification()
            }
            .setNegativeButton(R.string.history_cancel, null)
            .show()
    }

    private fun createQrToolNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        showQrToolNotification()
    }

    private fun showQrToolNotification() {
        val appName = getString(R.string.app_name)
        val message = getString(R.string.develop_settings_notification_message, appName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            QR_TOOL_NOTIFICATION_REQUEST_CODE,
            Intent(this, QRCodeToolActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(NotificationManager::class.java)

        notificationManager.createNotificationChannel(
            NotificationChannel(
                QR_TOOL_NOTIFICATION_CHANNEL_ID,
                getString(R.string.develop_settings_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.develop_settings_notification_channel_description)
            }
        )

        val notification = NotificationCompat.Builder(this, QR_TOOL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.develop_settings_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(QR_TOOL_NOTIFICATION_ID, notification)
    }

    private fun setupSupperBanner() {
        val localDescription = getString(R.string.develop_settings_supper_banner_local_description)
        val networkDescription = getString(R.string.develop_settings_supper_banner_network_description)
        val backgroundItems = listOf(
            "background.jpg" to R.drawable.background,
            "background_1.jpg" to R.drawable.background_1,
            "background_2.jpg" to R.drawable.background_2,
            "background_3.jpg" to R.drawable.background_3,
            "background_4.jpg" to R.drawable.background_4
        ).map { (name, drawableRes) ->
            SupperBannerItem(
                name = name,
                description = localDescription,
                image = SupperBannerImage.Local(drawableRes)
            )
        } + listOf(
            SupperBannerItem(
                name = "network_TianQi",
                description = networkDescription,
                image = SupperBannerImage.Network(AircraftConstants.Urls.EXAMPLE_IMAGE_PNG)
            ),
            SupperBannerItem(
                name = "network_ContactUs",
                description = networkDescription,
                image = SupperBannerImage.Network(AircraftConstants.Urls.CONTACT_US_QR_CODE)
            )
        )

        binding.supperBanner.apply {
            setItems(backgroundItems)
            setAutoPlayEnabled(binding.switchSupperBannerAutoPlay.isChecked)
            setShowImageInfo(binding.switchSupperBannerInfo.isChecked)
            setShowIndicator(binding.switchSupperBannerIndicator.isChecked)
            setTransitionTimeMillis(
                binding.etSupperBannerTransition.text.toString().toLongOrNull()
                    ?: SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
            )
            setOnBannerClickListener { item, _ ->
                startActivity(ShowImageDetailsActivity.createIntent(this@DevelopSettingsActivity, item))
            }
            setIndicatorCustomizer { indicator, selected, _ ->
                indicator.typeface = Typeface.MONOSPACE
                indicator.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                indicator.setTextColor(
                    if (selected) Color.parseColor("#061317") else Color.parseColor("#B8C9E8")
                )
                indicator.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (selected) Color.parseColor("#4EA1FF") else Color.parseColor("#2E3A4C"))
                    setStroke(2, Color.parseColor(if (selected) "#D8F0FF" else "#617089"))
                }
            }
        }

        binding.switchSupperBannerAutoPlay.setOnCheckedChangeListener { _, enabled ->
            binding.supperBanner.setAutoPlayEnabled(enabled)
        }
        binding.switchSupperBannerInfo.setOnCheckedChangeListener { _, enabled ->
            binding.supperBanner.setShowImageInfo(enabled)
        }
        binding.switchSupperBannerIndicator.setOnCheckedChangeListener { _, enabled ->
            binding.supperBanner.setShowIndicator(enabled)
        }
        binding.etSupperBannerTransition.doAfterTextChanged { text ->
            val transitionTime = text.toString().toLongOrNull() ?: return@doAfterTextChanged
            val coercedTime = SupperBannerConfig.coerceTransitionTimeMillis(transitionTime)
            binding.supperBanner.setTransitionTimeMillis(coercedTime)
        }
        binding.etSupperBannerTransition.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val coercedTime = SupperBannerConfig.coerceTransitionTimeMillis(
                    binding.etSupperBannerTransition.text.toString().toLongOrNull()
                        ?: SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
                )
                binding.etSupperBannerTransition.setText(coercedTime.toString())
                Toast.makeText(
                    this,
                    getString(R.string.develop_settings_supper_banner_transition_applied, coercedTime),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupInvincibleMode() {
        val isInvincible = viewModel.isInvincibleModeEnabled()
        GameStateManager.isInvincible = isInvincible
        binding.switchInvincible.isChecked = isInvincible
        updateInvincibleUi(isInvincible)

        binding.switchInvincible.setOnCheckedChangeListener { _, enabled ->
            viewModel.setInvincibleModeEnabled(enabled)
            GameStateManager.isInvincible = enabled
            updateInvincibleUi(enabled)

            val msg = if (enabled) R.string.invincible_mode_on else R.string.invincible_mode_off
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateInvincibleUi(enabled: Boolean) {
        val chipTextRes = if (enabled) {
            R.string.develop_settings_invincible_status_on
        } else {
            R.string.develop_settings_invincible_status_off
        }
        val chipBackgroundRes = if (enabled) {
            R.drawable.develop_settings_status_active_bg
        } else {
            R.drawable.develop_settings_status_inactive_bg
        }
        val runtimeStatusTextRes = if (enabled) {
            R.string.develop_settings_invincible_runtime_on
        } else {
            R.string.develop_settings_invincible_runtime_off
        }

        binding.tvInvincibleChip.text = getString(chipTextRes)
        binding.tvInvincibleChip.setBackgroundResource(chipBackgroundRes)
        binding.tvInvincibleRuntimeStatus.text = getString(runtimeStatusTextRes)
    }

    private companion object {
        const val QR_TOOL_NOTIFICATION_CHANNEL_ID = "qr_tool_test_notifications"
        const val QR_TOOL_NOTIFICATION_ID = 1010
        const val QR_TOOL_NOTIFICATION_REQUEST_CODE = 1011
    }
}
