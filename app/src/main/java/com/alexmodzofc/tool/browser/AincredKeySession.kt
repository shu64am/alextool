package com.alexmodzofc.tool.browser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Minimal CRX-only Aincrad gateway session. No Shortner API or fallback UI. */
internal object AincredKeySession {
    private const val GATEWAY_URL = "https://aincradmods.com/getkey"
    private const val SPOOF_HOST = "aincradmods.com"
    private const val SPOOF_REFERER = "http://rodaemotor.com/"
    private val staticExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "css", "woff", "woff2", "ttf", "otf", "mp4", "webm", "mp3")
    private val generation = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startExtensionOnly(webView: WebView) {
        generation.incrementAndGet()
        webView.loadUrl(GATEWAY_URL, mapOf("Referer" to SPOOF_REFERER))
    }

    fun cancel() {
        generation.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
    }

    /** Reproduces the reference extension's host/referrer request behavior. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (generation.get() == 0 || !request.method.equals("GET", true)) return null
        val uri = request.url
        if (!uri.host.equals(SPOOF_HOST, true) || isStatic(uri.path)) return null
        return runCatching { fetchWithHeaders(request) }.getOrNull()
    }

    private fun fetchWithHeaders(request: WebResourceRequest): WebResourceResponse? {
        var currentUrl = request.url.toString()
        var hops = 0
        while (true) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                request.requestHeaders.forEach { (key, value) ->
                    if (!key.equals("Accept-Encoding", true) && !key.equals("If-None-Match", true) && !key.equals("If-Modified-Since", true)) {
                        setRequestProperty(key, value)
                    }
                }
                CookieManager.getInstance().getCookie(currentUrl)?.let { setRequestProperty("Cookie", it) }
                setRequestProperty("Referer", SPOOF_REFERER)
            }
            val code = conn.responseCode
            conn.headerFields["Set-Cookie"]?.forEach { CookieManager.getInstance().setCookie(currentUrl, it) }
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && location != null) {
                val next = URL(URL(currentUrl), location).toString()
                if (request.isForMainFrame) {
                    val html = "<script>location.replace(${org.json.JSONObject.quote(next)})</script>"
                    return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
                }
                if (++hops > 10) return null
                currentUrl = next
                continue
            }
            if (code in 300..399) return null
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val type = (conn.contentType ?: "application/octet-stream").substringBefore(';').trim()
            val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(conn.contentType.orEmpty())?.groupValues?.getOrNull(1)?.trim(' ', '"')
            return WebResourceResponse(type, charset, code, conn.responseMessage?.ifBlank { "OK" } ?: "OK", emptyMap(), stream ?: ByteArrayInputStream(ByteArray(0)))
        }
    }

    private fun isStatic(path: String?): Boolean = path?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in staticExtensions
}
