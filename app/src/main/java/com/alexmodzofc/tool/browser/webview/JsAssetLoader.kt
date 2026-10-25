package com.alexmodzofc.tool.browser.webview
import com.alexmodzofc.tool.browser.MainActivity

internal fun MainActivity.loadJsAsset(filename: String): String {
    return assets.open("JavaScript/$filename").bufferedReader().use { it.readText() }
}
