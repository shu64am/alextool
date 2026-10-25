package com.alexmodzofc.tool.bookmarks

import com.alexmodzofc.tool.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.alexmodzofc.tool.base.AlexToolActivity
import com.alexmodzofc.tool.ui.listscreen.ConfirmDialogConfig
import com.alexmodzofc.tool.ui.listscreen.ConfirmDialogHost
import com.alexmodzofc.tool.ui.rememberMaxContentWidth
import com.alexmodzofc.tool.ui.theme.AlexToolComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the Compose [BookmarksScreen]. See [com.alexmodzofc.tool.history.HistoryActivity] for the
 * general pattern this mirrors (coroutine-backed persistence, WindowSizeClass-driven adaptive
 * width, shared confirm-dialog host).
 */
class BookmarksActivity : AlexToolActivity() {

    private lateinit var uiState: BookmarksUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this) {
            when {
                uiState.isSearchMode -> { uiState.isSearchMode = false; uiState.searchQuery = "" }
                uiState.isInSelectionMode -> uiState.exitSelectionMode()
                else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        }

        uiState = BookmarksUiState()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "dark") ?: "dark"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)

        loadBookmarks()

        setContent {
            AlexToolComposeTheme(theme = theme) {
                val maxContentWidth = rememberMaxContentWidth(this)

                BookmarksScreen(
                    state = uiState,
                    maxContentWidth = maxContentWidth,
                    onExit = { finish() },
                    onOpenItem = { item -> openBookmark(item) },
                    onDeleteSelectedClick = { showDeleteConfirm() }
                )

                ConfirmDialogHost(uiState.deleteConfirm, hideStatusBar) { uiState.deleteConfirm = null }
            }
        }
    }

    private fun loadBookmarks() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { BookmarkManager.getAll(this@BookmarksActivity) }
            uiState.items = items
            uiState.isLoading = false
        }
    }

    private fun openBookmark(item: Bookmark) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
        intent.setPackage(packageName)
        startActivity(intent)
        finish()
    }

    private fun showDeleteConfirm() {
        val count = uiState.selectedCount
        if (count == 0) return
        uiState.deleteConfirm = ConfirmDialogConfig(
            title = getString(R.string.bookmarks_delete_confirm_title),
            message = getString(R.string.bookmarks_delete_confirm_message, count),
            negativeLabel = getString(R.string.action_cancel),
            positiveLabel = getString(R.string.bookmarks_delete_selected),
            onPositive = { deleteSelected() }
        )
    }

    private fun deleteSelected() {
        val toDelete = uiState.selectedKeys
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                for (url in toDelete) BookmarkManager.remove(applicationContext, url)
            }
            uiState.items = uiState.items.filterNot { it.url in toDelete }
            uiState.exitSelectionMode()
            Toast.makeText(this@BookmarksActivity, getString(R.string.bookmarks_items_deleted), Toast.LENGTH_SHORT).show()
        }
    }
}
