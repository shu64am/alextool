package com.alexmodzofc.tool.extratooling

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Per-script JavascriptInterface (`GMBridge_<id>`) exposing GM_* operations to user scripts.
 * Storage keys live under `scriptId:key` in a dedicated SharedPreferences file, mirroring the
 * reference toolkit's `GM_<scriptId>` prefs semantics.
 *
 * @param onOpenInTab called on the main thread when a script asks to open a URL in a tab.
 * @param pageView the WebView the bridge evaluates callbacks against (used by GM_xmlhttpRequest).
 */
class GMBridge(
    private val appContext: Context,
    private val script: UserScriptStore.UserScript,
    private val onOpenInTab: (String) -> Unit = {},
    private val pageView: () -> WebView? = { null }
) {

    companion object {
        private const val TAG = "AlexTool-GMBridge"
        private const val ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val MAIN = Handler(Looper.getMainLooper())
    }

    private val prefs by lazy {
        appContext.getSharedPreferences("AlexToolScriptData_${script.id}", Context.MODE_PRIVATE)
    }

    @JavascriptInterface
    fun getValue(key: String): String = getValue(key, "undefined")

    @JavascriptInterface
    fun getValue(key: String, defaultJson: String): String {
        return try { prefs.getString(key, defaultJson) ?: defaultJson } catch (e: Exception) { defaultJson }
    }

    @JavascriptInterface
    fun setValue(key: String, value: String) {
        try { prefs.edit().putString(key, value).apply() } catch (e: Exception) { ignored(e) }
    }

    @JavascriptInterface
    fun deleteValue(key: String) {
        try { prefs.edit().remove(key).apply() } catch (e: Exception) { ignored(e) }
    }

    @JavascriptInterface
    fun listValues(): String {
        return try {
            val keys = prefs.all.keys
            val arr = JSONArray()
            keys.forEach { arr.put(it) }
            arr.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun setClipboard(text: String) {
        try {
            val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            manager?.setPrimaryClip(ClipData.newPlainText("AlexToolGM", text))
            Log.d(TAG, "GM_setClipboard: text copied for script ${script.name}")
        } catch (e: Exception) {
            ignored(e)
        }
    }

    @JavascriptInterface
    fun openInTab(url: String) {
        MAIN.post { try { onOpenInTab(url) } catch (e: Exception) { ignored(e) } }
    }

    @JavascriptInterface
    fun showNotification(text: String) {
        MAIN.post {
            try {
                android.widget.Toast.makeText(appContext, text, android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                ignored(e)
            }
        }
    }

    @JavascriptInterface
    fun log(vararg args: String) {
        Log.d(TAG, "[${script.name}] ${args.joinToString(" ")}")
    }

    /**
     * GM_xmlhttpRequest — full reference-style XHR: HttpURLConnection on a worker thread with
     * POST/PUT body support, custom headers, 15 s timeouts, mobile UA, and a JSON response-headers
     * object delivered to the page via `window._gmXhrCallback(id, status, text, headersJson)`.
     */
    @JavascriptInterface
    fun xmlhttpRequest(cbId: String, method: String, url: String, headersJson: String, body: String) {
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method.uppercase()
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", ANDROID_UA)
                    try {
                        val headers = JSONObject(headersJson)
                        val keys = headers.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            setRequestProperty(k, headers.getString(k))
                        }
                    } catch (e: Exception) {
                        // invalid headers JSON — continue with defaults
                    }
                    if (body.isNotEmpty() && (method.equals("POST", true) || method.equals("PUT", true))) {
                        doOutput = true
                        outputStream.use { os -> os.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                val status = conn.responseCode
                val text = StringBuilder()
                BufferedReader(
                    InputStreamReader(
                        if (status >= 400) conn.errorStream else conn.inputStream, Charsets.UTF_8
                    )
                ).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) text.append(line).append("\n")
                }
                val respHeaders = JSONObject()
                conn.headerFields.forEach { (key, values) ->
                    if (key != null && values != null && values.isNotEmpty()) respHeaders.put(key, values[0])
                }
                val escapedText = text.toString()
                    .replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                    .replace("\r", "\\r").replace("`", "\\`")
                val escapedHeaders = respHeaders.toString().replace("'", "\\'")
                MAIN.post {
                    try {
                        pageView()?.evaluateJavascript(
                            "window._gmXhrCallback('$cbId',$status,`$escapedText`,'$escapedHeaders');", null
                        )
                    } catch (e: Exception) {
                        ignored(e)
                    }
                }
            } catch (e: Exception) {
                MAIN.post {
                    try {
                        pageView()?.evaluateJavascript("window._gmXhrCallback('$cbId',0,'','');", null)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }.start()
    }

    /** GM_xmlhttpRequest abort — drops the pending page-side callback. */
    @JavascriptInterface
    fun abortXhr(cbId: String) {
        MAIN.post {
            try {
                pageView()?.evaluateJavascript("delete window._gmXhrCallbacks['$cbId'];", null)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun ignored(e: Exception) {
        Log.w(TAG, "GMBridge error", e)
    }
}
