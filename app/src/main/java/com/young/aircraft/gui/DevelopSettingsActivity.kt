package com.young.aircraft.gui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.DevelopSettingsViewModel

// Panel visuals lifted from develop_settings_hero_bg / develop_settings_panel_bg /
// develop_settings_danger_bg / device_info_gauge_bg / develop_settings_status_*_bg /
// btn_reject_bg and the legacy layout's hardcoded section colors.
private val HeroBorder = Color(0x3300FF88)
private val PanelBg = Color(0x22252A3A)
private val PanelBorder = Color(0x2200FF88)
private val DangerBorder = Color(0x44FF5555)
private val GaugeBg = Color(0x18FFFFFF)
private val StatusActiveBg = Color(0x2600FF88)
private val StatusActiveBorder = Color(0x6600FF88)
private val StatusInactiveBg = Color(0x18FFFFFF)
private val StatusInactiveBorder = Color(0x28FFFFFF)
private val CtaBg = Color(0x2600FF88)
private val CtaBorder = Color(0x6600FF88)
private val RejectBg = Color(0x1A161A26)
private val DangerRed = Color(0xFFFF5555)
private val BannerAccent = Color(0xFF4EA1FF)
private val BannerSectionLabel = Color(0xFF8CC6FF)
private val DangerBadgeText = Color(0xFFFF6F7E)
private val CrashSummary = Color(0xFFD3AAB2)
private val RuntimeLabel = Color(0xFF8C97AD)
private val HintMuted = Color(0xFF7F8AA3)
private val SectionLabel = Color(0x66FFFFFF)
private val HeroDivider = Color(0x16FFFFFF)

class DevelopSettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: DevelopSettingsViewModel
    private var invincible by mutableStateOf(false)
    private var versionBadgeClickCount = 0

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

        enableEdgeToEdge()
        viewModel = ViewModelProvider(this, DevelopSettingsViewModel.Factory(this))[DevelopSettingsViewModel::class.java]
        invincible = viewModel.isInvincibleModeEnabled()
        GameStateManager.isInvincible = invincible

        setContent {
            AircraftTheme {
                DevelopSettingsScreen(
                    invincible = invincible,
                    onInvincibleChange = ::setInvincibleMode,
                    onVersionBadgeClick = ::onVersionBadgeClick,
                    onBack = { finish() },
                    onOpenRichText = { startActivity(Intent(this, RichTextEditorActivity::class.java)) },
                    onOpenAssistantTools = { startActivity(Intent(this, AndroidDevAssistantToolsActivity::class.java)) },
                    onNotificationTest = ::showNotificationConfirmationDialog,
                    onOpenBannerItem = { item ->
                        startActivity(ShowImageDetailsActivity.createIntent(this, item))
                    }
                )
            }
        }
    }

    /** Reads the live snapshot state, so rapid taps never act on a stale captured value. */
    private fun onVersionBadgeClick() {
        if (++versionBadgeClickCount % 8 == 0) {
            setInvincibleMode(!invincible)
        }
    }

    private fun setInvincibleMode(enabled: Boolean) {
        viewModel.setInvincibleModeEnabled(enabled)
        GameStateManager.isInvincible = enabled
        invincible = enabled

        val msg = if (enabled) R.string.invincible_mode_on else R.string.invincible_mode_off
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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

    private companion object {
        const val QR_TOOL_NOTIFICATION_CHANNEL_ID = "qr_tool_test_notifications"
        const val QR_TOOL_NOTIFICATION_ID = 1010
        const val QR_TOOL_NOTIFICATION_REQUEST_CODE = 1011
    }
}

@Composable
internal fun DevelopSettingsScreen(
    invincible: Boolean,
    onInvincibleChange: (Boolean) -> Unit,
    onVersionBadgeClick: () -> Unit,
    onBack: () -> Unit,
    onOpenRichText: () -> Unit,
    onOpenAssistantTools: () -> Unit,
    onNotificationTest: () -> Unit,
    onOpenBannerItem: (SupperBannerItem) -> Unit
) {
    var autoPlay by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(true) }
    var showIndicator by remember { mutableStateOf(true) }
    var transitionInput by remember { mutableStateOf(SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS.toString()) }
    val context = LocalContext.current
    // Resolved in composition so the focus-lost toast stays configuration-aware (lint).
    val transitionAppliedTemplate = stringResource(
        R.string.develop_settings_supper_banner_transition_applied
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        DevelopHeader(onBack = onBack)
        NeonDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            HeroPanel(
                invincible = invincible,
                onVersionBadgeClick = onVersionBadgeClick
            )

            SectionHeader(R.string.develop_settings_section_supper_banner, BannerAccent, BannerSectionLabel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, PanelBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    BadgePill(
                        text = stringResource(R.string.develop_settings_supper_banner_badge),
                        tint = BannerSectionLabel
                    )

                    Text(
                        text = stringResource(R.string.develop_settings_supper_banner_title),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Text(
                        text = stringResource(R.string.develop_settings_supper_banner_summary),
                        color = TextSubtle,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(190.dp),
                        factory = { bannerContext ->
                            SupperBannerView(bannerContext).apply {
                                setItems(buildBannerItems(bannerContext))
                                setOnBannerClickListener { item, _ -> onOpenBannerItem(item) }
                                setIndicatorCustomizer { indicator, selected, _ ->
                                    indicator.typeface = Typeface.MONOSPACE
                                    indicator.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                                    indicator.setTextColor(
                                        if (selected) android.graphics.Color.parseColor("#061317")
                                        else android.graphics.Color.parseColor("#B8C9E8")
                                    )
                                    indicator.background = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(
                                            if (selected) android.graphics.Color.parseColor("#4EA1FF")
                                            else android.graphics.Color.parseColor("#2E3A4C")
                                        )
                                        setStroke(
                                            2,
                                            android.graphics.Color.parseColor(if (selected) "#D8F0FF" else "#617089")
                                        )
                                    }
                                }
                            }
                        },
                        update = { banner ->
                            banner.setAutoPlayEnabled(autoPlay)
                            banner.setShowImageInfo(showInfo)
                            banner.setShowIndicator(showIndicator)
                            banner.setTransitionTimeMillis(
                                transitionInput.toLongOrNull()
                                    ?.let(SupperBannerConfig::coerceTransitionTimeMillis)
                                    ?: SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
                            )
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledSwitch(
                            label = stringResource(R.string.develop_settings_supper_banner_auto_play),
                            checked = autoPlay,
                            onCheckedChange = { autoPlay = it },
                            modifier = Modifier.weight(1f)
                        )
                        LabeledSwitch(
                            label = stringResource(R.string.develop_settings_supper_banner_show_info),
                            checked = showInfo,
                            onCheckedChange = { showInfo = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledSwitch(
                            label = stringResource(R.string.develop_settings_supper_banner_show_indicator),
                            checked = showIndicator,
                            onCheckedChange = { showIndicator = it },
                            modifier = Modifier.weight(1f)
                        )
                        TransitionField(
                            value = transitionInput,
                            onValueChange = { transitionInput = it },
                            onFocusLost = {
                                val coercedTime = SupperBannerConfig.coerceTransitionTimeMillis(
                                    transitionInput.toLongOrNull() ?: SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
                                )
                                transitionInput = coercedTime.toString()
                                Toast.makeText(
                                    context,
                                    transitionAppliedTemplate.format(coercedTime),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SectionHeader(R.string.develop_settings_section_gameplay, AccentGreen, SectionLabel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, PanelBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.develop_settings_invincible_title),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = stringResource(R.string.develop_settings_invincible_description),
                                color = TextSubtle,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Switch(
                            checked = invincible,
                            onCheckedChange = onInvincibleChange,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("switch_invincible"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentGreen,
                                checkedTrackColor = Color(0x6600FF88),
                                checkedBorderColor = Color(0x6600FF88),
                                uncheckedThumbColor = Color(0x88FFFFFF),
                                uncheckedTrackColor = Color(0x33FFFFFF),
                                uncheckedBorderColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                    Text(
                        text = stringResource(R.string.develop_settings_invincible_hint),
                        color = HintMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            SectionHeader(R.string.develop_settings_section_tools, AccentGreen, SectionLabel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, PanelBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    BadgePill(
                        text = stringResource(R.string.rich_text_section_title),
                        tint = TextBright
                    )
                    Text(
                        text = stringResource(R.string.rich_text_card_title),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.rich_text_card_summary),
                        color = TextSubtle,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    ToolButton(
                        textRes = R.string.test_rich_text_editor,
                        onClick = onOpenRichText,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .testTag("btn_test_rich_text")
                    )
                    ToolButton(
                        textRes = R.string.develop_settings_assistant_tools_button,
                        onClick = onOpenAssistantTools,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("btn_android_dev_assistant_tools")
                    )
                    ToolButton(
                        textRes = R.string.develop_settings_notification_button,
                        onClick = onNotificationTest,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("btn_notification")
                    )
                }
            }

            SectionHeader(R.string.develop_settings_section_danger, DangerRed, Color(0xFFFF9EA8))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, DangerBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    BadgePill(
                        text = stringResource(R.string.develop_settings_danger_badge),
                        tint = DangerBadgeText
                    )
                    Text(
                        text = stringResource(R.string.develop_settings_crash_title),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = stringResource(R.string.develop_settings_crash_summary),
                        color = CrashSummary,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    RejectButton(
                        textRes = R.string.test_crash,
                        onClick = { throw RuntimeException("Test Crash") }, // Force a crash
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .testTag("btn_test_crash")
                    )
                }
            }
        }
    }
}

/** Same 7-item set as the legacy setupSupperBanner(): five local backgrounds + two network images. */
private fun buildBannerItems(context: android.content.Context): List<SupperBannerItem> {
    val localDescription = context.getString(R.string.develop_settings_supper_banner_local_description)
    val networkDescription = context.getString(R.string.develop_settings_supper_banner_network_description)
    return listOf(
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
}

@Composable
private fun DevelopHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("btn_back")
                .padding(start = 4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.develop_settings_title),
            modifier = Modifier.align(Alignment.Center),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp
        )
    }
}

@Composable
private fun HeroPanel(invincible: Boolean, onVersionBadgeClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .border(1.dp, HeroBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgePill(text = stringResource(R.string.develop_settings_debug_badge), tint = AccentGreen)
            Spacer(modifier = Modifier.width(8.dp))
            BadgePill(
                text = stringResource(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME),
                tint = TextBright,
                onClick = onVersionBadgeClick,
                modifier = Modifier.testTag("version_badge")
            )
        }

        Text(
            text = stringResource(R.string.develop_settings_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 14.dp)
        )

        Text(
            text = stringResource(R.string.develop_settings_banner_summary),
            color = TextBody,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(1.dp)
                .background(HeroDivider)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.develop_settings_runtime_label),
                    color = RuntimeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.15.sp
                )
                Text(
                    text = stringResource(
                        if (invincible) {
                            R.string.develop_settings_invincible_runtime_on
                        } else {
                            R.string.develop_settings_invincible_runtime_off
                        }
                    ),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            val active = invincible
            Text(
                text = stringResource(
                    if (active) {
                        R.string.develop_settings_invincible_status_on
                    } else {
                        R.string.develop_settings_invincible_status_off
                    }
                ),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(
                        if (active) StatusActiveBg else StatusInactiveBg,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (active) StatusActiveBorder else StatusInactiveBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun BadgePill(
    text: String,
    tint: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = tint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(GaugeBg, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionHeader(titleRes: Int, barColor: Color, labelColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(barColor)
        )
        Text(
            text = stringResource(titleRes),
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextBright,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentGreen,
                checkedTrackColor = Color(0x6600FF88),
                checkedBorderColor = Color(0x6600FF88),
                uncheckedThumbColor = Color(0x88FFFFFF),
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedBorderColor = Color(0x33FFFFFF)
            )
        )
    }
}

@Composable
private fun TransitionField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit)) },
        modifier = modifier.onFocusChanged { focus -> if (!focus.isFocused) onFocusLost() },
        placeholder = { Text(stringResource(R.string.develop_settings_supper_banner_transition_hint)) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = BannerAccent,
            unfocusedBorderColor = Color(0xFF3A4658),
            cursorColor = BannerAccent
        )
    )
}

@Composable
private fun ToolButton(textRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CtaBg, RoundedCornerShape(4.dp))
            .border(1.dp, CtaBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(textRes),
            color = AccentGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.15.sp
        )
    }
}

@Composable
private fun RejectButton(textRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(RejectBg, RoundedCornerShape(4.dp))
            .border(1.dp, DangerRed, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(textRes),
            color = DangerRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.15.sp
        )
    }
}
