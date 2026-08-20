package com.alexmodzofc.tool.browser
import com.alexmodzofc.tool.browser.delegates.*
import com.alexmodzofc.tool.browser.sheets.*
import com.alexmodzofc.tool.browser.suggestions.*
import com.alexmodzofc.tool.browser.webview.*

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.abs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.alexmodzofc.tool.base.AlexToolActivity
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.BuildConfig
import com.alexmodzofc.tool.crash.CrashHandler
import com.alexmodzofc.tool.downloads.AlexToolDownloadManager
import com.alexmodzofc.tool.tabs.TabManager
import com.alexmodzofc.tool.ui.AlexToolSnackbarHost
import com.alexmodzofc.tool.ui.OverlayHostActivity
import com.alexmodzofc.tool.ui.SnackbarHostActivity
import com.alexmodzofc.tool.ui.theme.AlexToolComposeTheme
import com.alexmodzofc.tool.update.UpdateChecker
import androidx.webkit.ScriptHandler

class MainActivity : AlexToolActivity(), OverlayHostActivity, SnackbarHostActivity {

    companion object {
        const val EXTRA_REFRESH_LINK_MODE = "extra_refresh_link_mode"
        const val EXTRA_REFRESH_LINK_DOWNLOAD_ID = "extra_refresh_link_download_id"
        const val EXTRA_REFRESH_LINK_FILENAME = "extra_refresh_link_filename"
        const val EXTRA_REFRESH_LINK_ORIGINAL_URL = "extra_refresh_link_original_url"
        const val EXTRA_REFRESH_LINK_ORIGINAL_REFERER = "extra_refresh_link_original_referer"
    }

    data class RefreshLinkSession(
        val downloadId: Int,
        val filename: String,
        val originalUrl: String,
        val originalReferer: String,
        val previousTabIndex: Int
    )

    internal var refreshLinkSession: RefreshLinkSession? = null

    /** Compose-observed chrome state; see [MainUiState] and `MainScreen.kt`. */
    internal val uiState = MainUiState()

    /** Full-window Compose overlay (document viewer, download dialog, update flow) rendered
     *  inline in this activity's own composition; see [OverlayHostActivity]. */
    override var overlayContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** Backs the "downloading started" Snackbar; see [SnackbarHostActivity]. */
    override val snackbarHostState = SnackbarHostState()

    // The WebView/pull-to-refresh island and the fullscreen-video host stay real Android Views
    // (there's no Compose gain there) and are hosted into the Compose tree via `AndroidView`.
    internal lateinit var webContainer: FrameLayout
    internal lateinit var swipeRefreshView: AlexToolSwipeRefreshLayout
    internal lateinit var fullscreenContainerView: FrameLayout

    internal lateinit var prefs: SharedPreferences
    internal val tabManager = TabManager()
    internal var isDesktopMode = false
    internal var desktopModeHost: String? = null
    internal var autoDesktopPendingReload: String? = null
    internal val desktopScriptHandlers = mutableMapOf<String, ScriptHandler>()
    internal val autoplayScriptHandlers = mutableMapOf<String, ScriptHandler>()
    internal val quiverGuardScriptHandlers = com.alexmodzofc.tool.quiver.engine.ScriptHandlerStore()
    internal val quiverGuardJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    internal var bottomBarAnimator2: android.animation.ValueAnimator? = null
    internal var hasWebBottomNav: Boolean = false
    internal var nestedScrollActive = false
    internal var canvasTouchActive = false
    internal var swipeGuardBlocked = false
    private var swipeGuardInitX = 0f
    private var swipeGuardInitY = 0f

    internal var fullscreenView: View? = null
    internal var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    internal var suggestionFetcher: SuggestionFetcher? = null
    internal var suggestionsBgThread: android.os.HandlerThread? = null
    internal var suggestionsBgHandler: android.os.Handler? = null

    private var backPressedOnce = false
    private val backPressHandler = Handler(Looper.getMainLooper())

    internal var pendingFileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var pendingFileChooserParams: android.webkit.WebChromeClient.FileChooserParams? = null
    internal var filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var cameraImageUri: android.net.Uri? = null
    internal var cameraVideoUri: android.net.Uri? = null

    internal data class PendingDownload(
        val url: String,
        val filename: String,
        val userAgent: String,
        val referer: String,
        val cookies: String
    )

    internal var pendingDownload: PendingDownload? = null
    internal var downloadDialogFolderPickerCallback: ((android.net.Uri) -> Unit)? = null
    internal var pendingWebPermissionRequest: android.webkit.PermissionRequest? = null
    internal var pendingWebMicPermissionRequest: android.webkit.PermissionRequest? = null
    internal var pendingWebGeoOrigin: String? = null
    internal var pendingWebGeoCallback: android.webkit.GeolocationPermissions.Callback? = null
    internal var pendingBridgeNotifCallbackId: String? = null
    internal var pendingBridgeNotifOrigin: String? = null
    internal var pendingBridgeNotifWebView: android.webkit.WebView? = null

    internal val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            AlexToolDownloadManager.enqueue(this, pending.url, pending.filename, pending.userAgent, pending.referer, pending.cookies)
        }
    }

    internal val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    internal val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val cb = pendingFileChooserCallback
        val params = pendingFileChooserParams
        pendingFileChooserCallback = null
        pendingFileChooserParams = null
        if (granted && cb != null && params != null) {
            launchFileChooser(cb, params)
        } else if (cb != null) {
            cb.onReceiveValue(null)
        }
    }

    internal val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoiceSearch()
    }

    internal val webCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (granted && request != null) {
            showWebCameraDialog(request)
        } else {
            request?.deny()
        }
    }

    internal val webMicrophonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebMicPermissionRequest
        pendingWebMicPermissionRequest = null
        if (granted && request != null) {
            showWebMicDialog(request)
        } else {
            request?.deny()
        }
    }

    internal val webLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val origin = pendingWebGeoOrigin
        val callback = pendingWebGeoCallback
        pendingWebGeoOrigin = null
        pendingWebGeoCallback = null
        if (granted && origin != null && callback != null) {
            showWebLocationDialog(origin, callback)
        } else {
            callback?.invoke(origin ?: "", false, false)
        }
    }

    internal val webNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val callbackId = pendingBridgeNotifCallbackId
        val origin = pendingBridgeNotifOrigin
        val wv = pendingBridgeNotifWebView
        pendingBridgeNotifCallbackId = null
        pendingBridgeNotifOrigin = null
        pendingBridgeNotifWebView = null
        if (wv == null || callbackId == null) return@registerForActivityResult
        val safeId = callbackId.replace("'", "")
        if (granted && origin != null) {
            showWebNotificationPermissionFromBridge(wv, safeId, origin)
        } else {
            wv.evaluateJavascript("window._AlexToolResolvePermission('$safeId','denied')", null)
        }
    }

    internal val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { uiState.voiceResult = it }
        }
    }

    internal val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                cameraImageUri != null -> arrayOf(cameraImageUri!!)
                else -> null
            }
        } else {
            cameraImageUri?.let { contentResolver.delete(it, null, null) }
            null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
        cameraImageUri = null
        cameraVideoUri = null
    }

    internal val downloadDialogFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            downloadDialogFolderPickerCallback?.invoke(uri)
        }
        downloadDialogFolderPickerCallback = null
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "javascript_enabled" -> applyJavaScript()
            "block_third_party_cookies" -> applyCookiePolicy()
            "custom_user_agent" -> applyUserAgent()
            "quiver_guard_enabled" -> onQuiverGuardEnabled(prefs.getBoolean("quiver_guard_enabled", false))
            "data_saver_enabled", "data_saver_disable_images", "data_saver_disable_autoplay" -> applyDataSaverSettings()
            "force_dark_web" -> {
                tabManager.tabs.forEach { applyWebDarkMode(it.webView) }
                tabManager.activeTab?.webView?.reload()
            }
            "hide_bars_on_scroll" -> {
                if (!prefs.getBoolean("hide_bars_on_scroll", true)) {
                    animateBottomBarTo(0f, animated = false)
                }
            }
            "scroll_hide_mode" -> {
                if (prefs.getString("scroll_hide_mode", "off") == "off") {
                    animateBottomBarTo(0f, animated = false)
                }
            }
            // v1.2.24: apply the status-bar hide toggle immediately (the Settings
            // restart flow still writes the pref, but a live apply keeps the bar
            // geometry correct even before a restart completes or when the toggle
            // is changed programmatically).
            "hide_status_bar" -> applyStatusBarVisibility()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webContainer = FrameLayout(this)
        swipeRefreshView = AlexToolSwipeRefreshLayout(this).apply {
            addView(webContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        fullscreenContainerView = FrameLayout(this)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        applyStatusBarVisibility()

        // v1.2.22: Chrome-style UI — the status bar is transparent and the app draws
        // edge-to-edge, exactly like Chrome on Android. The system clock/battery icons
        // float over the app's own surface; the toolbar no longer paints a separate
        // colored strip behind them, so the address bar reads as the compact pill
        // only and page content starts right at the pill's bottom edge.
        runCatching {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val isDarkTheme = (prefs.getString("app_theme", "dark") ?: "dark") == "dark"
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme
            // Fix: without this, hiding the status bar (hide_status_bar) on notch /
            // punch-hole devices leaves a solid black strip where the cutout is, since
            // the window only draws into the cutout while the status bar is showing by
            // default. Chrome always requests the widest allowed cutout area.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }
        applyStatusBarIconAppearance()

        // v1.2.16: the Compose status-bar inset can report 0 on the first composition
        // (insets arrive after the layout pass), which made the top toolbar measure a
        // too-small height (bar content only, without the status padding) and permanently
        // cut the top of every page. Capture the REAL status-bar inset at View level
        // BEFORE the first composition — View-level insets are always available. If the
        // system reports 0 (edge-to-edge gestures), fall back to the system resource.
        var systemStatusBarPx = runCatching {
            window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        }.getOrDefault(0)
        if (systemStatusBarPx == 0) {
            systemStatusBarPx = android.content.res.Resources.getSystem()
                .getIdentifier("status_bar_height", "dimen", "android")
                .takeIf { it > 0 }
                ?.let { android.content.res.Resources.getSystem().getDimensionPixelSize(it) } ?: 0
        }
        if (systemStatusBarPx > 0) uiState.cachedStatusBarInsetPx = systemStatusBarPx

        val startTheme = prefs.getString("app_theme", "dark") ?: "dark"
        setContent {
            AlexToolComposeTheme(theme = startTheme) {
                MainScreen(activity = this, state = uiState)
                overlayContent?.invoke()
                AlexToolSnackbarHost(hostState = snackbarHostState)
            }
        }

        AlexToolDownloadManager.createNotificationChannel(this)
        AlexToolDownloadManager.init(this)
        initializeQuiverGuardEngine()
        observeQuiverGuardCounter()
        createWebNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!BuildConfig.IS_FDROID && prefs.getBoolean("check_update_on_launch", true)) {
            val skipOnMetered = prefs.getBoolean("skip_update_on_metered", true)
            val isBeta = prefs.getBoolean("beta_channel", false)
            if (!skipOnMetered || !isNetworkMetered()) {
                UpdateChecker.check(this, isBeta, silent = true)
            }
        }
        migrateScrollHideMode()
        setupSwipeRefresh()
        setupAddressBar()
        applyAddressBarPosition()
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
        if (isRefreshLinkMode) {
            val downloadId = intent.getIntExtra(EXTRA_REFRESH_LINK_DOWNLOAD_ID, -1)
            val filename = intent.getStringExtra(EXTRA_REFRESH_LINK_FILENAME) ?: ""
            val originalUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_URL) ?: ""
            val originalReferer = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_REFERER) ?: ""
            setIntent(android.content.Intent())
            if (downloadId != -1) {
                restoreTabs()
                refreshLinkSession = RefreshLinkSession(downloadId, filename, originalUrl, originalReferer, tabManager.activeIndex)
                openRefreshLinkTab(originalReferer.ifEmpty { originalUrl.ifEmpty { getSearchEngineHomeUrl() } })
            } else if (!restoreTabs()) {
                openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
            }
        } else {
            val intentUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            if (!intentUrl.isNullOrEmpty()) {
                restoreTabs()
                openNewTab(isIncognito = false, url = intentUrl)
            } else if (!restoreTabs()) {
                openNewTab(isIncognito = false, url = getSearchEngineHomeUrl())
            }
        }
        val toolingUrl = intent.getStringExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_URL)
        if (!toolingUrl.isNullOrEmpty()) {
            setIntent(android.content.Intent())
            // v1.2.28: load the bypass link in ONE fresh tab without restoring
            // the previous session — restoring re-created the tab that already
            // held the used link, causing the site's "LINK USED" error.
            clearTabsSilently()
            openNewTab(isIncognito = false, url = toolingUrl)
            return
        }
                setupBackPressedDispatcher()
    }

    // v1.2.22: keeps status-bar icon colors correct when the user switches the
    // app theme while the browser is already open (dark ↔ light).
    private fun applyStatusBarIconAppearance() {
        runCatching {
            val isLight = (prefs.getString("app_theme", "dark") ?: "dark") == "light"
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isLight
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
        if (isRefreshLinkMode) {
            val downloadId = intent.getIntExtra(EXTRA_REFRESH_LINK_DOWNLOAD_ID, -1)
            val filename = intent.getStringExtra(EXTRA_REFRESH_LINK_FILENAME) ?: ""
            val originalUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_URL) ?: ""
            val originalReferer = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_REFERER) ?: ""
            setIntent(android.content.Intent())
            if (downloadId != -1) {
                refreshLinkSession = RefreshLinkSession(downloadId, filename, originalUrl, originalReferer, tabManager.activeIndex)
                openRefreshLinkTab(originalReferer.ifEmpty { originalUrl.ifEmpty { getSearchEngineHomeUrl() } })
            }
            return
        }
        val toolingUrl = intent.getStringExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_URL)
        if (!toolingUrl.isNullOrEmpty()) {
            setIntent(android.content.Intent())
            // v1.2.28: same-tab isolated flow — don't restore the previous
            // session (its tab already used the link) before opening the
            // bypass target.
            clearTabsSilently()
            openNewTab(isIncognito = false, url = toolingUrl)
            return
        }
        val url = getUrlFromIntent(intent)
        setIntent(android.content.Intent())
        if (!url.isNullOrEmpty()) {
            openNewTab(isIncognito = false, url = url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        bottomBarAnimator2?.cancel()
        uiState.topBarFraction = 0f
        uiState.bottomBarFraction = 0f
        nestedScrollActive = false
        canvasTouchActive = false
        hasWebBottomNav = false
        // v1.2.14: do NOT clear the measured bar heights here — they are layout
        // constants that only change when the user changes the address-bar
        // position (applyAddressBarPosition resets them deliberately). Clearing
        // them made updateMainContentInsets() bail and kept stale (wrong)
        // content padding forever, e.g. after returning to the app.
        swipeRefreshView.isEnabled = true
        updateMainContentInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarVisibility()
    }

    override fun onStop() {
        super.onStop()
        saveTabs()
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
        tabManager.destroyAll()
        suggestionFetcher?.cancel()
        suggestionsBgThread?.quitSafely()
        quiverGuardJobs.clear()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeGuardInitX = ev.x
                swipeGuardInitY = ev.y
                swipeGuardBlocked = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!swipeGuardBlocked) {
                    swipeGuardBlocked = true
                    swipeRefreshView.isEnabled = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeGuardBlocked) {
                    val dx = abs(ev.x - swipeGuardInitX)
                    val dy = abs(ev.y - swipeGuardInitY)
                    if (dx > slop && dx >= dy) {
                        swipeGuardBlocked = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeGuardBlocked) {
                    swipeGuardBlocked = false
                    updateMainContentInsets()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupBackPressedDispatcher() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenView != null) {
                    exitFullscreen()
                    return
                }
                if (uiState.searchOverlayOpen) {
                    closeSearchOverlay()
                    return
                }
                val activeTab = tabManager.activeTab
                val wv = activeTab?.webView
                if (wv?.canGoBack() == true) {
                    wv.goBack()
                    return
                }
                if (activeTab != null && closePopupTabToOpener(activeTab)) {
                    return
                }
                handleExitConfirmation()
            }
        })
    }

    private fun handleExitConfirmation() {
        val mode = prefs.getString("exit_confirmation", "toast") ?: "toast"
        when (mode) {
            "off" -> finish()
            "dialog" -> showExitDialog()
            else -> handleToastExit()
        }
    }

    private fun handleToastExit() {
        if (backPressedOnce) {
            backPressHandler.removeCallbacksAndMessages(null)
            finish()
            return
        }
        backPressedOnce = true
        Toast.makeText(this, getString(R.string.exit_tap_again), Toast.LENGTH_SHORT).show()
        backPressHandler.postDelayed({ backPressedOnce = false }, 2000L)
    }

    private fun showExitDialog() {
        uiState.confirmDialogConfig = com.alexmodzofc.tool.ui.listscreen.ConfirmDialogConfig(
            title = getString(R.string.exit_dialog_title),
            message = getString(R.string.exit_dialog_message),
            cancelable = false,
            positiveLabel = getString(R.string.exit_dialog_confirm),
            onPositive = { finish() },
            negativeLabel = getString(R.string.action_cancel)
        )
    }

    fun onTabSelected(index: Int) { tabManager.switchTo(index); attachActiveWebView() }
    fun onTabClosed(index: Int) {
        val tab = tabManager.tabs.getOrNull(index)
        tab?.let {
            removeDesktopScript(it)
            onQuiverGuardTabClosed(it)
            if (!it.isIncognito) com.alexmodzofc.tool.ui.FaviconCache.evict(this, it.url)
        }
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
        resetProgressBar()
        if (tabManager.count == 0) openNewTab(false)
        else if (wasActive) attachActiveWebView()
        else updateTabCount()
    }
    fun onNewTab() { openNewTab(false) }
    fun onNewIncognitoTab() { openNewTab(true) }

    fun onMenuGoBack() { navGoBack() }
    fun onMenuGoForward() { navGoForward() }
    fun onMenuHome() { navGoHome() }
    fun onMenuRefreshOrStop() { navRefreshOrStop() }
    fun onMenuToggleBookmark() { navToggleBookmark() }
    fun onMenuNewTab() { openNewTab(false) }
    fun onMenuIncognito() { openNewTab(true) }
    fun onMenuShare() {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, tabManager.activeTab?.webView?.url)
        }
        startActivity(android.content.Intent.createChooser(i, getString(R.string.share_url)))
    }
    fun onMenuOpenInApp() {
        val currentUrl = tabManager.activeTab?.webView?.url ?: return
        val currentUri = runCatching { android.net.Uri.parse(currentUrl) }.getOrNull() ?: return
        val webClient = tabManager.activeTab?.webView?.webViewClient as? com.alexmodzofc.tool.browser.webview.AlexToolWebViewClient ?: return
        val appMatches = webClient.resolveAppMatches(currentUri, this)
        if (appMatches.size == 1) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, currentUri)
                .setPackage(appMatches[0].activityInfo.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        } else {
            webClient.tryOpenInApp(tabManager.activeTab?.webView ?: return, currentUri)
        }
    }
    fun onMenuDownloads() { startActivity(android.content.Intent(this, com.alexmodzofc.tool.downloads.DownloadsActivity::class.java)) }
    fun onMenuBookmarks() { startActivity(android.content.Intent(this, com.alexmodzofc.tool.bookmarks.BookmarksActivity::class.java)) }
    fun onMenuHistory() { startActivity(android.content.Intent(this, com.alexmodzofc.tool.history.HistoryActivity::class.java)) }
    fun onMenuDesktopMode() {
        isDesktopMode = !isDesktopMode

        val wv = tabManager.activeTab?.webView
        val currentWebUrl = wv?.url
        val host = currentWebUrl?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }

        desktopModeHost = if (isDesktopMode) host else null

        if (host != null && tabManager.activeTab?.isIncognito != true) {
            val shouldSave = prefs.getString(
                com.alexmodzofc.tool.settings.desktopmode.DesktopModeActivity.PREF_DESKTOP_MODE_SAVE_STATE,
                com.alexmodzofc.tool.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE
            ) == com.alexmodzofc.tool.settings.desktopmode.DesktopModeActivity.VALUE_SAVE_STATE

            if (isDesktopMode && shouldSave) {
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.setState(
                    this, host,
                    com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE,
                    com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW
                )
            } else if (!isDesktopMode && shouldSave) {
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.deleteEntry(
                    this, host,
                    com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE
                )
            }
        }

        tabManager.tabs.forEach { tab ->
            tab.webView.settings.userAgentString = buildUserAgent()
            applyUserAgentMetadata(tab.webView)
            if (isDesktopMode) addDesktopScript(tab) else removeDesktopScript(tab)
        }

        if (wv != null && !currentWebUrl.isNullOrEmpty()) {
            val headers = buildDesktopHeaders()
            if (headers != null) wv.loadUrl(currentWebUrl, headers) else wv.reload()
        } else wv?.reload()
    }
    fun onMenuSettings() { startActivity(android.content.Intent(this, com.alexmodzofc.tool.settings.SettingsActivity::class.java)) }
    fun onMenuLinkToolkit() {
        startActivity(android.content.Intent(this, com.alexmodzofc.tool.settings.SettingsActivity::class.java)
            .putExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "link_toolkit"))
    }
    fun onMenuDomainBlocker() {
        startActivity(android.content.Intent(this, com.alexmodzofc.tool.settings.SettingsActivity::class.java)
            .putExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "domain_blocker"))
    }
    // v1.2.26: UserScripts manager is now reachable straight from the overflow menu — users
    // asked for it outside Settings. It opens the same manager screen that the UserScripts
    // pane inside Settings uses, so add/edit/delete/enable behaviour is identical.
    fun onMenuUserscripts() {
        startActivity(android.content.Intent(this, com.alexmodzofc.tool.settings.SettingsActivity::class.java)
            .putExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "user_scripts"))
    }
    fun onMenuDataSaver() {
        val enabled = !prefs.getBoolean("data_saver_enabled", false)
        prefs.edit().putBoolean("data_saver_enabled", enabled).apply()
    }
    fun onMenuOpenDataSaverSettings() {
        startActivity(android.content.Intent(this, com.alexmodzofc.tool.settings.SettingsActivity::class.java)
            .putExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "data_saver"))
    }
    fun onMenuOpenDownloadSettings() {
        startActivity(android.content.Intent(this, com.alexmodzofc.tool.settings.SettingsActivity::class.java)
            .putExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "download_settings"))
    }
    fun onMenuQuiverGuard() {
        val enabled = !prefs.getBoolean("quiver_guard_enabled", false)
        if (enabled) {
            val filterListDb = com.alexmodzofc.tool.quiver.FilterListDatabase(this)
            val hasActive: Boolean
            try {
                hasActive = filterListDb.hasActiveFilterLists()
            } finally {
                filterListDb.close()
            }
            if (!hasActive) {
                startActivity(
                    android.content.Intent(this, com.alexmodzofc.tool.quiver.QuiverGuardActivity::class.java)
                        .putExtra(com.alexmodzofc.tool.quiver.QuiverGuardActivity.EXTRA_SHOW_SETUP_GUIDE, true)
                )
                return
            }
        }
        prefs.edit().putBoolean("quiver_guard_enabled", enabled).apply()
    }
    fun onMenuOpenQuiverGuardSettings() {
        startActivity(android.content.Intent(this, com.alexmodzofc.tool.quiver.QuiverGuardActivity::class.java))
    }
    fun onMenuDisableQuiverGuardForSite() {
        val tab = tabManager.activeTab ?: return
        val wv = tab.webView
        val currentUrl = wv.url ?: return
        if (!currentUrl.startsWith("http://") && !currentUrl.startsWith("https://")) return
        val host = runCatching { android.net.Uri.parse(currentUrl).host }.getOrNull() ?: return
        if (tab.isIncognito) return
        val isExcepted = com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.getState(
            this, host, com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
        ) != null
        if (isExcepted) {
            com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.deleteEntry(
                this, host, com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION
            )
        } else {
            com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.setState(
                this, host,
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_QUIVER_GUARD_EXCEPTION,
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW
            )
            com.alexmodzofc.tool.quiver.engine.BlockedRequestCounter.resetTab(tab.id)
        }
        wv.reload()
    }
    fun onMenuReaderMode() {
        val wv = tabManager.activeTab?.webView ?: return
        val pageUrl = wv.url ?: return
        val js = assets.open("JavaScript/reader_mode.js").bufferedReader().use { it.readText() }
        wv.evaluateJavascript(js) { raw ->
            if (isFinishing) return@evaluateJavascript
            val json = runCatching {
                val unescaped = raw?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                    ?.replace("\\n", "\n")
                    ?.replace("\\t", "\t")
                    ?: ""
                org.json.JSONObject(unescaped)
            }.getOrNull() ?: return@evaluateJavascript
            val title = json.optString("title", "")
            val content = json.optString("content", "")
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val theme = prefs.getString("app_theme", "dark") ?: "dark"
            val isDark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val bgColor = if (isDark) "#121212" else "#ffffff"
            val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
            val secondaryColor = if (isDark) "#aaaaaa" else "#555555"
            val linkColor = if (isDark) "#90caf9" else "#1a73e8"
            val html = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:Georgia,'Times New Roman',serif;font-size:18px;line-height:1.75;max-width:700px;margin:0 auto;padding:16px 20px 32px;background-color:$bgColor;color:$textColor;}
h1,h2,h3,h4,h5,h6{font-family:-apple-system,sans-serif;line-height:1.3;color:$textColor;}
a{color:$linkColor;}
img{max-width:100%;height:auto;display:block;margin:12px auto;}
pre,code{overflow-x:auto;font-size:14px;}
blockquote{border-left:3px solid $secondaryColor;margin:0;padding:4px 16px;color:$secondaryColor;}
figure{margin:12px 0;}
figcaption{font-size:13px;color:$secondaryColor;text-align:center;margin-top:4px;}
table{border-collapse:collapse;width:100%;}
td,th{border:1px solid $secondaryColor;padding:6px 8px;}
</style>
</head>
<body>$content</body>
</html>"""
            runOnUiThread {
                uiState.contentPreviewRequest = com.alexmodzofc.tool.browser.sheets.ContentPreviewRequest.forReaderMode(pageUrl, title, html)
            }
        }
    }

    inner class NestedScrollBridge {
        @android.webkit.JavascriptInterface
        fun onNestedScroll(active: Boolean) {
            runOnUiThread { nestedScrollActive = active }
        }
    }

    inner class CanvasTouchBridge {
        @android.webkit.JavascriptInterface
        fun onCanvasTouch(active: Boolean) {
            runOnUiThread { canvasTouchActive = active }
        }
    }

    inner class BottomNavBridge {
        @android.webkit.JavascriptInterface
        fun onBottomNavDetected(detected: Boolean) {
            runOnUiThread {
                if (detected && !hasWebBottomNav) {
                    hasWebBottomNav = true
                }
            }
        }
    }

    inner class NotificationBridge(private val webView: android.webkit.WebView) {
        @android.webkit.JavascriptInterface
        fun getPermissionState(origin: String): String {
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return "denied"
            val rawOrigin = origin.trim()
            return when (com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
            )) {
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> "granted"
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> "denied"
                else -> "default"
            }
        }

        @android.webkit.JavascriptInterface
        fun requestPermission(callbackId: String, origin: String) {
            runOnUiThread {
                val tab = tabManager.tabs.find { it.webView == webView }
                val safeId = callbackId.replace("'", "")
                if (tab?.isIncognito == true) {
                    webView.evaluateJavascript("window._AlexToolResolvePermission('$safeId','denied')", null)
                    return@runOnUiThread
                }
                val rawOrigin = origin.trim()
                val stored = com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.getState(
                    this@MainActivity, rawOrigin,
                    com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
                )
                when (stored) {
                    com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            !isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            pendingBridgeNotifCallbackId = safeId
                            pendingBridgeNotifOrigin = rawOrigin
                            pendingBridgeNotifWebView = webView
                            webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            webView.evaluateJavascript("window._AlexToolResolvePermission('$safeId','granted')", null)
                        }
                    }
                    com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> {
                        webView.evaluateJavascript("window._AlexToolResolvePermission('$safeId','denied')", null)
                    }
                    else -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            !isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            pendingBridgeNotifCallbackId = safeId
                            pendingBridgeNotifOrigin = rawOrigin
                            pendingBridgeNotifWebView = webView
                            val needsRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
                            if (needsRationale) {
                                uiState.confirmDialogConfig = com.alexmodzofc.tool.ui.listscreen.ConfirmDialogConfig(
                                    title = getString(R.string.notification_permission_title),
                                    message = getString(R.string.notification_permission_message),
                                    positiveLabel = getString(R.string.action_allow),
                                    onPositive = { webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                    negativeLabel = getString(R.string.action_deny),
                                    onNegative = {
                                        pendingBridgeNotifCallbackId = null
                                        pendingBridgeNotifOrigin = null
                                        pendingBridgeNotifWebView = null
                                        webView.evaluateJavascript("window._AlexToolResolvePermission('$safeId','denied')", null)
                                    }
                                )
                            } else {
                                webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            showWebNotificationPermissionFromBridge(webView, safeId, rawOrigin)
                        }
                    }
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun postNotification(title: String, body: String, tag: String, origin: String) {
            val rawOrigin = origin.trim()
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return
            val stored = com.alexmodzofc.tool.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
            )
            if (stored != com.alexmodzofc.tool.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW) return
            runOnUiThread { postWebNotification(title, body, tag, rawOrigin) }
        }
    }

    inner class BlobDownloadBridge {
        @android.webkit.JavascriptInterface
        fun receiveBlob(base64: String, filename: String, mimeType: String) {
            runOnUiThread {
                showDownloadDialogForBlob(base64, filename, mimeType)
            }
        }

        @android.webkit.JavascriptInterface
        fun onError(error: String) {
        }
    }

    internal fun isNetworkMetered(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    private fun migrateScrollHideMode() {
        when (prefs.getString("scroll_hide_mode", "off")) {
            "top_only" -> prefs.edit().putString("scroll_hide_mode", "search_bar").apply()
            "bottom_only" -> {
                val position = prefs.getString("address_bar_position", "top") ?: "top"
                val newValue = if (position == "bottom") "search_bar" else "navigation_bar"
                prefs.edit().putString("scroll_hide_mode", newValue).apply()
            }
        }
    }

    fun onImageOpenInNewTab(imageUrl: String) { handleImageOpenInNewTab(imageUrl) }
    fun onImageOpenIncognito(imageUrl: String) { openNewTab(isIncognito = true, url = imageUrl) }
    fun onImageOpenInCurrentTab(imageUrl: String) { dismissContentPreview(); loadUrl(imageUrl) }
    fun onImagePreview(imageUrl: String) { handleImagePreview(imageUrl) }
    fun onImageCopy(imageUrl: String) { handleImageCopy(imageUrl) }
    fun onImageDownload(imageUrl: String, altText: String) { handleImageDownload(imageUrl, altText) }
    fun onImageShare(imageUrl: String) { handleImageShare(imageUrl) }

    fun onLinkOpenInNewTab(url: String) { handleLinkOpenInNewTab(url) }
    fun onLinkOpenIncognito(url: String) { handleLinkOpenIncognito(url) }
    fun onLinkPreviewPage(url: String) { handleLinkPreviewPage(url) }
    fun onLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    fun onLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    fun onLinkShare(url: String) { handleLinkShare(url) }

    fun onPreviewOpenInNewTab(url: String) { openNewTab(isIncognito = false, url = url) }

    fun onPreviewLinkOpenInNewTab(url: String) { dismissContentPreview(); handleLinkOpenInNewTab(url) }
    fun onPreviewLinkOpenIncognito(url: String) { dismissContentPreview(); handleLinkOpenIncognito(url) }
    fun onPreviewLinkOpenInCurrentTab(url: String) { dismissContentPreview(); loadUrl(url) }
    fun onPreviewLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    fun onPreviewLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    fun onPreviewLinkShare(url: String) { handleLinkShare(url) }

    private fun dismissContentPreview() {
        uiState.contentPreviewRequest = null
    }

    private fun getUrlFromIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            else -> intent.data?.toString()
        }
    }
}
