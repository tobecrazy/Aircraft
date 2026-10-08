package com.young.aircraft.gui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.RepoUpdateConfigStore
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextBright
import androidx.core.net.toUri

// Panel visuals mirror the About/Settings card treatment so the blocking gate
// reads as part of the same app rather than a stock Material dialog.
private val VersionCardBg = Color(0x20252A3A)
private val VersionCardBorder: Color
    @Composable get() = AccentGreen.copy(alpha = 0x22 / 255f)
private val VersionDivider: Color
    @Composable get() = AccentGreen.copy(alpha = 0x14 / 255f)

class MandatoryUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AircraftTheme {
                BackHandler(enabled = true) {}
                MandatoryUpdateScreen(
                    currentVersion = BuildConfig.VERSION_NAME,
                    minimumVersion = RepoUpdateConfigStore.getMinimumVersion(),
                    onUpdate = { openUpdatePage(this) }
                )
            }
        }
    }
}

internal const val GITHUB_RELEASES_BASE_URL = "https://github.com/tobecrazy/Aircraft/releases/tag"

/**
 * Builds the GitHub release tag URL for [version], tolerating a missing "V"
 * prefix. Tags use the form `V1.4.2` while Remote Config versions are plain
 * `1.4.2`, so both inputs must resolve to the same page. Returns null when
 * the version is blank.
 */
internal fun githubReleaseTagUrl(version: String): String? {
    val trimmed = version.trim().removePrefix("v").removePrefix("V")
    if (trimmed.isEmpty()) return null
    return "$GITHUB_RELEASES_BASE_URL/V$trimmed"
}

internal fun openUpdatePage(context: Context) {
    val configuredUrl = RepoUpdateConfigStore.getUpdateUrl().trim()
    val latestVersion = RepoUpdateConfigStore.getLatestVersion().trim()
    val minimumVersion = RepoUpdateConfigStore.getMinimumVersion().trim()
    val marketUrl = "market://details?id=${context.packageName}"
    val playStoreUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"

    // GitHub sits ahead of the Play fallbacks: market:// and play.google.com
    // have no handler on mainland-China devices, while the release page works
    // everywhere a browser exists.
    val candidates = listOf(
        configuredUrl.ifBlank { null },
        githubReleaseTagUrl(latestVersion.ifBlank { minimumVersion }),
        marketUrl,
        playStoreUrl
    ).filterNotNull()

    for (url in candidates) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next candidate.
        }
    }
    // Keep the update prompt visible when the device has no store or browser.
}

@Composable
private fun MandatoryUpdateScreen(
    currentVersion: String,
    minimumVersion: String,
    onUpdate: () -> Unit
) {
    val criticalColor = MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .maxContentWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hero: circular badge with the tinted system "update" arrow.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(criticalColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_upward_black_24dp),
                    contentDescription = null,
                    tint = criticalColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Status pill.
            Text(
                text = stringResource(R.string.remote_update_required_status),
                color = criticalColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp,
                modifier = Modifier
                    .background(criticalColor.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.remote_update_required_title),
                modifier = Modifier.semantics { heading() },
                color = TextBright,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(
                    R.string.remote_update_required_message,
                    currentVersion,
                    minimumVersion
                ),
                color = TextBody,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Structured version comparison so the required target is scannable.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = VersionCardBg,
                border = BorderStroke(1.dp, VersionCardBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    VersionRow(
                        label = stringResource(R.string.remote_update_current_version),
                        version = currentVersion,
                        valueColor = TextBody
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(VersionDivider)
                    )
                    VersionRow(
                        label = stringResource(R.string.remote_update_minimum_version),
                        version = minimumVersion,
                        valueColor = AccentGreen
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.remote_update_required_action),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, version: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = version,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    }
}