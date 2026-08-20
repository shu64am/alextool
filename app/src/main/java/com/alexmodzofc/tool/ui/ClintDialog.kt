package com.alexmodzofc.tool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.base.AlexToolActivity
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

/** Max height of a AlexToolDialog's scrollable content area before it starts scrolling. */
val AlexToolDialogContentMaxHeight = 440.dp

/** Space reserved for the title and footer rows, subtracted from the window height when
 *  capping the content area so the dialog never grows taller than the screen (e.g. in
 *  landscape, where the window is shorter than [AlexToolDialogContentMaxHeight] alone needs). */
private val AlexToolDialogChromeHeight = 140.dp

@Composable
internal fun AlexToolDialogStatusBarEffect(hideStatusBar: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current

    // Mirrors the old MaterialAlertDialogBuilder-era applyStatusBarFlagToDialog()'s
    // trackDialogShown()/trackDialogDismissed() bookkeeping: while any dialog (now: any
    // Compose dialog reaching this effect) is on screen, the hosting Activity's own
    // onWindowFocusChanged should skip re-asserting its status-bar visibility, so it doesn't
    // fight this dialog's own window over the status bar on a focus change (e.g. pulling down
    // the notification shade and back while a dialog is open).
    DisposableEffect(Unit) {
        val activity = try {
            context as? AlexToolActivity
        } catch (_: Exception) {
            null
        }
        try {
            activity?.trackDialogShown()
        } catch (_: Exception) {
            // Hosting activity may not be an AlexToolActivity; never crash the dialog for it.
        }
        onDispose {
            try {
                activity?.trackDialogDismissed()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(hideStatusBar) {
        val window = try {
            (view.parent as? DialogWindowProvider)?.window
        } catch (_: Exception) {
            null
        }
        if (window != null) {
            try {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                if (hideStatusBar) {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            } catch (_: Exception) {
                // Some device ROMs (e.g., MIUI) throw here; ignore to avoid dialog crash.
            }
        }
        onDispose {}
    }
}

@Composable
fun AlexToolDialogCancelFooter(onDismiss: () -> Unit) {
    val colors = LocalAlexToolColors.current
    Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_cancel), color = colors.primary, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * The app's shared dialog chrome: a themed rounded Surface with a title, a scrollable content
 * slot, and a footer slot, plus status-bar sync with the "hide status bar" preference. Used for
 * settings pickers, confirmations, and document viewing alike — reach for this rather than a new
 * MaterialAlertDialogBuilder or a one-off Surface/Dialog pair.
 */
@Composable
fun AlexToolDialog(
    title: String,
    hideStatusBar: Boolean,
    onDismiss: () -> Unit,
    cancelable: Boolean = true,
    footer: @Composable () -> Unit = { AlexToolDialogCancelFooter(onDismiss) },
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAlexToolColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
            dismissOnBackPress = cancelable,
            dismissOnClickOutside = cancelable
        )
    ) {
        AlexToolDialogStatusBarEffect(hideStatusBar)
        BoxWithConstraints {
            val maxContentHeight = (maxHeight - AlexToolDialogChromeHeight)
                .coerceIn(0.dp, AlexToolDialogContentMaxHeight)
            Surface(shape = RoundedCornerShape(24.dp), color = colors.popupBackground) {
                Column {
                    Text(
                        title,
                        color = colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
                    )
                    Column(
                        Modifier
                            .heightIn(max = maxContentHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp)
                    ) { content() }
                    footer()
                }
            }
        }
    }
}
