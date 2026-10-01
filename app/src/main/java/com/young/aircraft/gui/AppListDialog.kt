package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.young.aircraft.R
import com.young.aircraft.gui.dialogs.LocalDialogDismiss
import com.young.aircraft.gui.dialogs.gameDialogPalette
import com.young.aircraft.ui.theme.TextSubtle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tag on the app-list LazyColumn; tests scroll it with performScrollToNode. */
internal const val AppListTag = "assistant_app_list"

/** One launchable package. Icon is loaded lazily per row, not stored here. */
internal data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean
)

internal enum class AppFilter { USER, SYSTEM, ALL }

/**
 * Launcher activities only, which is exactly the set this module can start and exactly what
 * the manifest `<queries>` block declares. An updated system app (Chrome, YouTube) counts as a
 * user app because that is how the user perceives it.
 */
internal fun readInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .mapNotNull { info ->
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            val flags = appInfo.flags
            InstalledApp(
                packageName = appInfo.packageName,
                label = info.loadLabel(pm).toString(),
                versionName = runCatching { pm.getPackageInfo(appInfo.packageName, 0).versionName }
                    .getOrNull().orEmpty(),
                isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Pure so the search/filter rules are testable without a PackageManager. */
internal fun filterApps(apps: List<InstalledApp>, query: String, filter: AppFilter): List<InstalledApp> {
    val q = query.trim()
    return apps.filter { app ->
        val typeOk = when (filter) {
            AppFilter.USER -> !app.isSystem
            AppFilter.SYSTEM -> app.isSystem
            AppFilter.ALL -> true
        }
        typeOk && (q.isEmpty() ||
            app.label.contains(q, ignoreCase = true) ||
            app.packageName.contains(q, ignoreCase = true))
    }
}

@Composable
internal fun AppListDialogContent() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val palette = gameDialogPalette(colors.primary)
    val dismiss = LocalDialogDismiss.current

    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.USER) }

    // ponytail: queried once per dialog; filtering is in-memory over a few hundred rows.
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { readInstalledApps(context) } }
        apps = result.getOrNull()
        loadFailed = result.isFailure
    }

    val visible = remember(apps, query, filter) {
        apps?.let { filterApps(it, query, filter) }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.statCardContainer, RoundedCornerShape(20.dp))
            .border(1.5.dp, palette.badgeBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.develop_settings_assistant_apps_title),
            color = palette.titleColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag(AppListSearchTag),
            placeholder = {
                Text(
                    text = stringResource(R.string.develop_settings_assistant_apps_search_hint),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = palette.badgeBorder,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
                cursorColor = colors.primary
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppFilter.entries.forEach { entry ->
                FilterChip(
                    selected = filter == entry,
                    onClick = { filter = entry },
                    label = {
                        Text(
                            text = stringResource(
                                when (entry) {
                                    AppFilter.USER -> R.string.develop_settings_assistant_apps_filter_user
                                    AppFilter.SYSTEM -> R.string.develop_settings_assistant_apps_filter_system
                                    AppFilter.ALL -> R.string.develop_settings_assistant_apps_filter_all
                                }
                            ),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = colors.onSurfaceVariant,
                        selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                        selectedLabelColor = colors.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == entry,
                        borderColor = palette.badgeBorder,
                        selectedBorderColor = colors.primary.copy(alpha = 0.45f)
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 360.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                loadFailed -> Text(
                    text = stringResource(R.string.develop_settings_assistant_unavailable),
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                apps == null -> CircularProgressIndicator(color = colors.primary)

                visible.isEmpty() -> Text(
                    text = stringResource(R.string.develop_settings_assistant_apps_empty),
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth().testTag(AppListTag)) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onLaunch = { context.launchApp(app.packageName) },
                            onDetails = { context.openAppDetails(app.packageName) }
                        )
                        HorizontalDivider(color = palette.dividerColor)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(48.dp)
                .background(colors.primary, RoundedCornerShape(12.dp))
                .clickable { dismiss?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(android.R.string.ok),
                color = colors.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

internal const val AppListSearchTag = "assistant_app_search"

@Composable
private fun AppRow(app: InstalledApp, onLaunch: () -> Unit, onDetails: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap() }
                .getOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                bitmap = requireNotNull(icon),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(modifier = Modifier.size(36.dp))
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = app.label,
                color = colors.onSurface,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName +
                    if (app.versionName.isBlank()) "" else
                        "  " + stringResource(R.string.develop_settings_assistant_apps_version_fmt, app.versionName),
                color = TextSubtle,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 48dp tall so the shortcut meets the minimum touch target even though the label is 10sp.
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .defaultMinSize(minHeight = 48.dp)
                .background(colors.primary.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                .clickable(onClick = onDetails)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.develop_settings_assistant_apps_details),
                color = colors.primary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun Context.launchApp(packageName: String) {
    // Every row came from a launcher query, so this only fails if the app is uninstalled
    // while the dialog is open.
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(intent)
}

private fun Context.openAppDetails(packageName: String) {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
