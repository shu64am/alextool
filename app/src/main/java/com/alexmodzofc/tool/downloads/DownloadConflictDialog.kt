package com.alexmodzofc.tool.downloads
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Save

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.browser.sheets.ActionSheetRow
import com.alexmodzofc.tool.ui.AlexToolDialog
import com.alexmodzofc.tool.ui.AlexToolDialogCancelFooter

data class DownloadConflictDialogRequest(
    val onAddDuplicate: () -> Unit,
    val onOverride: () -> Unit,
    val onRename: () -> Unit
)

@Composable
internal fun DownloadConflictDialog(request: DownloadConflictDialogRequest, hideStatusBar: Boolean, onDismiss: () -> Unit) {
    AlexToolDialog(
        title = stringResource(R.string.download_conflict_title),
        hideStatusBar = hideStatusBar,
        onDismiss = onDismiss,
        footer = { AlexToolDialogCancelFooter(onDismiss) }
    ) {
        ActionSheetRow(androidx.compose.material.icons.Icons.Filled.Download, stringResource(R.string.download_conflict_add_duplicate)) { onDismiss(); request.onAddDuplicate() }
        ActionSheetRow(androidx.compose.material.icons.Icons.Filled.Save, stringResource(R.string.download_conflict_override)) { onDismiss(); request.onOverride() }
        ActionSheetRow(androidx.compose.material.icons.Icons.Filled.FormatSize, stringResource(R.string.download_conflict_rename)) { onDismiss(); request.onRename() }
    }
}
