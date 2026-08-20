package com.alexmodzofc.tool.browser.webview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.extratooling.ExtraToolingManager
import com.alexmodzofc.tool.extratooling.UserScriptInjector
import com.alexmodzofc.tool.extratooling.UserScriptStore
import com.alexmodzofc.tool.extratooling.UserScriptFetcher
import com.alexmodzofc.tool.extratooling.GMBridge
import java.io.ByteArrayInputStream
import com.alexmodzofc.tool.quiver.engine.QuiverGuardWebIntegration
import com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase
import com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AlexToolWebViewClient(
    private val prefs: SharedPreferences,
    private val isActive: () -> Boolean = { true },
    private val onPageStartedCallback: (String) -> Unit = {},
    private val onPageFinishedCallback: (String) -> Unit = {},
    private val onTabUrlUpdatedCallback: (WebView, String) -> Unit = { _, _ -> },
    private val getDesktopHeaders: () -> Map<String, String>? = { null },
    private val getTabId: () -> String = { "" }
) : WebViewClient() {

    @Volatile private var cachedPageUrl: String? = null

    private val cooldownDomains = mutableMapOf<String, Long>()
    private var pendingHeaderLoad: String? = null

    // Caches the Quiver Guard site-exception lookup for the current page's host.
    // shouldInterceptRequest() is invoked once per subresource - often dozens of
    // times per page load thanks to images alone - and SitePermissionManager.getState()
    // is a synchronous SQLite query plus a public-suffix domain computation, so
    // repeating it per-request rather than per-navigation was adding a real,
    // cumulative DB round-trip to every single image/script/style fetch on the
    // page. The exception state can't change mid-navigation from anything the
    // WebView itself does, so it's safe to compute once per host and reuse it
    // for every subsequent request until the next page starts loading.
    @Volatile private var exceptionCacheHost: String? = null
    @Volatile private var exceptionCacheValid: Boolean = false
    @Volatile private var exceptionCacheState: Boolean = false
    private val exceptionCacheLock = Any()

    companion object {
        private const val COOLDOWN_MS = 4000L
    }

    private fun isQuiverGuardExcepted(context: android.content.Context, pageHost: String): Boolean {
        if (exceptionCacheValid && exceptionCacheHost == pageHost) return exceptionCacheState
        synchronized(exceptionCacheLock) {
            if (exceptionCacheValid && exceptionCacheHost == pageHost) return exceptionCacheState
            val state = SitePermissionManager.getState(
                context, pageHost, SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
            ) != null
            exceptionCacheHost = pageHost
            exceptionCacheState = state
            exceptionCacheValid = true
            return state
        }
    }

    private fun registeredDomain(host: String): String =
        "https://$host".toHttpUrlOrNull()?.topPrivateDomain() ?: host

    private fun isInCooldown(host: String): Boolean {
        val domain = registeredDomain(host)
        val timestamp = cooldownDomains[domain] ?: return false
        if (System.currentTimeMillis() - timestamp >= COOLDOWN_MS) {
            cooldownDomains.remove(domain)
            return false
        }
        return true
    }

    private fun startCooldown(host: String) {
        cooldownDomains[registeredDomain(host)] = System.currentTimeMillis()
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        cachedPageUrl = url
        pendingHeaderLoad = null
        // Invalidate the per-host exception cache on every navigation so a
        // change made via the Quiver Guard exception toggle (which reloads the
        // tab) is picked up on the very next request instead of serving a
        // stale cached result for this host.
        exceptionCacheValid = false
        if (isActive()) onPageStartedCallback(url)
        // AlexTool UserScript GM engine — attach each enabled script's JavascriptInterface
        // (addJavascriptInterface is idempotent, safe to re-register every navigation) and
        // inject the page-side GM_xmlhttpRequest callback registry before any script runs.
        // Requires are fetched asynchronously; their cached content is used on the next load.
        // Never inject the GM engine or the page-side XHR registry on AlexTool
        // tooling deep links — the /links page with an alextrick param is our own
        // synthetic page; injected JS on it can trip the target server's
        // anti-bypass protection ("Smart Bypass Script Detected") after the
        // decoded target loads from the same navigation chain.
        val isToolingDeepLink = url.contains("alextrick=")
        if (!isToolingDeepLink) attachGMEngine(view, url)
    }

    private fun attachGMEngine(view: WebView, url: String) {
        if (!prefs.getBoolean("javascript_enabled", true)) return
        val ctx = view.context.applicationContext
        val scripts = runCatching { UserScriptStore.loadUserScripts(ctx) }.getOrNull() ?: return
        val enabled = scripts.filter { it.enabled && it.matchesUrl(url, isIframe = false) }
        if (enabled.isEmpty()) return
        for (script in enabled) {
            try {
                val openCb: (String) -> Unit = { t -> view.loadUrl(t) }
                val viewCb: () -> android.webkit.WebView? = { view }
                val bridge = GMBridge(
                    appContext = ctx,
                    script = script,
                    onOpenInTab = openCb,
                    pageView = viewCb
                )
                view.addJavascriptInterface(bridge, "GMBridge_${script.id.replace("-", "_")}")
            } catch (e: Exception) {
                // Tab detached — swallow.
            }
        }
        // Page-side callback registry + prefetch any missing @require content.
        runCatching {
            view.evaluateJavascript(UserScriptInjector.XHR_REGISTRY_JS, null)
        }
        val missing = enabled.filter { s -> s.requireUrls.any { u -> s.requireCache[u].isNullOrEmpty() } }
        if (missing.isNotEmpty()) {
            Thread {
                runCatching {
                    val fetched = UserScriptFetcher.fetchRequires(ctx, missing)
                    if (fetched) UserScriptStore.saveUserScripts(ctx, scripts)
                }
            }.start()
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        cachedPageUrl = url
        onTabUrlUpdatedCallback(view, url)
        if (isActive()) onPageFinishedCallback(url)
        // AlexTool UserScripts — inject enabled scripts whose @match/@include patterns
        // match the finished page, mirroring the reference toolkit's document-end phase.
        // document-idle runs 200 ms later for scripts that need the DOM settled.
        injectUserScripts(view, url, "document-end", isIframe = false)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (view.url == url) injectUserScripts(view, url, "document-idle", isIframe = false)
        }, 200)
    }

    private fun injectUserScripts(view: WebView, url: String, phase: String, isIframe: Boolean) {
        if (url.isBlank()) return
        // Never run user scripts on AlexTool tooling deep links — injected JS
        // on the bypass chain can trigger the target server's anti-bypass
        // protection ("Smart Bypass Script Detected").
        if (url.contains("alextrick=")) return
        val scripts = runCatching { UserScriptStore.loadUserScripts(view.context.applicationContext) }.getOrNull()
            ?: return
        for (script in scripts) {
            if (!script.enabled || script.runAt != phase || !script.matchesUrl(url, isIframe)) continue
            val js = UserScriptInjector.buildScriptInjection(script)
            try {
                view.evaluateJavascript(js, null)
            } catch (e: Exception) {
                // Tab may have been detached — swallow.
            }
        }
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        cachedPageUrl = url
        onTabUrlUpdatedCallback(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase() ?: return true

        if (scheme == "intent") {
            return handleIntentScheme(view, uri.toString())
        }

        if (scheme != "http" && scheme != "https") {
            return handleCustomScheme(view, uri)
        }

        if (scheme == "http" && request.isForMainFrame && prefs.getBoolean("https_only", true)) {
            val host = uri.host ?: ""
            val isIpAddress = host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
            if (!isIpAddress) {
                val httpsUri = uri.buildUpon().scheme("https").build()
                view.loadUrl(httpsUri.toString())
                return true
            }
        }

        if (request.isForMainFrame && tryOpenInApp(view, uri)) return true

        // AlexTool Tooling deep links — any `*/links?alextrick=...` (or the
        // `alextool.links/?alextrick=...` variant) carries a share-safe bypassed
        // target (the Link Toolkit's "build link" output). Decode the wrapped
        // target and load it directly, mirroring the reference toolkit.
        if (request.isForMainFrame) {
            val target = runCatching { ExtraToolingManager.decodeToolingTarget(uri.toString()) }.getOrNull()
            if (target != null) {
                val referer = uri.scheme + "://" + (uri.host ?: "") + "/"
                val headers = mapOf("Referer" to referer, "Origin" to referer)
                view.loadUrl(target, headers)
                return true
            }
        }

        if (request.isForMainFrame) {
            val uriStr = uri.toString()
            if (pendingHeaderLoad == uriStr) {
                pendingHeaderLoad = null
                return false
            }
            val headers = getDesktopHeaders()
            if (headers != null) {
                pendingHeaderLoad = uriStr
                view.loadUrl(uriStr, headers)
                return true
            }
        }

        return false
    }

    private fun handleIntentScheme(view: WebView, uriString: String): Boolean {
        return try {
            val intent = Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = view.context.packageManager
            val resolveInfo = resolveActivityCompat(pm, intent)
            val activity = view.context as? android.app.Activity

            if (resolveInfo != null && activity != null) {
                val appName = resolveInfo.loadLabel(pm).toString()
                val appIcon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                val sourceHost = view.url
                    ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                    ?:activity.getString(R.string.open_in_app_dialog_source_fallback)

                activity.runOnUiThread {
                    view.pauseTimers()
                    val mainActivity = activity as? com.alexmodzofc.tool.browser.MainActivity
                    if (mainActivity == null) {
                        try { activity.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                        view.resumeTimers()
                    } else {
                        mainActivity.uiState.openInAppRequest = com.alexmodzofc.tool.browser.webview.OpenInAppRequest(
                            host = sourceHost,
                            matches = listOf(com.alexmodzofc.tool.browser.webview.OpenInAppMatch(appName, appIcon, resolveInfo.activityInfo.packageName)),
                            onStayHere = { view.resumeTimers() },
                            onOpenApp = {
                                view.resumeTimers()
                                try { activity.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                            }
                        )
                    }
                }
            } else {
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty()) view.loadUrl(fallbackUrl)
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun handleCustomScheme(view: WebView, uri: Uri): Boolean {
        val context = view.context
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolveInfo = resolveActivityCompat(pm, intent)
        val activity = context as? android.app.Activity

        if (resolveInfo != null && activity != null) {
            val appName = resolveInfo.loadLabel(pm).toString()
            val appIcon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
            val sourceHost = view.url
                ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                ?: uri.scheme
                ?:activity.getString(R.string.open_in_app_dialog_source_fallback)

            activity.runOnUiThread {
                view.pauseTimers()
                val mainActivity = activity as? com.alexmodzofc.tool.browser.MainActivity
                if (mainActivity == null) {
                    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                    view.resumeTimers()
                } else {
                    mainActivity.uiState.openInAppRequest = com.alexmodzofc.tool.browser.webview.OpenInAppRequest(
                        host = sourceHost,
                        matches = listOf(com.alexmodzofc.tool.browser.webview.OpenInAppMatch(appName, appIcon, resolveInfo.activityInfo.packageName)),
                        onStayHere = { view.resumeTimers() },
                        onOpenApp = {
                            view.resumeTimers()
                            try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                        }
                    )
                }
            }
            return true
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveActivityCompat(pm: PackageManager, intent: Intent): ResolveInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.resolveActivity(intent, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
    }

    fun resolveAppMatches(uri: Uri, context: android.content.Context): List<ResolveInfo> {
        val pm = context.packageManager
        val browserPackages = (
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("http://example.com/")
            }) +
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("https://example.com/")
            })
        ).map { it.activityInfo.packageName }.toSet()

        return queryActivities(pm, Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }).filter { ri ->
            val pkg = ri.activityInfo.packageName
            pkg != context.packageName && pkg !in browserPackages
        }
    }

    fun tryOpenInApp(view: WebView, uri: Uri): Boolean {
        val uriStr = uri.toString()
        val host = uri.host ?: uriStr

        if (isInCooldown(host)) return false

        val context = view.context
        val pm = context.packageManager

        val browserPackages = (
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("http://example.com/")
            }) +
            queryActivities(pm, Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = Uri.parse("https://example.com/")
            })
        ).map { it.activityInfo.packageName }.toSet()

        val appMatches = queryActivities(pm, Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }).filter { ri ->
            val pkg = ri.activityInfo.packageName
            pkg != context.packageName && pkg !in browserPackages
        }

        if (appMatches.isEmpty()) return false

        val activity = context as? com.alexmodzofc.tool.browser.MainActivity ?: return false

        activity.runOnUiThread {
            val matches = appMatches.map { ri ->
                com.alexmodzofc.tool.browser.webview.OpenInAppMatch(
                    label = ri.loadLabel(pm).toString(),
                    icon = runCatching { ri.loadIcon(pm) }.getOrNull(),
                    packageName = ri.activityInfo.packageName
                )
            }
            activity.uiState.openInAppRequest = com.alexmodzofc.tool.browser.webview.OpenInAppRequest(
                host = host,
                matches = matches,
                onStayHere = {
                    startCooldown(host)
                    val h = getDesktopHeaders()
                    if (h != null) view.loadUrl(uriStr, h) else view.loadUrl(uriStr)
                },
                onOpenApp = { packageName ->
                    val specificIntent = Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(specificIntent) } catch (_: ActivityNotFoundException) {}
                }
            )
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (request.url.host == null) return super.shouldInterceptRequest(view, request)

        val quiverGuardEnabled = prefs.getBoolean("quiver_guard_enabled", false)
        // AlexTool Tooling deep links — mirror the reference toolkit's navigateTo():
        // a main-frame request carrying `alextrick=...` (on any host, e.g.
        // `https://vplink.in/links?alextrick=...`) is decoded and the wrapped
        // target is loaded directly into this WebView with Referer/Origin
        // headers. Returning a canned empty response here cancels the network
        // request before it ever reaches the origin server, so the user never
        // sees an upstream 404 for the synthetic `/links` path.
        if (request.isForMainFrame) {
            val target = runCatching { ExtraToolingManager.decodeToolingTarget(request.url.toString()) }.getOrNull()
            if (target != null) {
                val referer = request.url.scheme + "://" + (request.url.host ?: "") + "/"
                val headers = mapOf("Referer" to referer, "Origin" to referer)
                view.loadUrl(target, headers)
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            }
        }

        // AlexTool Domain Blocker — requests (subresource or main frame) to blocked
        // domains are cancelled; URLs carrying a alextrick query param are never blocked.
        // Mirrors the reference toolkit: the blocked link is also copied to the clipboard
        // with a toast so the user can still reach it when needed.
        val atBlocked = ExtraToolingManager.isDomainBlocked(view.context.applicationContext, request.url.toString())
        if (atBlocked) {
            if (request.isForMainFrame) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    copyBlockedUrl(view, request.url.toString())
                }
            }
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
        }

        if (quiverGuardEnabled) {
            val pageHost = cachedPageUrl?.let {
                runCatching { android.net.Uri.parse(it).host }.getOrNull()
            }
            val isExcepted = pageHost != null && isQuiverGuardExcepted(view.context.applicationContext, pageHost)
            if (!isExcepted) {
                val blocked = QuiverGuardWebIntegration.shouldInterceptRequest(
                    context = view.context.applicationContext,
                    request = request,
                    pageUrl = cachedPageUrl,
                    tabId = getTabId(),
                    isQuiverGuardEnabled = true
                )
                if (blocked != null) return blocked
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    /** Reference-style "Blocked domain detected — link copied!" clipboard+toast feedback. */
    private fun copyBlockedUrl(view: WebView, url: String) {
        try {
            val cm = view.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("AlexTool", url))
            android.widget.Toast.makeText(
                view.context.applicationContext,
                "Blocked domain detected — link copied!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            // Clipboard unavailable — silently ignore.
        }
    }
}
