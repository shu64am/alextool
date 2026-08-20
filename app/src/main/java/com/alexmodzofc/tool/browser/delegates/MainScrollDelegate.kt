package com.alexmodzofc.tool.browser.delegates
import com.alexmodzofc.tool.browser.webview.*
import com.alexmodzofc.tool.browser.MainActivity

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.webkit.WebView
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

internal fun MainActivity.setupSwipeRefresh() {
    swipeRefreshView.canChildScrollUpCallback = {
        swipeGuardBlocked || isYouTubeShorts() || canvasTouchActive || run {
            val wv = tabManager.activeTab?.webView
            val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
            val barsHiddenByScrolling = !hasWebBottomNav && mode != "off" && uiState.topBarFraction >= 1f
            wv != null && (barsHiddenByScrolling || wv.canScrollVertically(-1) || nestedScrollActive)
        }
    }
    swipeRefreshView.apply {
        setColorSchemeColors(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
        setProgressBackgroundColorSchemeColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
        setOnRefreshListener {
            nestedScrollActive = false
            canvasTouchActive = false
            tabManager.activeTab?.webView?.reload() ?: run { isRefreshing = false }
        }
    }
}

internal fun MainActivity.updateMainContentInsets() {
    // The measured bar heights never get cleared after the first layout (see onResume),
    // so a zero height here genuinely means "not measured yet" — bail until then.
    if (uiState.topBarFullHeightPx == 0 || uiState.bottomBarFullHeightPx == 0) {
        // Fix: bailing out completely left contentPaddingTopPx/BottomPx at their initial
        // 0 value for the first frame(s), so the WebView (which is laid out immediately,
        // before Compose has measured the real toolbar heights) rendered flush against
        // the top of the screen — the page's own top content flashed in behind/above the
        // status bar and address bar for a frame, exactly like Chrome never does. Chrome
        // always reserves the bar's space from frame one, so estimate it here (status
        // bar inset + a stand-in for the not-yet-measured 56dp address bar row) until the
        // real measurement in TopToolbar/BottomBar's onGloballyPositioned overwrites it.
        val position = prefs.getString("address_bar_position", "top") ?: "top"
        val estimatedBarPx = (56 * resources.displayMetrics.density).toInt()
        if (uiState.topBarFullHeightPx == 0 && position != "bottom") {
            // v1.2.25: the pill paints at (inset + 56dp), so reserve exactly that. The
            // cached inset survives even when the live state inset is still 0.
            val inset = uiState.statusBarInsetPx.takeIf { it > 0 }
                ?: uiState.cachedStatusBarInsetPx.takeIf { it > 0 }
                ?: 0
            uiState.contentPaddingTopPx = inset + estimatedBarPx
        }
        if (uiState.bottomBarFullHeightPx == 0 && position != "top") {
            uiState.contentPaddingBottomPx = estimatedBarPx
        }
        return
    }
    // v1.2.14: padding is now position-aware. The old code always reserved the full
    // top-bar height at the top of the page, which left a dead strip under the status
    // bar whenever the address bar was docked at the BOTTOM (the top bar is hidden
    // there, so nothing should consume that space — the status bar sits above the
    // edge-to-edge page content). The same bug mirrored at the bottom in TOP mode.
    val position = prefs.getString("address_bar_position", "top") ?: "top"
    // v1.2.25: compute the visible bar edges from the MEASURED bar heights only — never
    // from statusBarInsetPx. The TopToolbar/BottomBar measured heights already include
    // whatever padding they actually rendered with, so anchoring the WebView padding to
    // them cannot under-estimate the bar's bottom edge even when the system inset
    // arrives late, reports 0, or is stale in the state (that mismatch is exactly what
    // made page content render underneath the address bar — the padding was computed
    // with inset 0 while the pill painted lower, at inset + bar height).
    //
    // Edge-to-edge semantics are preserved by the bar's own padding: when the pill is
    // fully hidden (fraction == 1) the padding goes to 0 and the page slides under the
    // transparent status bar; during the hide animation the visible height scales
    // smoothly between the measured full height and 0.
    val contentBarHeight = (uiState.topBarFullHeightPx - uiState.statusBarInsetPx).coerceAtLeast(0)
    val visibleTop = (uiState.topBarFullHeightPx - (uiState.topBarFraction * contentBarHeight).toInt())
        .coerceIn(0, uiState.topBarFullHeightPx)
    val visibleBottom = ((1f - uiState.bottomBarFraction) * uiState.bottomBarFullHeightPx).toInt()
        .coerceIn(0, uiState.bottomBarFullHeightPx)
    // When the search overlay is open the toolbar is hidden and the overlay field
    // replaces the bar — in BOTTOM mode it docks to the bottom, so the top padding
    // stays zero there too.
    // v1.2.21: back to the proven padding-based layout (the way ClintBrowser shipped).
    // The explicit sizing/offsetting approach from v1.2.17-1.2.20 broke nested scroll
    // handling and could collapse the WebView to zero height. Padding is reliable:
    // the page content starts exactly at the visible address bar's bottom edge with
    // zero extra margin, in every bar position and scroll-hide state.
    // v1.2.23: Chrome behavior — when the bar is fully hidden the page runs
    // edge-to-edge behind the transparent status bar (zero top padding). While
    // the bar is visible even partially, the page starts flush under the bar's
    // bottom edge.
    uiState.contentPaddingTopPx = when (position) {
        "bottom" -> 0
        // v1.2.25: while the search overlay covers the toolbar, keep the top of the page
        // below the status bar (overlay-drawn area) instead of 0 — otherwise the page's
        // first line could peek out from under the overlay's field. When the bar is
        // fully hidden, zero top padding lets the page run edge-to-edge (Chrome style).
        else -> if (uiState.searchOverlayOpen) uiState.statusBarInsetPx else if (uiState.topBarFraction >= 1f) 0 else visibleTop
    }
    // v1.2.25: clamp — the page may never start above the address bar's real bottom
    // edge. If any path ever computes a smaller padding (stale inset, animation race),
    // the measured bar height wins, which guarantees content is never hidden under it.
    if (position != "bottom") {
        uiState.contentPaddingTopPx = maxOf(uiState.contentPaddingTopPx, visibleTop)
    }
    uiState.contentPaddingBottomPx = when (position) {
        "top" -> 0
        else -> visibleBottom
    }
    val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
    val barsHidden = mode != "off" && when (mode) {
        "search_bar" -> if (position == "bottom") uiState.bottomBarFraction >= 1f else uiState.topBarFraction >= 1f
        "navigation_bar" -> uiState.bottomBarFraction >= 1f
        else -> uiState.topBarFraction >= 1f
    }
    swipeRefreshView.isEnabled = !barsHidden
}

internal fun MainActivity.setTopBarFraction(fraction: Float) {
    uiState.topBarFraction = fraction
    updateMainContentInsets()
}

internal fun MainActivity.setBottomBarFraction(fraction: Float) {
    uiState.bottomBarFraction = fraction
    updateMainContentInsets()
}

internal fun MainActivity.animateBottomBarTo(targetFraction: Float, animated: Boolean = true) {
    bottomBarAnimator2?.cancel()
    if (!animated || (uiState.topBarFullHeightPx == 0 && uiState.bottomBarFullHeightPx == 0)) {
        setTopBarFraction(targetFraction)
        setBottomBarFraction(targetFraction)
        return
    }
    val startFraction = uiState.bottomBarFraction
    if (startFraction == targetFraction) return
    bottomBarAnimator2 = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
        duration = 200L
        interpolator = if (targetFraction > startFraction) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            setTopBarFraction(f)
            setBottomBarFraction(f)
        }
        start()
    }
}

internal fun MainActivity.animateTopBarOnlyTo(targetFraction: Float, animated: Boolean = true) {
    bottomBarAnimator2?.cancel()
    if (!animated || uiState.topBarFullHeightPx == 0) {
        setTopBarFraction(targetFraction)
        return
    }
    val startFraction = uiState.topBarFraction
    if (startFraction == targetFraction) return
    bottomBarAnimator2 = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
        duration = 200L
        interpolator = if (targetFraction > startFraction) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            setTopBarFraction(f)
        }
        start()
    }
}

internal fun MainActivity.animateBottomBarOnlyTo(targetFraction: Float, animated: Boolean = true) {
    bottomBarAnimator2?.cancel()
    if (!animated || uiState.bottomBarFullHeightPx == 0) {
        setBottomBarFraction(targetFraction)
        return
    }
    val startFraction = uiState.bottomBarFraction
    if (startFraction == targetFraction) return
    bottomBarAnimator2 = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
        duration = 200L
        interpolator = if (targetFraction > startFraction) AccelerateInterpolator() else DecelerateInterpolator()
        addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            setBottomBarFraction(f)
        }
        start()
    }
}

private fun MainActivity.refHeightDp(mode: String, position: String): Float {
    // Reference height the scroll delta is divided by — bigger values make the bar
    // hide slower, matching Chrome's roughly one full bar height to hide it.
    val px = when (mode) {
        "search_bar" -> if (position == "bottom") uiState.bottomBarFullHeightPx.takeIf { it > 0 } ?: uiState.topBarFullHeightPx else uiState.topBarFullHeightPx
        else -> uiState.bottomBarFullHeightPx.takeIf { it > 0 } ?: uiState.topBarFullHeightPx
    }
    return (px * 1.2f).coerceAtLeast(1f)
}

internal fun MainActivity.attachScrollListener(webView: WebView) {
    var localVelocityTracker: VelocityTracker? = null

    val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
            if (mode != "off") {
                val position = prefs.getString("address_bar_position", "top") ?: "top"
                // v1.2.23: Chrome-like hide semantics. distanceY > 0 means the user is
                // dragging the finger down (page content scrolls up), which must reveal
                // the bar — and it must reveal *immediately*, never park halfway. The old
                // code divided by refHeight*1.5 and only snapped on release, so a slow
                // scroll could leave the bar stuck half-hidden forever.
                //
                // Chrome's actual behavior: any downward drag fully reveals the bar
                // (fraction goes to 0), upward drags hide it progressively, and release
                // snaps deterministically — below half hidden → fully visible, at or
                // above half hidden → fully hidden. No mid-way state survives a release.
                val delta = (distanceY / refHeightDp(mode, position)).coerceIn(-1f, 1f)
                val revealing = delta > 0f
                when (mode) {
                    "search_bar" -> {
                        if (position == "bottom") {
                            val newFrac = if (revealing) (uiState.bottomBarFraction - delta).coerceIn(0f, 1f) else (uiState.bottomBarFraction + delta).coerceIn(0f, 1f)
                            setBottomBarFraction(newFrac)
                        } else {
                            val newFrac = if (revealing) (uiState.topBarFraction - delta).coerceIn(0f, 1f) else (uiState.topBarFraction + delta).coerceIn(0f, 1f)
                            setTopBarFraction(newFrac)
                        }
                    }
                    "navigation_bar" -> {
                        val newFrac = if (revealing) (uiState.bottomBarFraction - delta).coerceIn(0f, 1f) else (uiState.bottomBarFraction + delta).coerceIn(0f, 1f)
                        setBottomBarFraction(newFrac)
                    }
                    "both" -> {
                        val newFrac = if (revealing) (uiState.bottomBarFraction - delta).coerceIn(0f, 1f) else (uiState.bottomBarFraction + delta).coerceIn(0f, 1f)
                        setTopBarFraction(newFrac)
                        setBottomBarFraction(newFrac)
                    }
                }
            }
            return false
        }
    })

    webView.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                localVelocityTracker?.recycle()
                localVelocityTracker = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val mode = prefs.getString("scroll_hide_mode", "off") ?: "off"
                if (mode != "off") {
                    // v1.2.23: deterministic Chrome-style snap on release — no bar may
                    // remain half-hidden after the finger leaves the screen.
                    localVelocityTracker?.computeCurrentVelocity(1000)
                    val vy = localVelocityTracker?.yVelocity ?: 0f
                    val position = prefs.getString("address_bar_position", "top") ?: "top"
                    val currentFrac = when (mode) {
                        "search_bar" -> if (position == "bottom") uiState.bottomBarFraction else uiState.topBarFraction
                        else -> uiState.bottomBarFraction
                    }
                    // A committed fling beats the half-way rule: fast upward fling hides,
                    // fast downward fling reveals. Otherwise the bar below half-hidden
                    // reappears, at or above half-hidden it hides.
                    val snapToHidden = when {
                        vy < -500f -> true
                        vy > 500f -> false
                        else -> currentFrac >= 0.5f
                    }
                    val target = if (snapToHidden) 1f else 0f
                    when (mode) {
                        "search_bar" -> {
                            if (position == "bottom") animateBottomBarOnlyTo(target)
                            else animateTopBarOnlyTo(target)
                        }
                        "navigation_bar" -> animateBottomBarOnlyTo(target)
                        "both" -> animateBottomBarTo(target)
                    }
                }
                localVelocityTracker?.recycle()
                localVelocityTracker = null
            }
        }
        localVelocityTracker?.addMovement(event)
        detector.onTouchEvent(event)
        false
    }
}

internal fun MainActivity.injectScrollTracker(webView: WebView) {
    webView.evaluateJavascript(loadJsAsset("scroll_tracker.js"), null)
}

internal fun MainActivity.injectBottomNavDetector(webView: WebView) {
    webView.evaluateJavascript(loadJsAsset("bottom_nav_detector.js"), null)
}

internal fun MainActivity.injectCanvasTouchDetector(webView: WebView) {
    webView.evaluateJavascript(loadJsAsset("canvas_touch_detector.js"), null)
}

internal fun MainActivity.isYouTubeShorts(): Boolean {
    val url = tabManager.activeTab?.webView?.url ?: return false
    return url.contains("youtube.com/shorts", ignoreCase = true)
}
