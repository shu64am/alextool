package com.alexmodzofc.tool.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.alexmodzofc.tool.ui.theme.AlexToolComposeTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

/** Fetches a markdown document from a URL and shows it in a Compose dialog rendered inline in
 *  the host activity's own composition (via [OverlayHostActivity]), rather than as a separate
 *  ComposeView mounted on the window's decor view. Kept as a plain object with a
 *  show(context, title, url) signature (rather than a Composable) so every existing call site
 *  — Activities and non-Compose call sites alike — can keep invoking it the same way. */
object DocumentViewer {

    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    const val PRIVACY_POLICY_URL =
        "https://raw.githubusercontent.com/shu64am/alextool/main/app/src/main/assets/privacy_policy.md"
    const val TERMS_URL =
        "https://raw.githubusercontent.com/shu64am/alextool/main/app/src/main/assets/terms_of_service.md"
    const val CHANGELOG_URL =
        "https://raw.githubusercontent.com/shu64am/alextool/main/CHANGELOG.md"
    const val ATTRIBUTION_URL =
        "https://raw.githubusercontent.com/shu64am/alextool/main/Attribution.md"

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun show(context: Context, title: String, url: String) {
        val host = context.findActivity() as? OverlayHostActivity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val theme = prefs.getString("app_theme", "dark") ?: "dark"
        val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
        val state = DocumentViewerUiState()

        host.overlayContent = {
            AlexToolComposeTheme(theme = theme) {
                DocumentViewerDialog(
                    title = title,
                    state = state,
                    hideStatusBar = hideStatusBar,
                    onDismiss = { host.overlayContent = null }
                )
            }
        }

        executor.submit {
            try {
                val markdown = loadDocument(context, url)
                mainHandler.post {
                    state.markdown = markdown
                    state.isLoading = false
                }
            } catch (_: Exception) {
                mainHandler.post {
                    state.isError = true
                    state.isLoading = false
                }
            }
        }
    }

    /** Loads a document: bundled asset first (works offline), then falls back to the network URL. */
    private fun loadDocument(context: Context, url: String): String {
        val bundled = bundledAssetFor(url)
        if (bundled != null) {
            runCatching {
                context.assets.open(bundled).bufferedReader().use { it.readText() }
            }.let { result ->
                if (result.isSuccess) return result.getOrThrow()
            }
        }
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return response.body.string()
    }

    private fun bundledAssetFor(url: String): String? = when {
        url.contains("PRIVACY_POLICY") -> "privacy_policy.md"
        url.contains("TERMS_OF_SERVICE") -> "terms_of_service.md"
        url.contains("CHANGELOG") -> "changelog.md"
        url.contains("Attribution") -> "attribution.md"
        else -> null
    }
}
