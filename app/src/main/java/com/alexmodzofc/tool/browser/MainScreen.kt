package com.alexmodzofc.tool.browser
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward

import com.alexmodzofc.tool.browser.delegates.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alexmodzofc.tool.tabs.TabSwitcherSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.viewinterop.AndroidView
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

/**
 * Root Compose UI for [MainActivity], replacing `activity_main.xml`. Layer order mirrors the
 * old `FrameLayout` exactly (bottom to top): the WebView/SwipeRefreshLayout island, the two
 * toolbar positions, the bottom nav bar, the search overlay, the fullscreen video host, and the
 * status-bar spacer.
 */
@Composable
internal fun MainScreen(activity: MainActivity, state: MainUiState) {
    val density = LocalDensity.current
    val colors = LocalAlexToolColors.current
    var tabSwitcherOpen by remember { mutableStateOf(false) }
    val hideStatusBar = state.hideStatusBar
    val rawStatusBarPx = WindowInsets.statusBars.getTop(density)
    val rawNavBarPx = WindowInsets.navigationBars.getBottom(density)
    val rawImePx = WindowInsets.ime.getBottom(density)

    LaunchedEffect(rawStatusBarPx) {
        if (rawStatusBarPx > 0) {
            // v1.2.15: if the toolbar already measured itself before the system status-bar
            // inset was known, it captured a wrong (too small) height — the status padding
            // above the bar was missing, so the page ended up partly hidden under the
            // address bar. Forcing a re-measure fixes that permanently.
            val wasZeroInset = state.statusBarInsetPx == 0
            state.cachedStatusBarInsetPx = rawStatusBarPx
            if (wasZeroInset) {
                state.topBarFullHeightPx = 0
                state.bottomBarFullHeightPx = 0
            }
        }
    }
    LaunchedEffect(rawNavBarPx) {
        if (rawNavBarPx > 0) state.navBarInsetPx = rawNavBarPx
    }
    // v1.2.16: the Compose inset can be 0 while the real one is already cached (captured at
    // View level in onCreate), so the cached value is the primary source, not a fallback.
    val effectiveStatusBarPx = if (hideStatusBar || state.addressBarPosition == AddressBarPosition.BOTTOM) {
        0
    } else {
        rawStatusBarPx.takeIf { it > 0 } ?: state.cachedStatusBarInsetPx
    }
    LaunchedEffect(effectiveStatusBarPx) {
        state.statusBarInsetPx = effectiveStatusBarPx
        activity.updateMainContentInsets()
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        // v1.2.21: back to the proven original layout — the WebView/SwipeRefreshLayout
        // fills the whole screen and content insets are applied as VIEW PADDING (the way
        // ClintBrowser shipped). The risky explicit sizing/offsetting from v1.2.17-1.2.20
        // broke nested scroll and could collapse to zero height; padding-based layout has
        // no such failure modes. Content starts flush under the visible address bar
        // (inset capture from v1.2.16 keeps the measured bar height correct), and the
        // compact 56dp Chrome-style bar keeps the bar itself slim.
        AndroidView(
            factory = { activity.swipeRefreshView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setPadding(0, state.contentPaddingTopPx, 0, state.contentPaddingBottomPx)
            }
        )

        // Chrome-style: when the address bar is tapped, the toolbar hides and the search
        // overlay becomes the only visible bar, so the user never sees two bars at once.
        if (!state.isFullscreen && !state.searchOverlayOpen && (state.addressBarPosition == AddressBarPosition.TOP || state.addressBarPosition == AddressBarPosition.SPLIT)) {
            TopToolbar(
                activity = activity,
                state = state,
                statusBarPaddingPx = effectiveStatusBarPx,
                onTabCountClick = { tabSwitcherOpen = true },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        if (!state.isFullscreen && state.addressBarPosition == AddressBarPosition.SPLIT) {
            BottomNavBar(
                activity = activity,
                state = state,
                navBarPaddingPx = state.navBarInsetPx,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        if (!state.isFullscreen && state.addressBarPosition == AddressBarPosition.BOTTOM) {
            BottomToolbar(
                activity = activity,
                state = state,
                bottomPaddingPx = maxOf(rawImePx, state.navBarInsetPx),
                onTabCountClick = { tabSwitcherOpen = true },
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Grows out of whichever edge the tapped address bar docks to, so the overlay reads
        // as that bar expanding into fullscreen rather than an unrelated screen swap.
        AnimatedVisibility(
            visible = state.searchOverlayOpen,
            enter = fadeIn(tween(200)) + expandVertically(
                animationSpec = tween(300),
                expandFrom = if (state.searchOverlayIsBottom) Alignment.Bottom else Alignment.Top
            ),
            exit = fadeOut(tween(150)) + shrinkVertically(
                animationSpec = tween(250),
                shrinkTowards = if (state.searchOverlayIsBottom) Alignment.Bottom else Alignment.Top
            )
        ) {
            SearchOverlay(
                initialText = state.searchQuery,
                isBottom = state.searchOverlayIsBottom,
                hint = stringResource(R.string.search_bar_hint, stringResource(engineNameRes(activity.prefs.getString("search_engine", "google") ?: "duckduckgo"))),
                statusBarPx = if (state.searchOverlayIsBottom) 0 else state.statusBarInsetPx,
                suggestions = state.suggestions,
                voiceResult = state.voiceResult,
                onVoiceResultConsumed = { state.voiceResult = null },
                onQueryChange = { activity.onSearchQueryChanged(it) },
                onSubmit = { activity.onSearchSubmitted(it) },
                onVoiceSearch = { activity.handleVoiceSearchTap() },
                onClose = { activity.closeSearchOverlay() },
                onSuggestionClick = { activity.onSuggestionChosen(it) },
                onSuggestionDelete = { activity.onSuggestionHistoryDelete(it) },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.isFullscreen) {
            AndroidView(
                factory = { activity.fullscreenContainerView },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }

        if (tabSwitcherOpen) {
            TabSwitcherSheet(activity = activity, onDismiss = { tabSwitcherOpen = false })
        }

        state.imageLongPressRequest?.let { req ->
            com.alexmodzofc.tool.browser.sheets.ImageLongPressSheet(request = req, activity = activity, onDismiss = { state.imageLongPressRequest = null })
        }
        state.linkLongPressRequest?.let { req ->
            com.alexmodzofc.tool.browser.sheets.LinkLongPressSheet(request = req, activity = activity, onDismiss = { state.linkLongPressRequest = null })
        }
        state.contentPreviewRequest?.let { req ->
            com.alexmodzofc.tool.browser.sheets.ContentPreviewSheet(request = req, activity = activity, onDismiss = { state.contentPreviewRequest = null })
        }

        val hideStatusBarPref = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_status_bar", false) }
        com.alexmodzofc.tool.ui.listscreen.ConfirmDialogHost(state.confirmDialogConfig, hideStatusBarPref) { state.confirmDialogConfig = null }
        state.conflictDialogRequest?.let { req ->
            com.alexmodzofc.tool.downloads.DownloadConflictDialog(req, hideStatusBarPref) { state.conflictDialogRequest = null }
        }
        state.webPermissionDialogRequest?.let { req ->
            com.alexmodzofc.tool.ui.WebPermissionDialog(req, hideStatusBarPref) { state.webPermissionDialogRequest = null }
        }
        state.popupAlertRequest?.let { req ->
            com.alexmodzofc.tool.browser.dialogs.PopupAlertDialog(req, hideStatusBarPref) { state.popupAlertRequest = null }
        }
        state.refreshLinkDialogRequest?.let { req ->
            com.alexmodzofc.tool.browser.dialogs.RefreshLinkDialog(req, hideStatusBarPref) { state.refreshLinkDialogRequest = null }
        }
        state.openInAppRequest?.let { req ->
            com.alexmodzofc.tool.browser.webview.OpenInAppDialog(req, hideStatusBarPref) { state.openInAppRequest = null }
        }

        // Status-bar strip removed in v1.2.13: the TopToolbar already reserves the same
        // inset with its own top padding, so a second overlay box on top of it created a
        // visible double strip / dead space above the page content. The toolbar padding
        // alone keeps the WebView content flush against the bottom edge of the bar.
    }
}

@Composable
private fun TopToolbar(
    activity: MainActivity,
    state: MainUiState,
    statusBarPaddingPx: Int,
    onTabCountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAlexToolColors.current
    val density = LocalDensity.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val contentBarHeight = (state.topBarFullHeightPx - state.statusBarInsetPx).toFloat()
                translationY = -state.topBarFraction * contentBarHeight
            }
            .onGloballyPositioned { coordinates ->
                val h = coordinates.size.height
                if (h > 0 && state.topBarFullHeightPx != h) {
                    state.topBarFullHeightPx = h
                    activity.swipeRefreshView.setProgressViewOffset(false, h + 4, h + 72)
                    activity.updateMainContentInsets()
                }
            }
            // v1.2.23: the status-bar spacer carries no background — with the status
            // bar now transparent (v1.2.22), a colored strip above the pill would show
            // up as a dead bar and get painted over the page when the bar is partially
            // hidden. The pill keeps its own Surface background, exactly like Chrome.
            .background(Color.Transparent)
            .padding(top = with(density) { statusBarPaddingPx.toDp() })
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AddressBarRow(
                activity = activity,
                isIncognito = state.isIncognito,
                addressBarText = state.addressBarTextTop,
                isSecure = state.addressBarSecureTop,
                tabCountText = state.tabCountText,
                onAddressBarClick = { activity.openSearchOverlay(isBottom = false) },
                onTabCountClick = onTabCountClick
            )
            if (state.isPageLoading) {
                LinearProgressIndicator(
                    progress = { state.pageLoadProgress / 100f },
                    color = colors.primary,
                    trackColor = colors.surface,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun BottomToolbar(
    activity: MainActivity,
    state: MainUiState,
    bottomPaddingPx: Int,
    onTabCountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAlexToolColors.current
    val density = LocalDensity.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = state.bottomBarFraction * state.bottomBarFullHeightPx }
            .onGloballyPositioned { coordinates ->
                val h = coordinates.size.height
                if (h > 0 && state.bottomBarFullHeightPx != h) {
                    state.bottomBarFullHeightPx = h
                    activity.updateMainContentInsets()
                }
            }
            // v1.2.23: same no-strip rule for the bottom-docked bar — the navigation
            // inset spacer is background-less; only the pill area is painted.
            .background(Color.Transparent)
            .padding(bottom = with(density) { bottomPaddingPx.toDp() })
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            if (state.isPageLoading) {
                LinearProgressIndicator(
                    progress = { state.pageLoadProgress / 100f },
                    color = colors.primary,
                    trackColor = colors.surface,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
            AddressBarRow(
                activity = activity,
                isIncognito = state.isIncognito,
                addressBarText = state.addressBarTextBottom,
                isSecure = state.addressBarSecureBottom,
                tabCountText = state.tabCountText,
                onAddressBarClick = { activity.openSearchOverlay(isBottom = true) },
                onTabCountClick = onTabCountClick
            )
        }
    }
}

@Composable
private fun BottomNavBar(
    activity: MainActivity,
    state: MainUiState,
    navBarPaddingPx: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalAlexToolColors.current
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { translationY = state.bottomBarFraction * state.bottomBarFullHeightPx }
            .onGloballyPositioned { coordinates ->
                val h = coordinates.size.height
                if (h > 0 && state.bottomBarFullHeightPx != h) {
                    state.bottomBarFullHeightPx = h
                    activity.updateMainContentInsets()
                }
            }
            .background(colors.surface)
            .padding(bottom = with(density) { navBarPaddingPx.toDp() }),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
    ) {
        NavIconButton(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), enabled = state.canGoBack) { activity.navGoBack() }
        NavIconButton(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward), enabled = state.canGoForward) { activity.navGoForward() }
        NavIconButton(androidx.compose.material.icons.Icons.Filled.Home, stringResource(R.string.home), enabled = true) { activity.navGoHome() }
        NavIconButton(
            if (state.isPageLoading) androidx.compose.material.icons.Icons.Filled.Close else androidx.compose.material.icons.Icons.Filled.Refresh,
            stringResource(R.string.refresh),
            enabled = true
        ) { activity.navRefreshOrStop() }
        NavIconButton(
            if (state.isBookmarked) androidx.compose.material.icons.Icons.Filled.Bookmark else androidx.compose.material.icons.Icons.Filled.BookmarkBorder,
            stringResource(R.string.content_desc_bookmark),
            enabled = state.hasActiveUrl
        ) { activity.navToggleBookmark() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavIconButton(
    iconRes: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAlexToolColors.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxSize()
    ) {
        Icon(
            imageVector = iconRes,
            contentDescription = description,
            tint = colors.iconTint,
            modifier = Modifier.alpha(if (enabled) 1.0f else 0.38f)
        )
    }
}

/** Maps the "search_engine" preference value to its display-name string resource. */
private fun engineNameRes(engine: String): Int = when (engine) {
    "brave" -> R.string.engine_brave
    "google" -> R.string.engine_google
    else -> R.string.engine_duckduckgo
}
