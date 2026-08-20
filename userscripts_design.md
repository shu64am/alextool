# Userscripts feature design notes

## User request (Hindi)
1. Userscripts feature add karna — Settings ke andar NAHI, menu mein bahar (overflow menu jahan New Tab, Quiver Guard, Bookmarks, Desktop Mode, Link Toolkit, Block Domains, Settings hain).
2. Code ko GitHub repo "alextool" par push karna.

## Existing patterns found
- Menu: `app/src/main/java/com/alexmodzofc/tool/browser/menu/MenuComposables.kt`
  - `BrowserMenuSnapshot` data class holds menu state (add fields: userscriptsEnabled bool, userscriptCount int)
  - `BrowserMenuActions` class holds callbacks (add onUserscripts action)
  - `buildMenuSnapshot()` / `buildMenuActions()` extension fns on MainActivity
  - Menu items inserted in `BrowserMenuContent` Column; order: quiver guard items → bookmarks/history/reader → divider → desktop mode → divider → data saver → divider → Link Toolkit, Block Domains → divider → Settings.
  - `MenuItemRow(icon, label, enabled, checked, badge, onClick, onLongClick)` composable
  - Menu strings are Android resources (e.g., R.string.menu_link_toolkit, R.string.menu_domain_blocker, R.string.settings)
- JS injection pattern: `MainActivity.loadJsAsset(filename)` reads from assets/JavaScript/; injected via `webView.evaluateJavascript(js, null)` in `ClintWebViewClient` (line ~178) and `UserScriptInjector.XHR_REGISTRY_JS`.
- Settings pages live under `settings/` package, launched via activities in `browser/MainActivity.kt` (e.g. `onMenuLinkToolkit`, `onMenuDomainBlocker`).
- Overflow menu item handlers are `onMenu*()` methods on MainActivity (grep: onMenuLinkToolkit etc).
- Domain blocker / link toolkit screens = activities under `settings/` maybe `extratooling/`. Check: `com.alexmodzofc.tool.settings.extratooling.ExtraToolingPanes.kt`.

## Design for Userscripts
- Menu entry: "Userscripts" (icon: Code icon `Icons.Filled.Code` or `Icons.AutoMirrored.Filled.Terminal`) placed ABOVE Settings divider group, next to Link Toolkit/Block Domains.
- Checked state = userscripts globally enabled (pref `userscripts_enabled`, default true).
- Long-click = open Userscripts Manager screen (add/edit/delete/enable-disable scripts).
- Manager screen: Compose Activity `UserscriptsActivity` (like other settings activities; register in AndroidManifest; launch from `onMenuUserscripts`).
  - List of scripts: name, urlMatchPattern (glob, default "*"), enabled toggle, delete.
  - Add new: name + url pattern + JS body (text editor dialog).
  - Storage: JSON file in app private files dir (`userscripts.json`) — scripts are text, too big for SharedPreferences.
  - Data class `UserScript(name, pattern, enabled, body)`.
- Injection: in `ClintWebViewClient.onPageFinished`-like hook (or in onPageFinished delegate) after page scripts, evaluate enabled scripts whose pattern matches current URL. Pattern: simple glob with `*` → convert to regex. Match applied per script to `webView.url`.
- Where to inject: find the existing per-page script injection point — `onPageFinished(url)` in MainUiDelegate delegates, or ClintWebViewClient. Best: a delegate fn `injectUserscripts(webView)` called from onPageFinished flow (same as injectScrollTracker).
- Strings: add to `res/values/strings.xml`: userscripts, userscripts_manager, add_userscript, delete, name, url_pattern, pattern_hint, js_body, empty_userscripts etc.
- Manifest: add UserscriptsActivity (exported=false).

## Build & repo facts (from earlier phases)
- Project root: /home/ubuntu/alextool (gradle AGP 9.3.1, Gradle 9.7, compileSdk 37)
- JDK 17: /usr/lib/jvm/java-17-openjdk-amd64; ANDROID_HOME=/home/ubuntu/android-sdk
- gradle.properties: jvmargs -Xmx1536m; lint disabled via android.defaults.lint.disabled=true (property didn't work; must use -x lintVitalGithubRelease -x lintVitalAnalyzeGithubRelease)
- Build cmd: `./gradlew assembleGithubRelease --no-daemon -Ddexing.number.of.buckets.limit=1 -x lintVitalGithubRelease -x lintVitalAnalyzeGithubRelease`
- Keystore: /home/ubuntu/alextool/app/release_keystore.jks, alias alextool, pass android (in local.properties)
- Output apks: app/build/outputs/apk/github/release/app-github-arm64-v8a-release.apk (+universal)
- Version: currently 1.2.25 (versionCode 47); bump to 1.2.26 / 48 for this release.
- GitHub repo: user has repo "alextool"; push via gh CLI (logged in). repo may not exist yet → create private repo `alextool` and push (force push ok since fresh).
- git state: project dir has .gitignore only maybe no git repo → init repo, add all, commit, push.
