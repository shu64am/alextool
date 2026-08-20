package com.alexmodzofc.tool.extratooling

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Tampermonkey-style user-script store. Parses `.user.js` metadata blocks, persists scripts in
 * SharedPreferences, and decides per-navigation whether a script should run (matchesUrl).
 */
object UserScriptStore {

    private const val PREFS_NAME = "AlexToolExtraPrefs"
    private const val KEY_USERSCRIPTS = "userScripts"

    data class UserScript(
        var id: String = UUID.randomUUID().toString(),
        var name: String = "Untitled",
        var namespace: String = "",
        var version: String = "1.0",
        var description: String = "",
        var author: String = "",
        var matches: MutableList<String> = mutableListOf(),
        var includes: MutableList<String> = mutableListOf(),
        var excludes: MutableList<String> = mutableListOf(),
        var grants: MutableList<String> = mutableListOf(),
        var requireUrls: MutableList<String> = mutableListOf(),
        var runAt: String = "document-end",
        var noframes: Boolean = false,
        var enabled: Boolean = true,
        var source: String = "",
        var requireCache: MutableMap<String, String> = mutableMapOf(),
        var updateUrl: String = "",
        var downloadUrl: String = ""
    ) {

        fun toUserJsText(): String {
            val meta = buildString {
                appendLine("// ==UserScript==")
                appendLine("// @name        $name")
                if (namespace.isNotBlank()) appendLine("// @namespace   $namespace")
                appendLine("// @version     $version")
                if (description.isNotBlank()) appendLine("// @description $description")
                if (author.isNotBlank()) appendLine("// @author      $author")
                matches.forEach { appendLine("// @match       $it") }
                includes.forEach { appendLine("// @include     $it") }
                excludes.forEach { appendLine("// @exclude     $it") }
                grants.forEach { appendLine("// @grant       $it") }
                requireUrls.forEach { appendLine("// @require     $it") }
                if (updateUrl.isNotBlank()) appendLine("// @updateURL   $updateUrl")
                if (downloadUrl.isNotBlank()) appendLine("// @downloadURL $downloadUrl")
                appendLine("// @run-at      $runAt")
                if (noframes) appendLine("// @noframes")
                appendLine("// ==/UserScript==")
            }
            return "$meta\n\n$source"
        }

        /** Mirrors the reference toolkit: excludes first (glob), then @match (scheme:host/path), then @include. */
        fun matchesUrl(url: String, isIframe: Boolean): Boolean {
            if (noframes && isIframe) return false
            for (pat in excludes) {
                if (globMatch(pat, url)) return false
            }
            for (pat in matches) {
                if (matchPattern(pat, url)) return true
            }
            for (pat in includes) {
                if (globMatch(pat, url)) return true
            }
            return false
        }

        /** Converts an @match pattern (`*://host/path*`) into a regex. Scheme wildcard → http(s). */
        private fun matchPattern(pattern: String, url: String): Boolean {
            return try {
                val parts = pattern.removePrefix("http://").removePrefix("https://").removePrefix("*://")
                val hostPart = parts.substringBefore("/")
                val pathPart = parts.substringAfter("/", "")
                val hostRegex = hostPart.replace(".", "\\.").replace("*", ".*")
                val pathRegex = if (pathPart.isEmpty()) "/.*" else pathPart
                    .replace(".", "\\.").replace("*", ".*")
                val urlUri = Uri.parse(url)
                val scheme = urlUri.scheme?.lowercase() ?: return false
                if (scheme != "http" && scheme != "https") return false
                val urlPath = (urlUri.encodedPath ?: "/").ifEmpty { "/" }
                val schemeOk = pattern.startsWith("*://") || scheme == pattern.substringBefore("://", "*")
                val hostOk = (urlUri.host?.lowercase() ?: "").matches(Regex(hostRegex, RegexOption.IGNORE_CASE))
                val pathOk = urlPath.matches(Regex(pathRegex))
                schemeOk && hostOk && pathOk
            } catch (e: Exception) {
                false
            }
        }

        /** Simple glob-to-regex used by @include/@exclude. */
        fun globMatch(pattern: String, url: String): Boolean {
            return try {
                val regex = Regex.escape(pattern).replace("\\*", ".*").replace("\\?", ".")
                url.matches(Regex(regex))
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadUserScripts(context: Context): MutableList<UserScript> {
        val raw = prefs(context).getString(KEY_USERSCRIPTS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<UserScript>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(UserScript(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Untitled"),
                    namespace = o.optString("namespace", ""),
                    version = o.optString("version", "1.0"),
                    description = o.optString("description", ""),
                    author = o.optString("author", ""),
                    matches = optStringArray(o, "matches"),
                    includes = optStringArray(o, "includes"),
                    excludes = optStringArray(o, "excludes"),
                    grants = optStringArray(o, "grants"),
                    requireUrls = optStringArray(o, "requireUrls"),
                    runAt = o.optString("runAt", "document-end"),
                    noframes = o.optBoolean("noframes", false),
                    enabled = o.optBoolean("enabled", true),
                    source = o.optString("source", ""),
                    requireCache = optStringMap(o, "requireCache"),
                    updateUrl = o.optString("updateUrl", ""),
                    downloadUrl = o.optString("downloadUrl", "")
                ))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveUserScripts(context: Context, scripts: List<UserScript>) {
        val arr = JSONArray()
        scripts.forEach { s ->
            val cache = JSONObject()
            s.requireCache.forEach { (k, v) -> cache.put(k, v) }
            val o = JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("namespace", s.namespace)
                .put("version", s.version)
                .put("description", s.description)
                .put("author", s.author)
                .put("matches", JSONArray(s.matches))
                .put("includes", JSONArray(s.includes))
                .put("excludes", JSONArray(s.excludes))
                .put("grants", JSONArray(s.grants))
                .put("requireUrls", JSONArray(s.requireUrls))
                .put("runAt", s.runAt)
                .put("noframes", s.noframes)
                .put("enabled", s.enabled)
                .put("source", s.source)
                .put("requireCache", cache)
                .put("updateUrl", s.updateUrl)
                .put("downloadUrl", s.downloadUrl)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_USERSCRIPTS, arr.toString()).apply()
    }

    fun activeCount(context: Context): Int = loadUserScripts(context).count { it.enabled }

    fun parse(source: String): UserScript {
        val script = UserScript(source = source)
        val metaRegex = Regex("//\\s*==UserScript==\\s*(.*?)//\\s*==/UserScript==", RegexOption.DOT_MATCHES_ALL)
        val metaMatch = metaRegex.find(source)
        if (metaMatch != null) {
            val meta = metaMatch.groupValues[1]
            script.name = findTag(meta, "name") ?: "Untitled"
            script.namespace = findTag(meta, "namespace") ?: ""
            script.version = findTag(meta, "version") ?: "1.0"
            script.description = findTag(meta, "description") ?: ""
            script.author = findTag(meta, "author") ?: ""
            script.matches = findTags(meta, "match")
            script.includes = findTags(meta, "include")
            script.excludes = findTags(meta, "exclude")
            script.grants = findTags(meta, "grant")
            script.requireUrls = findTags(meta, "require")
            script.runAt = findTag(meta, "run-at") ?: "document-end"
            script.noframes = meta.contains(Regex("//\\s*@noframes"))
            script.updateUrl = findTag(meta, "updateURL") ?: ""
            script.downloadUrl = findTag(meta, "downloadURL") ?: ""
            val body = source.substring(metaMatch.range.last + 1).trim()
            script.source = body
        } else {
            script.name = "Untitled"
            script.matches.add("*://*/*")
        }
        return script
    }

    private fun findTag(meta: String, tag: String): String? {
        val m = Regex("(?i)//\\s*@$tag\\s+(.*)").find(meta) ?: return null
        return m.groupValues[1].trim()
    }

    private fun findTags(meta: String, tag: String): MutableList<String> {
        return Regex("(?i)//\\s*@$tag\\s+(.*)").findAll(meta).map { it.groupValues[1].trim() }.toMutableList()
    }

    private fun optStringArray(o: JSONObject, key: String): MutableList<String> {
        val list = mutableListOf<String>()
        val arr = o.optJSONArray(key) ?: return list
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        return list
    }

    private fun optStringMap(o: JSONObject, key: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val obj = o.optJSONObject(key) ?: return map
        obj.keys().forEach { k -> map[k] = obj.optString(k) }
        return map
    }
}
