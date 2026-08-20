package com.alexmodzofc.tool.tabs
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.browser.MainActivity
import com.alexmodzofc.tool.ui.FaviconCache
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

/**
 * Replaces the old `BottomSheetDialogFragment` + `RecyclerView`/`TabAdapter`. [tabs] is a local
 * mirror of [MainActivity.tabManager]'s list, kept in lockstep by removing from both on close
 * (the same two-list arrangement the old Fragment/Adapter pair used) so the sheet updates
 * immediately without needing the tab manager itself to be observable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(activity: MainActivity, onDismiss: () -> Unit) {
    val colors = LocalAlexToolColors.current
    val tabs = remember { mutableStateListOf<TabPreview>().apply { addAll(activity.tabManager.previews()) } }
    // Captured once, same as the old Fragment's constructor-injected `activeIndex` — not
    // recomputed after a close, matching the original's (slightly quirky but faithful) behavior.
    val activeIndex = remember { activity.tabManager.activeIndex }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideStatusBar = remember { PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("hide_status_bar", false) }
    val listState = rememberLazyListState()
    // Consumes whatever fling velocity is left over once the list itself has scrolled as far as
    // it can, so a fast fling at either edge of the list never reaches the sheet's own drag
    // gesture and closes it. Dragging the handle, tapping the scrim, or pressing back still
    // dismiss normally since none of those go through this connection.
    val flingBoundaryConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
    fun closeTabAt(index: Int) {
        activity.onTabClosed(index)
        if (index in tabs.indices) tabs.removeAt(index)
        if (tabs.isEmpty()) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.popupBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        com.alexmodzofc.tool.ui.AlexToolDialogStatusBarEffect(hideStatusBar)
        LazyColumn(
            Modifier.fillMaxWidth().nestedScroll(flingBoundaryConnection),
            state = listState
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    val regularCount = tabs.count { !it.isIncognito }
                    val incognitoCount = tabs.count { it.isIncognito }
                    val headerParts = buildList {
                        if (regularCount > 0) add("$regularCount tab${if (regularCount != 1) "s" else ""}")
                        if (incognitoCount > 0) add("$incognitoCount incognito")
                    }
                    val headerText = if (tabs.isEmpty()) stringResource(R.string.no_tabs) else headerParts.joinToString("  ·  ")

                    // Chrome-style header: title on the left, floating circular "+" button on the right.
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(headerText, color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { activity.onNewTab(); onDismiss() }, modifier = Modifier.size(44.dp)) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxSize().background(colors.buttonBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.new_tab),
                                    tint = colors.buttonIconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(onClick = { activity.onNewIncognitoTab(); onDismiss() }, modifier = Modifier.size(44.dp).padding(start = 8.dp)) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxSize().background(colors.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.VisibilityOff,
                                    contentDescription = stringResource(R.string.new_incognito_tab),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.divider, thickness = 1.dp)

                    val normalTabs = tabs.filter { !it.isIncognito }
                    val incognitoTabs = tabs.filter { it.isIncognito }

                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp)) {
                        if (normalTabs.isNotEmpty()) {
                            TabSectionHeader(isIncognito = false)
                            ChromeTabGrid(tabs = normalTabs, onClick = { tab -> activity.onTabSelected(tabs.indexOf(tab)); onDismiss() }, onClose = { closeTabAt(tabs.indexOf(it)) })
                        }
                        if (incognitoTabs.isNotEmpty()) {
                            TabSectionHeader(isIncognito = true)
                            ChromeTabGrid(tabs = incognitoTabs, onClick = { tab -> activity.onTabSelected(tabs.indexOf(tab)); onDismiss() }, onClose = { closeTabAt(tabs.indexOf(it)) })
                        }
                    }
                }
            }
        }
    }
}

/** Chrome-like tab switcher grid: large rounded-rectangle tab cards arranged in a 2-column grid,
 *  each card showing the favicon, title and URL, with a close chip overlapping the corner. */
@Composable
private fun ChromeTabGrid(tabs: List<TabPreview>, onClick: (TabPreview) -> Unit, onClose: (TabPreview) -> Unit) {
    val colors = LocalAlexToolColors.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
            ChromeTabCard(tab = tab, onClick = { onClick(tab) }, onClose = { onClose(tab) })
        }
    }
}

@Composable
private fun ChromeTabCard(tab: TabPreview, onClick: () -> Unit, onClose: () -> Unit) {
    val colors = LocalAlexToolColors.current
    val favicon = rememberTabFavicon(tab)
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(150.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(12.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (favicon != null) {
                        Image(favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            if (tab.isIncognito) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Public,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        tab.title.ifBlank { stringResource(R.string.new_tab) },
                        color = colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                }
                Text(
                    tab.url.removePrefix("https://").removePrefix("http://"),
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).size(28.dp).padding(end = 4.dp, top = 4.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(colors.surfaceVariant.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close_tab),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabSectionHeader(isIncognito: Boolean) {
    val colors = LocalAlexToolColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isIncognito) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Tab,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(14.dp)
        )
        Text(
            stringResource(if (isIncognito) R.string.tabs_section_incognito else R.string.tabs_section_normal),
            color = colors.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// TabRow kept for backward compatibility (other call sites may reference it).
@Composable
private fun TabRow(tab: TabPreview, isActive: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    ChromeTabCard(tab = tab, onClick = onClick, onClose = onClose)
}

/** Mirrors TabAdapter's old favicon logic: incognito tabs use the memory-only cache lookup so
 *  nothing about them ever touches disk, matching the original's privacy behavior exactly. */
@Composable
private fun rememberTabFavicon(tab: TabPreview): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(tab.url, tab.isIncognito) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(tab.url, tab.isIncognito) {
        val faviconUrl = FaviconCache.faviconUrlFor(tab.url)
        if (faviconUrl.isEmpty()) return@LaunchedEffect
        if (tab.isIncognito) {
            FaviconCache.loadMemoryOnly(faviconUrl) { bmp -> bitmap = bmp }
        } else {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val cacheOnly = prefs.getBoolean("data_saver_enabled", false) && prefs.getBoolean("data_saver_disable_images", true)
            FaviconCache.load(context, faviconUrl, cacheOnly) { bmp -> bitmap = bmp }
        }
    }
    return bitmap
}
