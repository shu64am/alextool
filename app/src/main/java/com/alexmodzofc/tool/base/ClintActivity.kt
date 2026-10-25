package com.alexmodzofc.tool.base

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.ui.ThemeRevealHolder
import com.alexmodzofc.tool.ui.ThemeRevealOverlay
import com.alexmodzofc.tool.util.LocaleHelper
import kotlin.math.hypot
import kotlin.math.max

abstract class AlexToolActivity : AppCompatActivity() {

    private var appliedTheme: String? = null
    private var appliedAccent: String? = null
    private var appliedIntensity: String? = null
    private var appliedLanguage: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        appliedTheme = prefs.getString("app_theme", "dark") ?: "dark"
        appliedAccent = prefs.getString("accent_color", "purple") ?: "purple"
        appliedIntensity = prefs.getString("surface_intensity", "strong_tint") ?: "strong_tint"
        appliedLanguage = prefs.getString(LocaleHelper.PREF_APP_LANGUAGE, LocaleHelper.LANGUAGE_SYSTEM) ?: LocaleHelper.LANGUAGE_SYSTEM
        applyThemeResource()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        openDialogCount = 0
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTheme = prefs.getString("app_theme", "dark") ?: "dark"
        val currentAccent = prefs.getString("accent_color", "purple") ?: "purple"
        val currentIntensity = prefs.getString("surface_intensity", "strong_tint") ?: "strong_tint"
        val currentLanguage = prefs.getString(LocaleHelper.PREF_APP_LANGUAGE, LocaleHelper.LANGUAGE_SYSTEM) ?: LocaleHelper.LANGUAGE_SYSTEM
        if (currentTheme != appliedTheme || currentAccent != appliedAccent || currentIntensity != appliedIntensity || currentLanguage != appliedLanguage) {
            window.setWindowAnimations(0)
            recreate()
            return
        }
        applyStatusBarVisibility()
        applySystemBarAppearance()
        window.decorView.post { startRevealIfNeeded() }
    }

    private fun isMaterialYouActive(): Boolean {
        return appliedAccent == "material_you" &&
            (appliedTheme == "dark" || appliedTheme == "light")
    }

    private fun isDefaultMaterialYou(): Boolean {
        return appliedAccent == "material_you" && appliedTheme == "default"
    }

    private fun isPurpleActive(): Boolean {
        return appliedAccent == "purple"
    }

    private fun isBlueActive(): Boolean {
        return appliedAccent == "blue"
    }

    private fun isYellowActive(): Boolean {
        return appliedAccent == "yellow"
    }

    private fun isRedActive(): Boolean {
        return appliedAccent == "red"
    }

    private fun isGreenActive(): Boolean {
        return appliedAccent == "green"
    }

    private fun isOrangeActive(): Boolean {
        return appliedAccent == "orange"
    }

    private fun isSurfaceIntensityActive(): Boolean {
        if (appliedTheme == "default") return false
        return appliedAccent == "material_you" || appliedAccent == "purple" || appliedAccent == "blue" || appliedAccent == "yellow" || appliedAccent == "red" || appliedAccent == "green" || appliedAccent == "orange" || appliedAccent == "default"
    }

    private fun applyThemeResource() {
        when {
            appliedTheme == "dark" && isPurpleActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_Purple)
            appliedTheme == "dark" && isBlueActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_Blue)
            appliedTheme == "dark" && isYellowActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_Yellow)
            appliedTheme == "dark" && isRedActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_Red)
            appliedTheme == "dark" && isGreenActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_Green)
            appliedTheme == "dark" && isOrangeActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_Orange)
            appliedTheme == "dark" && isMaterialYouActive() -> setTheme(R.style.Theme_AlexToolBrowser_Dark_MaterialYou)
            appliedTheme == "dark" -> setTheme(R.style.Theme_AlexToolBrowser_Dark)
            appliedTheme == "light" && isPurpleActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_Purple)
            appliedTheme == "light" && isBlueActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_Blue)
            appliedTheme == "light" && isYellowActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_Yellow)
            appliedTheme == "light" && isRedActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_Red)
            appliedTheme == "light" && isGreenActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_Green)
            appliedTheme == "light" && isOrangeActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_Orange)
            appliedTheme == "light" && isMaterialYouActive() -> setTheme(R.style.Theme_AlexToolBrowser_Light_MaterialYou)
            appliedTheme == "light" -> setTheme(R.style.Theme_AlexToolBrowser_Light)
            isPurpleActive() -> setTheme(R.style.Theme_AlexToolBrowser_Purple)
            isBlueActive() -> setTheme(R.style.Theme_AlexToolBrowser_Blue)
            isYellowActive() -> setTheme(R.style.Theme_AlexToolBrowser_Yellow)
            isRedActive() -> setTheme(R.style.Theme_AlexToolBrowser_Red)
            isGreenActive() -> setTheme(R.style.Theme_AlexToolBrowser_Green)
            isOrangeActive() -> setTheme(R.style.Theme_AlexToolBrowser_Orange)
            isDefaultMaterialYou() -> {
                setTheme(R.style.Theme_AlexToolBrowser_MaterialYou)
                if (DynamicColors.isDynamicColorAvailable()) {
                    DynamicColors.applyToActivityIfAvailable(this)
                    theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_PreserveDefaultBackground, true)
                }
                return
            }
            else -> setTheme(R.style.Theme_AlexToolBrowser)
        }
        if (isMaterialYouActive()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        applyIntensityOverlay()
    }

    private fun applyIntensityOverlay() {
        if (!isSurfaceIntensityActive()) return
        val intensity = appliedIntensity ?: "soft_tint"
        val isLight = appliedTheme == "light"
        val isPurple = isPurpleActive()
        val isBlue = isBlueActive()
        val isYellow = isYellowActive()
        val isRed = isRedActive()
        val isGreen = isGreenActive()
        val isOrange = isOrangeActive()
        val isMaterialYou = isMaterialYouActive()
        when (intensity) {
            "soft_tint" -> {
                if (isPurple && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Purple_Dark, true)
                else if (isPurple && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Purple_Light, true)
                else if (isBlue && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Blue_Dark, true)
                else if (isBlue && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Blue_Light, true)
                else if (isYellow && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Yellow_Dark, true)
                else if (isYellow && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Yellow_Light, true)
                else if (isRed && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Red_Dark, true)
                else if (isRed && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Red_Light, true)
                else if (isGreen && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Green_Dark, true)
                else if (isGreen && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Green_Light, true)
                else if (isOrange && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Orange_Dark, true)
                else if (isOrange && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_Orange_Light, true)
                else if (isMaterialYou && !isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_MaterialYou_Dark, true)
                else if (isMaterialYou && isLight) theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_SoftTint_MaterialYou_Light, true)
            }
            "pure_mode" -> {
                when {
                    isMaterialYou && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_MaterialYou_Dark, true)
                    isMaterialYou && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_MaterialYou_Light, true)
                    isPurple && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Purple_Dark, true)
                    isPurple && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Purple_Light, true)
                    isBlue && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Blue_Dark, true)
                    isBlue && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Blue_Light, true)
                    isYellow && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Yellow_Dark, true)
                    isYellow && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Yellow_Light, true)
                    isRed && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Red_Dark, true)
                    isRed && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Red_Light, true)
                    isGreen && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Green_Dark, true)
                    isGreen && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Green_Light, true)
                    isOrange && !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Orange_Dark, true)
                    isOrange && isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Orange_Light, true)
                    !isLight -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Dark, true)
                    else -> theme.applyStyle(R.style.ThemeOverlay_AlexToolBrowser_SurfaceIntensity_PureMode_Light, true)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarAppearance() {
        // v1.2.22: Chrome-style edge-to-edge — the status and navigation bars are
        // transparent and the system icons float over the app's own surface.
        // Solid colored strips are NOT painted behind them, so the address pill is
        // the only "bar" the user sees, exactly like Chrome on Android.
        val isLight = appliedTheme == "light"
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Fix: on notch / punch-hole devices, hiding the status bar (hide_status_bar
        // preference) left a solid black strip exactly where the cutout is, because the
        // window's default cutout mode only lets content draw into that area while the
        // status bar is shown. Chrome always requests the widest allowed cutout area, so
        // the page fills the space instead of leaving dead black pixels behind. This must
        // be set regardless of the current hide_status_bar value (not just when hidden),
        // since the window attribute can't be toggled per-frame without a flicker.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }

    fun captureAndRecreate(newTheme: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("app_theme", "dark") ?: "dark"
        if (current == newTheme) return
        if (newTheme == "default") {
            prefs.edit().putString("surface_intensity", "soft_tint").apply()
        }
        captureScreenBitmap()
        prefs.edit().putString("app_theme", newTheme).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    fun captureAndApplyAccentColor(newAccent: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("accent_color", "purple") ?: "purple"
        if (current == newAccent) return
        val currentIntensity = prefs.getString("surface_intensity", "strong_tint") ?: "strong_tint"
        if (currentIntensity == "strong_tint" && newAccent != "purple" && newAccent != "blue" && newAccent != "yellow" && newAccent != "red" && newAccent != "green" && newAccent != "orange") {
            prefs.edit().putString("surface_intensity", "soft_tint").apply()
        }
        captureScreenBitmap()
        prefs.edit().putString("accent_color", newAccent).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    fun captureAndApplySurfaceIntensity(newIntensity: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("surface_intensity", "strong_tint") ?: "strong_tint"
        if (current == newIntensity) return
        captureScreenBitmap()
        prefs.edit().putString("surface_intensity", newIntensity).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    fun captureAndApplyLanguage(newLanguage: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString(LocaleHelper.PREF_APP_LANGUAGE, LocaleHelper.LANGUAGE_SYSTEM) ?: LocaleHelper.LANGUAGE_SYSTEM
        if (current == newLanguage) return
        captureScreenBitmap()
        prefs.edit().putString(LocaleHelper.PREF_APP_LANGUAGE, newLanguage).commit()
        window.setWindowAnimations(0)
        recreate()
    }

    private fun captureScreenBitmap() {
        val decor = window.decorView
        try {
            val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bmp))
            ThemeRevealHolder.bitmap = bmp
            ThemeRevealHolder.cx = decor.width / 2
            ThemeRevealHolder.cy = decor.height / 2
        } catch (_: Exception) {
        }
    }

    private fun startRevealIfNeeded() {
        val (bmp, cx, cy) = ThemeRevealHolder.consume() ?: return
        if (isFinishing || isDestroyed) {
            bmp.recycle()
            return
        }

        val decor = window.decorView as? android.view.ViewGroup ?: run {
            bmp.recycle()
            return
        }

        val maxRadius = hypot(
            max(cx, decor.width - cx).toDouble(),
            max(cy, decor.height - cy).toDouble()
        ).toFloat()

        val overlay = ThemeRevealOverlay(this, bmp, cx, cy).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        decor.addView(overlay)

        ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 450
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                overlay.revealRadius = it.animatedValue as Float
                overlay.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    decor.removeView(overlay)
                    bmp.recycle()
                }
            })
            start()
        }
    }

    private var openDialogCount = 0

    fun trackDialogShown() { openDialogCount++ }
    fun trackDialogDismissed() { if (openDialogCount > 0) openDialogCount-- }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && openDialogCount == 0) applyStatusBarVisibility()
    }

    fun applyStatusBarFlagToDialog(dialog: android.app.Dialog) {
        val hide = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("hide_status_bar", false)
        if (hide) {
            dialog.window?.let { dialogWindow ->
                val dialogController = WindowInsetsControllerCompat(dialogWindow, dialogWindow.decorView)
                dialogController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                dialogController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        trackDialogShown()
        dialog.setOnDismissListener { trackDialogDismissed() }
    }

    private fun applyStatusBarVisibility() {
        val hide = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("hide_status_bar", false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}
