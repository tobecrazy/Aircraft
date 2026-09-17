package com.young.aircraft.gui.dialogs

import android.app.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.young.aircraft.ui.theme.AircraftTheme

import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Provided by every dialog host; negative buttons dismiss even without a caller callback. */
internal val LocalDialogDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Hosts Compose content inside a classic Dialog window by borrowing the host activity's
 * lifecycle / ViewModelStore / saved-state owners. Safe here because every dialog using
 * this helper is modal and dismissed before its host goes away.
 *
 * Shows the dialog itself: appcompat AlertDialog replaces any pre-show
 * `setContentView` output with its own template during `show()`, so the
 * ComposeView must be installed afterwards.
 */
fun Dialog.setDialogComposeContent(
    host: androidx.activity.ComponentActivity,
    content: @Composable () -> Unit
) {
    val composeView = ComposeView(context)
    composeView.setViewTreeLifecycleOwner(host)
    composeView.setViewTreeViewModelStoreOwner(host)
    composeView.setViewTreeSavedStateRegistryOwner(host)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    composeView.setContent {
        AircraftTheme {
            CompositionLocalProvider(LocalDialogDismiss provides { dismiss() }) {
                content()
            }
        }
    }
    if (!isShowing) show()
    setContentView(composeView)
}
