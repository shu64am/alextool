package com.alexmodzofc.tool.extratooling

import org.json.JSONObject

/**
 * Builds the Tampermonkey-style GM bootstrap injected for each enabled user script.
 * Defines `window.GM_info`, `window.GM`, and `GM_*` globals used by the script bridge
 * (`GMBridge` JavascriptInterface attached per script in the WebViewClient).
 */
object UserScriptInjector {

    fun buildGMBootstrap(script: UserScriptStore.UserScript): String {
        val gmInfo = JSONObject()
            .put("script", JSONObject()
                .put("name", script.name)
                .put("namespace", script.namespace)
                .put("version", script.version)
                .put("description", script.description)
                .put("author", script.author)
                .put("matches", JSONObject().let { o ->
                    script.matches.forEachIndexed { i, m -> o.put(i.toString(), m) }
                    o
                })
            )
            .put("scriptHandler", "AlexTool-UserScript")
            .put("version", "1.0.0")
            .put("platform", JSONObject()
                .put("os", "android")
                .put("arch", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "")
            )
            .put("scriptMetaStr", "// ==UserScript== (parsed by AlexTool)")

        val safeName = script.name.replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
        val safeDesc = script.description.replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
        val safeId = script.id.replace("-", "_")

        return """
(function() {
  try {
    window.GM_info = $gmInfo;
    var bridge = window.GMBridge_${safeId};
    window._gmXhrCallbacks = window._gmXhrCallbacks || {};
    function gmv() { return bridge ? bridge.getValue.apply(bridge, arguments) : undefined; }
    function gsv() { return bridge ? bridge.setValue.apply(bridge, arguments) : undefined; }
    function gdv() { return bridge ? bridge.deleteValue.apply(bridge, arguments) : undefined; }
    function glv() { return bridge ? bridge.listValues.apply(bridge, arguments) : '[]'; }
    function gsc() { if (bridge) bridge.setClipboard.apply(bridge, arguments); }
    function glg() { if (bridge) bridge.log.apply(bridge, arguments); else console.log.apply(console, arguments); }
    function gnf() { if (bridge) bridge.showNotification.apply(bridge, arguments); }
    function gxit(d) {
      var id = Date.now() + '_' + Math.random();
      window._gmXhrCallbacks[id] = d;
      if (bridge) bridge.xmlhttpRequest(id, d.method || 'GET', d.url, JSON.stringify(d.headers || {}), d.data || '');
      return { abort: function() { if (bridge) bridge.abortXhr(id); delete window._gmXhrCallbacks[id]; } };
    }
    window.GM = {
      info: window.GM_info,
      getValue: gmv,
      setValue: gsv,
      deleteValue: gdv,
      listValues: glv,
      setClipboard: gsc,
      log: glg,
      notification: gnf,
      addStyle: function(css) {
        var s = document.createElement('style');
        s.textContent = css;
        (document.head || document.documentElement).appendChild(s);
      },
      openInTab: function(url) { if (bridge) bridge.openInTab(url); else window.open(url, '_blank'); },
      download: function(url) { if (bridge) bridge.openInTab(url); else window.open(url, '_blank'); },
      registerMenuCommand: function() { return -1; },
      unregisterMenuCommand: function() {}
    };
    window.GM_getValue = gmv;
    window.GM_setValue = gsv;
    window.GM_deleteValue = gdv;
    window.GM_listValues = glv;
    window.GM_setClipboard = gsc;
    window.GM_log = glg;
    window.GM_notification = gnf;
    window.GM_addStyle = function(css) {
      var s = document.createElement('style');
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
    };
    window.GM_xmlhttpRequest = gxit;
    window.GM_download = function(url) { if (bridge) bridge.openInTab(url); else window.open(url, '_blank'); };
    window.GM_registerMenuCommand = function() { return -1; };
    window.GM_unregisterMenuCommand = function() {};
    window.unsafeWindow = window;
    console.log('[AlexTool] GM bridge ready for: $safeName');
  } catch(e) { console.error('[AlexTool] GM bootstrap failed', e); }
})();
        """.trimIndent()
    }

    /**
     * Global page-side registry for GM_xmlhttpRequest callbacks — injected once per page via
     * onPageStarted so any script's XHR response resolves into `window._gmXhrCallback(...)`.
     */
    val XHR_REGISTRY_JS: String = "(function() { try { window._gmXhrCallbacks = window._gmXhrCallbacks || {}; window._gmXhrCallback = function(id, status, text, headersJson) { var cb = window._gmXhrCallbacks[id]; if (!cb) return; var headers; try { headers = JSON.parse(headersJson); } catch(e) { headers = {}; } var resp = { status: status, responseText: text, headers: headers }; try { var done = !!(cb.onloadend); if (cb.onerror && status === 0) cb.onerror(resp); else if (cb.onload && status > 0) cb.onload(resp); if (done && cb.onloadend) cb.onloadend(resp); } catch(e) {} delete window._gmXhrCallbacks[id]; }; } catch(e) {} })();"

    fun buildScriptInjection(script: UserScriptStore.UserScript): String {
        val bootstrap = buildGMBootstrap(script)
        val requires = buildString {
            script.requireUrls.forEach { url ->
                val cached = script.requireCache[url]
                if (!cached.isNullOrEmpty()) {
                    append("(function(){\n").append(cached).append("\n})();\n")
                }
            }
        }
        val wrapped = wrapInIife(script.source)
        return "$bootstrap\n$requires$wrapped"
    }

    /** Wraps raw script source in a self-executing function if it isn't already. */
    private fun wrapInIife(source: String): String {
        val trimmed = source.trim()
        return if (trimmed.startsWith("(function")) "$trimmed;" else "(function(){\n'use strict';\n\n$trimmed\n})();\n"
    }
}
