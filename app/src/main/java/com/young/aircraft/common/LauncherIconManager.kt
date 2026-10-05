package com.young.aircraft.common

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Applies one of the manifest launcher aliases selected by Remote Config. */
internal object LauncherIconManager {
    private val aliases = (1..5).map { "LauncherIcon$it" }

    fun apply(context: Context, variant: Int) {
        val selectedIndex = (variant - 1).takeIf { it in aliases.indices } ?: 0
        val packageManager = context.packageManager
        aliases.forEachIndexed { index, alias ->
            if (index == selectedIndex) return@forEachIndexed
            val component = ComponentName(context, "${context.packageName}.gui.$alias")
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        val selected = ComponentName(
            context,
            "${context.packageName}.gui.${aliases[selectedIndex]}"
        )
        packageManager.setComponentEnabledSetting(
            selected,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
