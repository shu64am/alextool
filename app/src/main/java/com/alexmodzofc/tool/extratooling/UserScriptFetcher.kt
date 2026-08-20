package com.alexmodzofc.tool.extratooling

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reference-style @require fetcher: downloads each script's `@require` libraries (OkHttp with a
 * mobile User-Agent, 15 s connect/read timeouts) and persists the source into
 * [UserScriptStore.UserScript.requireCache] so [UserScriptInjector] can inline it on the next
 * page load. Also powers "Install from URL" and per-script "Check updates" via
 * `@downloadURL`/`@updateURL`.
 */
object UserScriptFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /**
     * Downloads missing `@require` libraries for [scripts] and writes the content into each
     * script's requireCache. Returns true when at least one script was modified and should be
     * re-saved by the caller.
     */
    fun fetchRequires(context: Context, scripts: List<UserScriptStore.UserScript>): Boolean {
        var modified = false
        for (script in scripts) {
            for (url in script.requireUrls) {
                if (!script.requireCache[url].isNullOrEmpty()) continue
                val content = runCatching { downloadText(url) }.getOrNull() ?: continue
                script.requireCache[url] = content
                modified = true
            }
        }
        return modified
    }

    /**
     * Downloads a script from its `@downloadURL` (falling back to `@updateURL`), parses it, and
     * installs it — mirroring the reference toolkit's Install-from-URL flow.
     */
    fun installFromUrl(context: Context, url: String): UserScriptStore.UserScript? {
        val source = downloadText(url) ?: return null
        val script = UserScriptStore.parse(source)
        val scripts = UserScriptStore.loadUserScripts(context)
        val existing = scripts.indexOfFirst { it.name == script.name }
        if (existing >= 0) {
            script.id = scripts[existing].id
            script.enabled = scripts[existing].enabled
            for (u in scripts[existing].requireUrls) {
                scripts[existing].requireCache[u]?.let { script.requireCache[u] = it }
            }
            scripts[existing] = script
        } else {
            scripts.add(script)
        }
        UserScriptStore.saveUserScripts(context, scripts)
        return script
    }

    /**
     * Checks for updates against each script's `@updateURL`/`@downloadURL`. Returns a list of
     * script ids that have a newer `@version` available. Reference version comparison is
     * numeric per dot-separated segment (1.2.3 < 1.10.0).
     */
    fun checkUpdates(context: Context): List<String> {
        val scripts = UserScriptStore.loadUserScripts(context)
        val updates = mutableListOf<String>()
        for (script in scripts) {
            val updateUrl = script.downloadUrl.ifEmpty { script.updateUrl }
            if (updateUrl.isEmpty()) continue
            val source = runCatching { downloadText(updateUrl) }.getOrNull() ?: continue
            val remote = UserScriptStore.parse(source)
            if (isNewer(remote.version, script.version)) updates.add(script.id)
        }
        return updates
    }

    private fun downloadText(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", ANDROID_UA).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.code >= 400) null
            else response.body?.string()
        }
    }

    /** Numeric semver-ish comparison: 1.10.0 beats 1.2.3; non-numeric tails fall back to strings. */
    private fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split(".").map { it.toIntOrNull() }
        val b = local.split(".").map { it.toIntOrNull() }
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val ra = a.getOrElse(i) { 0 }
            val rb = b.getOrElse(i) { 0 }
            if (ra != null && rb != null) {
                if (ra > rb) return true
                if (ra < rb) return false
            } else if (ra == null || rb == null) {
                // Non-numeric segment — fall back to plain string comparison.
                val sA = a.getOrElse(i) { 0 }
                val sB = b.getOrElse(i) { 0 }
                return sA.toString().compareTo(sB.toString()) > 0
            }
        }
        return false
    }
}
