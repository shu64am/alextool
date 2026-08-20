# v1.2.27 fix notes

## Issue 1: New Script dialog black input field
- File: `app/src/main/java/com/alexmodzofc/tool/settings/extratooling/UserScriptsPane.kt` (dialog composable)
- Screenshot shows a solid black rounded input card and purple dropdown on the dialog (Compose dialog default surface not themed).
- Fix: use LocalAlexToolColors (colors.cardBackground / surface) for the TextField background + content color, align with app theme like other screens.

## Issue 2: alextrick bypass not working (headers not sent as reference tool)
Reference tool (pasted_content.txt) logic for nicktrick= / alextrick=:
- `handleUrl()` in shouldOverrideUrlLoading: when URL has the param, decode and `loadUrl(target, headers)` with
  - Referer = scheme://host/
  - Origin = scheme://host  (no trailing slash)
- Also in `navigateTo()` (address bar): decodes BEFORE loading the /links path, sets headers, loads directly.

AlexTool current code:
- MainUiDelegate.loadUrl() (line 168-195): decodes tooling target, builds headers with Referer/Origin, calls `wv.loadUrl(target, headers)`. Looks similar BUT:
  - `tabManager.activeTab?.url = toolingTarget` set BEFORE headers — fine.
  - BUT referer host = uri.host of the tooling URL e.g. "alextool.links" → Referer "https://alextool.links/" — same as reference. OK.
- ClintWebViewClient.shouldOverrideUrlLoading (line 219-227): same decode+load with headers. OK.
- **Potential gap**: when user pastes/taps an alextrick link from external share (ACTION_VIEW intent), MainActivity.onNewIntent/onCreate calls `loadUrl(toolingUrl)` which decodes and loads with headers. OK.
- **Real gap found**: the domain blocker / bypass chain — the user says it doesn't go like the reference tool. Compare: reference also passes `Origin` WITHOUT trailing slash ("scheme://host"), our code passes referer with trailing slash for Origin too. Minor.
- **Key difference**: reference sets headers only on the decoded target load; our tooling URL is `https://$domain/links?alextrick=` where domain = ORIGINAL link's domain (ExtraToolingPanes line ~217). Reference uses same. 
- Another real difference: reference's generated link lives at `https://DOMAIN/links?nicktrick=` (original's domain). Same here. 
- BUT: user reports "same refer header jo hota h us se nhi ja rha" → likely the tooling link opened loads without the Referer/Origin because:
  1. If the user opens the generated link in ANOTHER tab / shares it: when app is already open, EXTRA_OPEN_URL intent arrives → loadUrl(toolingUrl) → decoded w/ headers. OK.
  2. **If user opens link while browser tab loads alextool.links or the tooling URL itself is requested from the network** — upstream 404 → page shows error. Our loadUrl decodes before network — fine.
  3. **Possible actual bug**: `decodeToolingTarget` decodes up to 5 loops but only returns if starts with http. If bypassed url is double-encoded it works. OK.
  4. **CRITICAL**: when the tooling URL is loaded via webview directly (e.g. user typed `vplink.in/links?alextrick=...` in address bar), formatUrl leaves it, loadUrl decodes → target headers with referer from host. BUT ClintWebViewClient.shouldOverrideUrlLoading fires BEFORE onPageStarted for same-tab programmatic loadUrl(url, headers)? No — programmatic loadUrl DOES NOT trigger shouldOverrideUrlLoading; the WebView goes straight to the target. That means first load has headers from loadUrl(url,headers). Good.
  5. BUT wait — ClintWebViewClient.shouldOverrideUrlLoading with headers is called only for NAVIGATIONS initiated by the page (link clicks). If the site redirects via JS window.location → shouldOverride fires → our code adds Referer/Origin = tooling host. OK.
  6. **What reference does differently**: reference ALSO keeps the target's own referer-less behavior on further navs (only the alextrick param). Nothing extra.

- **Actually likely bug**: `getDesktopHeaders`/`buildDesktopHeaders` — desktop mode headers include User-Agent etc. If desktop mode OFF, no headers? No — referer added separately.
- **Real candidate**: the user's generated alextrick URL may point to the ORIGINAL link's domain `/links` path which returns 404 server-side... but decode happens client-side before network... unless user shares link and OPENS IT IN THE DEVICE'S DEFAULT HANDLER outside app? Then no app intercept → 404 page. That's expected.
- **Most likely user-visible bug**: when opening alextrick link inside app via EXTRA_OPEN_URL from Settings Link Toolkit "Open" button — but Settings activity may start MainActivity with EXTRA_OPEN_URL only; fine.

Decision: since we can't reproduce precisely, harden the chain:
1. Keep decoding in loadUrl + WebViewClient (already there).
2. In `shouldOverrideUrlLoading`, when tooling target decoded → also clear the synthetic /links path from back history so "back" doesn't return to 404 page (reference doesn't but nice). Actually reference also suffers this. Skip.
3. Ensure Origin header uses scheme://host (no trailing slash) matching reference exactly.
4. Also ensure when user taps generated link in another app → Android opens AlexTool via intent-filter (check manifest has intent-filter for https alextool.links and *.in/links?alextrick). VERIFY manifest.

## Manifest check
- Need to confirm <intent-filter> on MainActivity catches alextrick URLs (android:host patterns).

## Findings (updated)

### Dialog theme fix — DONE
Files: `app/src/main/java/com/alexmodzofc/tool/settings/extratooling/ExtraToolingPanes.kt`
- ScriptEditDialog (line ~873) and ScriptUrlDialog (line ~938) TextFieldDefaults.colors previously used colors.onSurface/surfaceVariant which resolved to pure black/transparent on this device (Material You attr `alextoolSurfaceVariant` = #1AFFFFFF translucent → black appearance on dark popup).
- Fixed both to explicit colors matching the working inline TextField in the same file (line 154 pattern): focused/unfocused text Color.Black (light) / 0xFFF0F0F0 (dark), container 0xFFECECEC (light) / 0xFF2E2E2E (dark), placeholder secondaryText.
- Dialog popup background itself (AlexToolDialog, ui/ClintDialog.kt line 134) uses colors.popupBackground — fine.

### alextrick bypass analysis
- AlexTool decode+header load exists in TWO places:
  1. MainUiDelegate.loadUrl() line 168-195 — decodes tooling target, headers {Referer: scheme://host/, Origin: scheme://host/}, wv.loadUrl(target, headers).
  2. ClintWebViewClient.shouldOverrideUrlLoading lines 219-227 — same pattern.
- Reference tool (pasted_content.txt lines 1585-1604, 1666-1696): Referer = scheme://host/ , Origin = scheme://host (NO trailing slash).
- Difference: Origin in AlexTool has trailing slash. Fix to match reference exactly.
- Manifest (AndroidManifest.xml line 81-89): MainActivity intent-filter catches http/https/about VIEW generically (no host restriction) — alextrick links from external apps DO reach MainActivity → EXTRA_OPEN_URL handling (line 412-417 onCreate, 445-450 onNewIntent) → loadUrl(toolingUrl) → decodes + headers. Good.
- Extra bug: when opened via EXTRA_OPEN_URL, `loadUrl(toolingUrl)` works; but if the tooling URL has query with encoded chars, formatUrl passes through (starts w/ https). OK.
- Also verify: ExtraToolingPanes line ~217 builds link with domain = original's domain; "Open" button (ExtraToolingPanes ~260-275) — check what it does: likely copies + opens via intent with EXTRA_OPEN_URL.
- TODO fix: Origin header without trailing slash in both load places (MainUiDelegate + ClintWebViewClient), to exactly match reference.

### Build environment reminders
- Project: /home/ubuntu/alextool, JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64, ANDROID_HOME=/home/ubuntu/android-sdk, local.properties + debug keystore at /home/ubuntu/alextool/release/keystore.jks (storepass from earlier session: check gradle signingConfig; keystore created earlier in /home/ubuntu/alextool app dir or /home/ubuntu/alextool/release)
- Build cmd used: `cd /home/ubuntu/alextool && export JAVA_HOME=... ANDROID_HOME=... && ./gradlew assembleGithubRelease -x lintVitalGithubRelease -Plint.abortOnError=false > /tmp/build.log 2>&1`
- gradle.properties: org.gradle.jvmargs=-Xmx1536m, dexing.inprocess.maxWorkers set, lint disabled via property
- APK outputs: app/build/outputs/apk/github/release/*.apk ; copy to /home/ubuntu/deliver with friendly names AlexTool-1.x.x-arm64-v8a.apk and -universal.apk
- GitHub repo: shu64am/alextool (public), branch main. gh CLI logged in.
- Version currently 1.2.26 (code 48) in app/build.gradle.kts → bump to 1.2.27 (49)
- Memory: add swap if needed (`sudo fallocate -l 4G /swapfile2 && sudo chmod 600 /swapfile2 && sudo mkswap /swapfile2 && sudo swapon /swapfile2`), kill GradleDaemon before build.
- Debug APK exists at app/build/outputs/apk/github/debug/ too (v1.2.26).
