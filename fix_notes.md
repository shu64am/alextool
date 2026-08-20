# AlexTool WebView overlap fix notes

## User report (Hindi): WebView height zyada hai, page content address bar ke peeche chup raha hai.
Screenshot: Google sign-in page, Google logo partially hidden under top address bar pill.
Status bar visible above pill. Pill (56dp) at top, content scrolled ~30dp under the pill.

## Diagnosis
- MainActivity: edge-to-edge, status/nav bars transparent, WindowCompat.setDecorFitsSystemWindows(window, false)
- MainScreen: WebView+SwipeRefreshLayout fills whole screen; top/bottom padding applied via
  `view.setPadding(0, state.contentPaddingTopPx, 0, state.contentPaddingBottomPx)`
- TopToolbar: Column with .padding(top = statusBarPaddingPx) holding 56dp AddressBarRow pill.
  onGloballyPositioned stores state.topBarFullHeightPx = full measured height (inset + 56dp).
- updateMainContentInsets (delegates/MainScrollDelegate.kt):
  - visibleTop = (statusBarInsetPx + (1 - topBarFraction) * contentBarHeight) where
    contentBarHeight = topBarFullHeightPx - statusBarInsetPx
  - contentPaddingTopPx (position != bottom): searchOverlayOpen ? statusBarInsetPx :
    (topBarFraction >= 1f ? 0 : visibleTop)
- Failure mode: if statusBarInsetPx is stale (0) or mismatches the toolbar's real padding
  (e.g., insets arrive after the toolbar measured, or cached value from device with
  gesture nav reports 0 while the pill renders with the resource-based inset),
  visibleTop computes smaller than the pill's true bottom edge → content hidden under pill.
  Screenshot shows clipping of roughly status-bar height → content padding missing the inset.

## Fix (MainScrollDelegate.kt, updateMainContentInsets)
- Don't recompute the bar bottom edge from statusBarInsetPx + contentBarHeight — use the
  MEASURED topBarFullHeightPx directly, since it already includes whatever padding the
  toolbar actually rendered with:
  - visibleTop = topBarFraction >= 1f ? 0 : topBarFullHeightPx (coerced)
  - But keep Chrome-style edge-to-edge while fully hidden: fraction >= 1 → 0 (already)
  - For partial hide (fraction between 0 and 1): visibleTop =
    (topBarFullHeightPx - fraction * contentBarHeight) using measured values only.
- This guarantees WebView content never starts above the address bar's rendered bottom
  edge regardless of inset timing.

## Build
- Signed debug keystore: /home/ubuntu/alextool/app/release_keystore.jks (password android)
- Build cmd: JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/android-sdk
  ./gradlew assembleGithubRelease --no-daemon -Ddexing.number.of.buckets.limit=1 -x lintVitalGithubRelease -x lintVitalAnalyzeGithubRelease
- Output: app/build/outputs/apk/github/release/app-github-arm64-v8a-release.apk (universal also)
