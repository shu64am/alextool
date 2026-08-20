package com.alexmodzofc.tool.setup

import android.app.role.RoleManager
import androidx.activity.addCallback
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.base.AlexToolActivity
import com.alexmodzofc.tool.browser.MainActivity
import com.alexmodzofc.tool.crash.CrashHandler
import com.alexmodzofc.tool.ui.DocumentViewer
import com.alexmodzofc.tool.ui.OverlayHostActivity

class SetupActivity : AlexToolActivity(), OverlayHostActivity {

    private lateinit var uiState: SetupUiState

    /** Full-window Compose overlay (document viewer) rendered inline in this activity's own
     *  composition; see [OverlayHostActivity]. */
    override var overlayContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    private val browserRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshDefaultBrowserState()
    }

    companion object {
        const val PRIVACY_POLICY_URL = "https://github.com/shu64am/alex-tool/blob/main/README.md"
        const val TERMS_URL = "https://github.com/shu64am/alex-tool/blob/main/README.md"
        private const val KEY_PENDING_PAGE = "setup_pending_page"
        private const val KEY_PENDING_SCROLL = "setup_pending_scroll"
        private const val KEY_PENDING_HIDE_STATUS_BAR = "setup_pending_hide_status_bar"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            if (uiState.currentPage > 0) {
                uiState.currentPage -= 1
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        CrashHandler.install(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("setup_complete", false)) { startMainActivity(); return }

        var hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        if (prefs.contains(KEY_PENDING_HIDE_STATUS_BAR)) {
            hideStatusBar = prefs.getBoolean(KEY_PENDING_HIDE_STATUS_BAR, false)
            prefs.edit().remove(KEY_PENDING_HIDE_STATUS_BAR).apply()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val pendingPage = prefs.getInt(KEY_PENDING_PAGE, -1)
        val initialPage: Int
        val initialScroll: Int
        if (pendingPage >= 0) {
            prefs.edit().remove(KEY_PENDING_PAGE).apply()
            initialPage = pendingPage
            initialScroll = prefs.getInt(KEY_PENDING_SCROLL, 0)
            if (initialScroll > 0) prefs.edit().remove(KEY_PENDING_SCROLL).apply()
        } else {
            initialPage = 0
            initialScroll = 0
        }

        uiState = SetupUiState(
            initialPage = initialPage,
            initialScrollY = initialScroll,
            initialTheme = prefs.getString("app_theme", "dark") ?: "dark",
            initialAccent = prefs.getString("accent_color", "purple") ?: "purple",
            initialIntensity = prefs.getString("surface_intensity", "strong_tint") ?: "strong_tint",
            initialAddressBarPosition = prefs.getString("address_bar_position", "top") ?: "top",
            initialMenuStyle = prefs.getString("menu_style", "popup") ?: "popup",
            initialScrollHideMode = prefs.getString("scroll_hide_mode", "off") ?: "off",
            initialHideStatusBar = hideStatusBar,
            initialEngine = "google"
        )
        if (uiState.currentPage == 4) refreshDefaultBrowserState()

        setContent {
            SetupScreen(
                state = uiState,
                onPrivacyClick = {
                    DocumentViewer.show(this, getString(R.string.document_viewer_privacy_policy_title), DocumentViewer.PRIVACY_POLICY_URL)
                },
                onTermsClick = {
                    DocumentViewer.show(this, getString(R.string.document_viewer_terms_title), DocumentViewer.TERMS_URL)
                },
                onHideStatusBarToggled = { checked ->
                    uiState.hideStatusBar = checked
                    Toast.makeText(this, getString(R.string.setup_status_bar_applied_later), Toast.LENGTH_SHORT).show()
                },
                onThemeSelected = { theme -> onSetupThemeSelected(theme) },
                onAccentSelected = { accent -> onSetupAccentSelected(accent) },
                onIntensitySelected = { intensity -> onSetupIntensitySelected(intensity) },
                onAddressBarPositionSelected = { position ->
                    uiState.addressBarPosition = position
                    uiState.scrollHideMode = sanitizeScrollHideMode(uiState.scrollHideMode, position)
                },
                onMenuStyleSelected = { style -> uiState.menuStyle = style },
                onScrollHideModeSelected = { mode -> uiState.scrollHideMode = mode },
                onEngineSelected = { engine -> uiState.engine = engine },
                onContinueFromWelcome = { uiState.currentPage = 1 },
                onNextFromLayoutPage = { onNextFromLayoutPage() },
                onNextFromEnginePage = { onNextFromEnginePage() },
                onSetDefaultBrowser = {
                    if (uiState.isDefaultBrowser) saveAndProceed() else openDefaultBrowserPicker()
                },
                onSkipDefaultBrowser = { saveAndProceed() }
            )
            com.alexmodzofc.tool.ui.listscreen.ConfirmDialogHost(uiState.confirmDialogConfig, uiState.hideStatusBar) { uiState.confirmDialogConfig = null }
            overlayContent?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::uiState.isInitialized && uiState.currentPage == 4) refreshDefaultBrowserState()
    }

    private fun refreshDefaultBrowserState() {
        uiState.isDefaultBrowser = isAlexToolDefaultBrowser()
    }

    private fun isAlexToolDefaultBrowser(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun openDefaultBrowserPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    browserRoleLauncher.launch(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    private fun onNextFromLayoutPage() {
        // AlexTool v1.1.1: skip the nested-scroll confirm dialog. Compose dialogs opened
        // inside the setup flow crashed on some devices (Compose dialog window handling),
        // and the layout page already explains the trade-off.
        uiState.currentPage = 3
    }

    private fun onNextFromEnginePage() {
        // AlexTool v1.1.1: skip the Google privacy-notice dialog and go straight to the
        // default browser page. The engine selection page already explains each engine, and
        // the dialog was the crash vector on Next.
        goToDefaultBrowserPage()
    }

    private fun goToDefaultBrowserPage() {
        uiState.currentPage = 4
        refreshDefaultBrowserState()
    }

    private fun onSetupThemeSelected(theme: String) {
        if (theme == uiState.theme) { uiState.currentPage = 2; return }
        savePendingNavigationState()
        captureAndRecreate(theme)
    }

    private fun onSetupAccentSelected(accent: String) {
        if (accent == uiState.accent) return
        savePendingNavigationState()
        captureAndApplyAccentColor(accent)
    }

    private fun onSetupIntensitySelected(intensity: String) {
        if (intensity == uiState.intensity) return
        savePendingNavigationState()
        captureAndApplySurfaceIntensity(intensity)
    }

    private fun savePendingNavigationState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putInt(KEY_PENDING_PAGE, 1)
            .putInt(KEY_PENDING_SCROLL, uiState.themePageScrollState.value)
            .putBoolean(KEY_PENDING_HIDE_STATUS_BAR, uiState.hideStatusBar)
            .apply()
    }

    private fun saveAndProceed() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putString("search_engine", uiState.engine)
            .putString("address_bar_position", uiState.addressBarPosition)
            .putString("menu_style", uiState.menuStyle)
            .putString("scroll_hide_mode", uiState.scrollHideMode)
            .putBoolean("hide_status_bar", uiState.hideStatusBar)
            .putBoolean("setup_complete", true)
            .apply()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
