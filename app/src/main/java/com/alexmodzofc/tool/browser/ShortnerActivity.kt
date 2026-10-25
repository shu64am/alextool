package com.alexmodzofc.tool.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/** Standalone port of the reference Shortner_1.0 activity. */
class ShortnerActivity : Activity() {
    private lateinit var portal: WebView
    private lateinit var root: FrameLayout
    private var injectedExtensionUrl: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        root = FrameLayout(this)
        portal = createPortalWebView()
        // The Shortner home UI is intentionally not shown. This screen is
        // the single direct extension/key flow.
        root.addView(portal, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        showPortal()
        startKeySession()
    }

    private fun createPortalWebView() = WebView(this).apply {
        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true; settings.useWideViewPort = true
        settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true; settings.setSupportMultipleWindows(true)
        settings.allowFileAccess = false; settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) { injectedExtensionUrl = null }
            override fun onPageFinished(v: WebView, url: String) {
                CookieManager.getInstance().flush()
                if (Uri.parse(url).host?.equals("aincradmods.com", true) == true && injectedExtensionUrl != url) {
                    injectedExtensionUrl = url
                    runCatching {
                        val script = assets.open("shortner/0.js").bufferedReader().use { it.readText() }
                        // 0.js must run in the top-level page context so it can
                        // use the gateway's window, cookies, and session state.
                        v.evaluateJavascript(script, null)
                    }
                }
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: android.webkit.WebResourceError) {
                if (r.isForMainFrame) Toast.makeText(this@ShortnerActivity, "Network error, tap again", Toast.LENGTH_SHORT).show()
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                AincredKeySession.intercept(r) ?: super.shouldInterceptRequest(v, r)
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val scheme = r.url.scheme?.lowercase() ?: return true
                if (scheme in setOf("http", "https", "about", "file", "data", "blob")) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { }
            override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
                pendingGeo = origin to cb
                requestPermissions(arrayOf("android.permission.ACCESS_FINE_LOCATION"), 501)
            }
            override fun onPermissionRequest(req: PermissionRequest) {
                runOnUiThread {
                    val needs = req.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    if (needs.isEmpty()) req.grant(req.resources) else {
                        pendingPermissionRequest = req
                        requestPermissions(arrayOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"), 502)
                    }
                }
            }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null); fileCallback = cb
                return runCatching { startActivityForResult(params.createIntent(), 503); true }.getOrDefault(false)
            }
            override fun onCreateWindow(v: WebView, dialog: Boolean, gesture: Boolean, msg: android.os.Message): Boolean {
                val transport = msg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = portal; msg.sendToTarget(); return true
            }
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view; customViewCallback = cb
                addContentView(view, ViewGroup.LayoutParams(-1, -1))
            }
            override fun onHideCustomView() { customView?.let { (it.parent as? ViewGroup)?.removeView(it) }; customView = null; customViewCallback?.onCustomViewHidden(); customViewCallback = null }
        }
        setDownloadListener(DownloadListener { url, ua, disposition, mime, _ ->
            runCatching {
                val req = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mime); addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty()); addRequestHeader("User-Agent", ua)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, mime))
                }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this@ShortnerActivity, "Downloading…", Toast.LENGTH_SHORT).show()
            }
        })
        visibility = View.GONE
    }

    private fun showPortal() { portal.visibility = View.VISIBLE }
    private fun closePortal() { AincredKeySession.cancel(); injectedExtensionUrl = null; portal.stopLoading(); finish() }
    private fun startKeySession() { showPortal(); AincredKeySession.startExtensionOnly(portal) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 503) { fileCallback?.onReceiveValue(if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null); fileCallback = null }
    }
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 501) pendingGeo?.let { it.second.invoke(it.first, results.isNotEmpty() && results[0] == 0, false) }
        if (code == 502) { pendingPermissionRequest?.grant(pendingPermissionRequest?.resources); pendingPermissionRequest = null }
    }
    override fun onBackPressed() { if (customView != null) (portal.webChromeClient as? WebChromeClient)?.onHideCustomView() else if (portal.visibility == View.VISIBLE) { if (portal.canGoBack()) portal.goBack() else closePortal() } else super.onBackPressed() }
    override fun onDestroy() { AincredKeySession.cancel(); portal.destroy(); super.onDestroy() }
}
