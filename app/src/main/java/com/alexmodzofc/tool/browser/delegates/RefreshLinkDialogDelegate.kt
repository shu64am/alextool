package com.alexmodzofc.tool.browser.delegates

import android.widget.Toast
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.browser.MainActivity
import com.alexmodzofc.tool.browser.dialogs.RefreshLinkDialogRequest
import com.alexmodzofc.tool.downloads.AlexToolDownloadManager

internal fun MainActivity.showRefreshLinkDownloadDialog(
    url: String,
    filename: String,
    userAgent: String,
    referer: String,
    cookies: String,
    session: MainActivity.RefreshLinkSession
) {
    uiState.refreshLinkDialogRequest = RefreshLinkDialogRequest(
        existingFilename = session.filename,
        onUpdateExisting = {
            AlexToolDownloadManager.updateDownloadUrl(session.downloadId, url)
            Toast.makeText(this, getString(R.string.refresh_link_updated_toast, session.filename), Toast.LENGTH_SHORT).show()
            refreshLinkSession = null
        },
        onAddNew = { showDownloadDialog(url, filename, userAgent, referer, cookies) }
    )
}
