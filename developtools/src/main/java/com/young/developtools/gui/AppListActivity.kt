package com.young.developtools.gui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.developtools.R
import com.young.developtools.ui.theme.AccentGreen
import com.young.developtools.ui.theme.AircraftTheme
import com.young.developtools.ui.theme.BackgroundDark
import com.young.developtools.ui.theme.NeonDivider
import com.young.developtools.utils.DeveloperMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen installed-app browser that replaced the former modal dialog. The App
 * Browser module navigates here; system back returns to the assistant tools screen.
 * The module manifest applies Theme.Aircraft.Common and the Compose content wraps in
 * [AircraftTheme], so the persisted accent selection still drives the colors.
 */
class AppListActivity : BaseAircraftActivity() {

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        // Same developer-mode gate as the assistant entry point that opens this screen.
        if (!DeveloperMode.isEnabled(this)) {
            finish()
        }
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                AppListScreen(onBack = { finish() })
            }
        }
    }
}

/**
 * Queries launchable apps once per screen off the main thread, then renders
 * [AppListBody]. The search text and selected filter survive Activity recreation
 * through rememberSaveable inside [AppListBody]; loading and unavailable states are
 * retained, and icons load lazily per visible row. The back icon and normal system
 * back both return to the assistant tools screen.
 */
@Composable
internal fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { readInstalledApps(context) } }
        apps = result.getOrNull()
        loadFailed = result.isFailure
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        AppListHeader(onBack = onBack)
        NeonDivider()

        AppListBody(apps = apps, loadFailed = loadFailed)
    }
}

/** Same layout as the assistant tools header: back icon at the start, centered title. */
@Composable
private fun AppListHeader(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("btn_back")
                .padding(start = 4.dp)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.develop_settings_assistant_apps_title),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 8.dp)
        )
    }
}
