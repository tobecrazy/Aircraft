package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.utils.BitmapUtils
import kotlinx.coroutines.launch

/**
 * Lets the user act on the two promises the policy makes about user-controlled data:
 * deleting everything stored on the device, and withdrawing consent (which re-opens the
 * consent gate on next launch).
 */
class DataPrivacyActivity : BaseAircraftActivity() {

    private lateinit var repository: SettingsRepository

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        repository = SettingsRepository(this)
    }

    override fun initializeUI() {
        supportActionBar?.hide()

        setContent {
            AircraftTheme {
                DataPrivacyScreen(
                    policyVersion = AircraftConstants.PrivacyPolicy.POLICY_VERSION,
                    onBack = { finish() },
                    onViewPolicy = { openPolicy() },
                    onDeleteData = ::confirmDeleteData,
                    onWithdrawConsent = ::confirmWithdrawConsent
                )
            }
        }
    }

    private fun openPolicy() {
        startActivity(Intent(this, PrivacyPolicyActivity::class.java))
    }

    private fun confirmWithdrawConsent() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCancelable(true)
            .setTitle(R.string.privacy_withdraw_title)
            .setMessage(R.string.privacy_withdraw_summary)
            .setPositiveButton(R.string.privacy_withdraw_confirm) { _, _ ->
                repository.setPrivacyPolicyAccepted(false)
                ThemedMessage.makeText(this, R.string.privacy_withdraw_done, ThemedMessage.LENGTH_SHORT)
                    .show()
            }
            .setNegativeButton(R.string.history_cancel, null)
            .show()
    }

    private fun confirmDeleteData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCancelable(true)
            .setTitle(R.string.privacy_delete_data_title)
            .setMessage(R.string.privacy_delete_data_summary)
            .setPositiveButton(R.string.privacy_delete_data_confirm) { _, _ ->
                deleteAllData()
            }
            .setNegativeButton(R.string.history_cancel, null)
            .show()
    }

    /** Deletes game rows, cached images and bitmap caches; acceptance is deliberately kept. */
    private fun deleteAllData() {
        lifecycleScope.launch {
            val result = runCatching {
                DatabaseProvider.getDatabase(this@DataPrivacyActivity)
                    .playerGameDataDao()
                    .deleteAll()
                repository.clearCachedGameData()
                BitmapUtils.clearCaches()
            }
            val messageRes = if (result.isSuccess) {
                R.string.privacy_delete_data_done
            } else {
                R.string.privacy_delete_data_failed
            }
            ThemedMessage.makeText(this@DataPrivacyActivity, messageRes, ThemedMessage.LENGTH_SHORT)
                .show()
        }
    }
}

private val DestructiveRed = Color(0xFFFF5555)

@Composable
internal fun DataPrivacyScreen(
    policyVersion: Int,
    onBack: () -> Unit,
    onViewPolicy: () -> Unit,
    onDeleteData: () -> Unit,
    onWithdrawConsent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(ScreenBg)) {
        SettingsHeader(
            title = stringResource(R.string.privacy_data_screen_title),
            onBack = onBack
        )
        NeonDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp).padding(horizontal = 14.dp)) {
                Text(
                    text = stringResource(R.string.privacy_version_label, policyVersion),
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                DataActionRow(
                    title = stringResource(R.string.privacy_policy_title),
                    summary = stringResource(R.string.privacy_policy_summary),
                    onClick = onViewPolicy,
                    testTag = "row_view_policy"
                )
                DataActionRow(
                    title = stringResource(R.string.privacy_withdraw_title),
                    summary = stringResource(R.string.privacy_withdraw_summary),
                    destructive = true,
                    onClick = onWithdrawConsent,
                    testTag = "row_withdraw_consent"
                )
                DataActionRow(
                    title = stringResource(R.string.privacy_delete_data_title),
                    summary = stringResource(R.string.privacy_delete_data_summary),
                    destructive = true,
                    onClick = onDeleteData,
                    testTag = "row_delete_data"
                )
            }
        }
    }
}

@Composable
private fun DataActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    testTag: String,
    destructive: Boolean = false
) {
    val accent = if (destructive) DestructiveRed else AccentGreen
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(TileBg, RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = summary,
            color = TextBody,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}