# JARVIS BLUE Mobile v1.1.9 — Responsive Screen + Smart Updates

**Status:** Source changes only. Android Gradle build, APK signature and Samsung device behavior have **not** been verified in this environment. No GitHub Actions release has been triggered.

## Screen-size auto detection

- Measures the available **application window width** using Compose `BoxWithConstraints` (not physical pixels/model name).
- Compact **<600dp**: bottom navigation; on windows **<390dp** show labels only for the selected tab to prevent clipped text.
- Medium **600–839dp** / Expanded **840dp+**: side `NavigationRail` instead of bottom navigation.
- Very narrow **<365dp** home content width: four shortcuts stack vertically; wider screens get two columns.
- Control Center has scrollable tabs. Works when rotated, in Android split-screen, or on foldable displays because UI reacts to window width.
- `app/src/main/kotlin/com/jarvisblue/mobile/ui/ResponsiveSizePolicy.kt` is pure Kotlin; policy was executed offline with 9 passing checks.

## Background update checks

- Settings > App updates has toggle **Automatic update checks**, disabled by default. This is an explicit opt-in; no background request before enabling.
- Optional **Wi-Fi / unmetered only** defaults ON. Android WorkManager `NetworkType.UNMETERED` means unmetered network, not necessarily Wi-Fi strictly. The check will not use metered mobile data when enabled.
- Once per ~24h when network constraints allow, WorkManager fetches an HTTPS `update.json`, verifies package/version/format, and can show a notification when a newer build is available.
- On Android 13+, notification needs `POST_NOTIFICATIONS` permission; without it, results remain in Settings.
- **NO automatic APK download or installation.** Tapping Check now, Download & verify, and Install verified update is an explicit user action and Android installer confirmation.
- Existing APK verification remains: HTTPS redirect restrictions, SHA-256 hash, expected package ID, newer version code, same APK signer certificate.
- Default public release feed for production builds (`com.jarvisblue.mobile`) is:
  `https://raw.githubusercontent.com/mihran94/JARVIS-Mobile-Beta/main/update.json`
  Until an actual release is published, this URL can return HTTP 404. Debug apps (`com.jarvisblue.mobile.debug`) have no default feed; release APK cannot overwrite them.
- Version in this ZIP is `1.1.9`, `versionCode=10`.

## For your Windows PC

1. Extract the **source** ZIP. Keep previously installed Android application and original signing material safe.
2. Execute `BUILD_DEBUG_WINDOWS.cmd` with JDK 17 + Android SDK 35.
3. Inspect build output and fix any compile errors; this ZIP is not a tested APK.
4. Install only with your device's usual security checks (don't disable Play Protect).
5. Test portrait/landscape, split-screen, Tamil text scaling, Update settings and background check notification.
6. For production APK release, verify your original release signing certificate and publish signed APK + update.json to your public release repository. `debug`/`release` APKs are not interchangeable.

## GitHub

The previous GitHub `main` is older than this ZIP. Upload v1.1.9 source into the **private Android development branch**, not a public repository. Never commit API credentials, `keystore` material, `local.properties`, or APK binaries. Leave publication disabled until Android APK compilation and device tests succeed.
