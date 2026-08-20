# v1.2.28 — "LINK USED" bug diagnosis

## Screenshot observation
- Address bar: https://athexmodsgetkey.onrend... (the ALEXTRICK TOOLING link itself, NOT the decoded target!)
- Tab count = 2. The page shows "LINK USED — This link has already been used. Start a new flow."

## Root cause
`loadUrl(toolingUrl)` in MainActivity (line 412-417 onCreate / 445-450 onNewIntent):
1. Calls `restoreTabs()` FIRST — this re-creates ALL previously saved tabs as NEW tabs (openNewTabSilent loads each saved URL into a freshly created WebView) AND switchTo(activeIndex).
2. Then `loadUrl(toolingUrl)` → MainUiDelegate.loadUrl decodes the tooling URL and loads the TARGET in the active tab.

Two problems combine:
A. The restored tabs include the PREVIOUS tab that was at the bypass link's target domain — when recreated, those pages reload fresh and the site sees another request in the same flow/session → "LINK USED".
B. If the intent arrives while the app is already open (onNewIntent) restoreTabs() re-adds tabs again → duplicates + reloads everything including the tab that holds the already-used link.

Also openInBrowser (Link Toolkit → Open button) sends the generated alextrick link via EXTRA_OPEN_URL intent — that goes through the same path.

## Fix plan
1. In both onCreate and onNewIntent EXTRA_OPEN_URL paths: do NOT call restoreTabs() when a tooling URL is present. Instead, clear (or avoid restoring) pending tab sessions and load the tooling target into a single fresh tab — or keep existing tabs but NOT reload the target-domain tab.
   Simplest robust: when toolingUrl present → close all existing tabs (or just don't restore) → openNewTab with toolingUrl → loadUrl decodes + headers in that same tab. That keeps the flow isolated: one tab, one request.
   But closing all tabs destroys user's tabs — better: only for tooling URL, skip restoreTabs entirely (do not re-open saved sessions); then restoreTabs would still run next app launch? Tabs saved in TabSessionManager file — if we don't reload them they remain saved for later. OK acceptable: when deep-linking a tooling URL, app opens the bypass in a fresh clean tab state (like reference toolkit behavior). To fully avoid "LINK USED" from leftover restored tab: clear current in-memory tabs without reloading URLs.
2. Add cleanup: before loading tooling target, remove existing tabs WITHOUT triggering webview reloads (just detach; TabSessionManager still has saved state for later restore if app restarts).
3. Keep behavior when NOT a tooling URL unchanged.

## Implementation detail
- MainActivity.onCreate: if toolingUrl -> skip restoreTabs, clear tabs silently, openNewTab(toolingUrl).
- onNewIntent: same.
- "clear tabs silently": iterate tabManager tabs, remove desktop scripts, closeTab without reload side effects? tabManager.closeTab just removes; WebView may still load? No — closed tabs' WebViews are detached. Use tabManager.tabs.map copy then closeTab(0) loop. No URL loads happen on close.
- openNewTab(isIncognito=false, url=toolingUrl) → internally does switchTo + post loadUrl → loadUrl decodes alextrick → same-tab target load with Referer/Origin headers.

## Files
- app/src/main/java/com/alexmodzofc/tool/browser/MainActivity.kt (lines 412-417, 445-450)
- bump version 1.2.28 (code 50) in app/build.gradle.kts

## Build env
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64, ANDROID_HOME=/home/ubuntu/android-sdk
./gradlew assembleGithubRelease -x lintVitalGithubRelease (lint errors abort; skip via property in gradle.properties: org.gradle.jvmargs=-Xmx1536m ... ; swap 6G)
APKs → app/build/outputs/apk/github/release/ → copy to /home/ubuntu/deliver
Repo: shu64am/alextool, branch main, gh CLI logged in.
Sign with app/release_keystore.jks (local.properties creds present). Verify v2 sig block magic 0x7109871a.
