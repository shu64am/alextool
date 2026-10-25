package com.alexmodzofc.tool.browser.delegates

import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.browser.MainActivity
import com.alexmodzofc.tool.browser.dialogs.PopupAlertRequest
import com.alexmodzofc.tool.browser.webview.AlexToolWebViewClient

internal fun MainActivity.showPopupAlertDialog(newUrl: String, isIncognito: Boolean, sourceTabId: String? = null) {
    val sourceHost = tabManager.activeTab?.webView?.url
        ?.let { android.net.Uri.parse(it).host?.takeIf { h -> h.isNotEmpty() } ?: it }
        ?: getString(R.string.popup_alert_source_unknown)

    uiState.popupAlertRequest = PopupAlertRequest(
        sourceHost = sourceHost,
        newUrl = newUrl,
        onAllow = {
            val uri = android.net.Uri.parse(newUrl)
            val scheme = uri.scheme?.lowercase()
            val activeWebView = tabManager.activeTab?.webView
            val client = activeWebView?.webViewClient as? AlexToolWebViewClient
            if (scheme == "http" || scheme == "https") {
                if (client == null || !client.tryOpenInApp(activeWebView, uri)) {
                    openNewTab(isIncognito = isIncognito, url = newUrl, openerTabId = sourceTabId)
                }
            } else {
                openNewTab(isIncognito = isIncognito, url = newUrl, openerTabId = sourceTabId)
            }
        }
    )
}
