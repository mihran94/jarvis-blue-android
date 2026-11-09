# JARVIS BLUE MOBILE v1.1.2 – Provider Settings refresh

**Patch source only. This archive is not an APK and has not been compiled on an Android device.**

## Changes
- Ollama runtime provider removed from registry and code. Legacy `ProviderId.OLLAMA` enum is retained for old Room database compatibility but hidden from Settings and Chat. No request can be routed to it.
- The known retired stored Gemini ID `gemini-2.0-flash` is mapped to `gemini-3.8-flash` at runtime (without rewriting the database).
- Gemini native API default `https://generativelanguage.googleapis.com/v1beta`, `gemini-3.8-flash`. Updates retired `gemini-2.0-flash` default.
- MiniMax OpenAI-compatible default `https://api.minimax.io/v1`, `MiniMax-M3.1-Flash`; removes wrong OpenAI URL fallback.
- Generic OpenAI-compatible provider now has an independent encrypted API-key alias; **any previously entered custom provider key must be entered again**. Old Ollama key is not reused for security reasons.
- URL and model save atomically, avoiding a race between two database updates; arbitrary custom URLs require HTTPS.
- Provider routing respects explicit enabled switch + saved key; disabled/unconfigured providers are not called.
- Missing-key and disabled statuses show UNCONFIGURED rather than UNAVAILABLE; health statuses are cleared when settings change.
- MiniMax availability stays UNKNOWN until an actual chat request (no undocumented GET /models check or paid probe).
- API key entry is masked; blank Save key is disabled. Existing key persists when other settings are saved.
- Health-check Gemini key is sent as a header, not as a query-string URL.
- HttpClient now uses the client with the requested timeout instead of ignoring it.

## Configuration
| Provider | Base URL | Model ID |
|---|---|---|
| Gemini | https://generativelanguage.googleapis.com/v1beta | gemini-3.8-flash |
| OpenAI | https://api.openai.com/v1 | gpt-4o-mini |
| MiniMax | https://api.minimax.io/v1 | MiniMax-M3.1-Flash |
| Compatible | Your own trusted HTTPS OpenAI-style endpoint | provider-specific |

Each API key must be entered within the app and never put into source code or this ZIP.

## Windows build
1. Extract ZIP to a NEW folder. Do not overwrite the installed project without backup.
2. Use JDK 17 as `JAVA_HOME`, keep `local.properties` with `sdk.dir=C:/Users/Mihran/AppData/Local/Android/Sdk` or your SDK location.
3. Run `gradlew.bat assembleDebug --no-daemon --console=plain`.
4. If `BUILD SUCCESSFUL`, inspect APK signature and install via normal `adb install -r` using your own USB device.
5. Keep Play Protect enabled. If it blocks the new APK, stop and investigate.

## Compatibility notes
- `com.jarvisblue.mobile.debug` package stays the same for data preservation. Android debug signing key must match the existing install to update in place. Avoid uninstalling the existing app if data matters.
- Gradle build, integration tests, provider API tests, and Play Protect checks **not performed here**.
- Health checks only report API accessibility, not that the chosen model definitely supports tool calls. Test with normal chat after saving.

## Sources verified (2026-11-09)
- Google Gemini models: https://ai.google.dev/gemini-api/docs/models
- MiniMax OpenAI compatibility: https://platform.minimax.io/docs/api-reference/text-openai-api
