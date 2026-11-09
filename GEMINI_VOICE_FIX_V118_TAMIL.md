# JARVIS BLUE MOBILE v1.1.8 — Gemini + Voice Fix (9 Nov 2026)

## என்ன சரி செய்யப்பட்டது
- Gemini functionDeclarations parameters-க்கு OpenAI `additionalProperties` / `default` fields அனுப்பப்படாது. Nested objects-லும் சரி. Type enum `OBJECT`, `STRING` போன்ற Google Schema வடிவில் அனுப்பப்படும்.
- Gemini இன்னும் HTTP 400 tool declaration error அளித்தால், tool execute செய்யாமல் **ஒரே ஒரு முறை text-only fallback** முயற்சிக்கும். Tool execution நடந்ததாகக் கூறக் கூடாது.
- Chat agent ஒரு பதிலுக்குப் பிறகும் ஆறு முறை Gemini-க்கு request அனுப்பிய bug சரி; `return@repeat` → `break`.
- Chat prompt-ல் அதே user message இருமுறை சேர்வது சரி.
- Android 11+ recognition service manifest query சேர்க்கப்பட்டது. On-device recognition இல்லாவிட்டால் Push-to-talk Android system recognizer-ஐப் பயன்படுத்த முயலும்.
- Voice page-ல் Voice settings, TTS voice-pack installation buttons, உண்மையான recognition status.
- Android TTS asynchronous initialization முடிந்தபிறகு voice pack status காட்டும்.
- Push-to-talk → AI text reply + voice pack இருந்தால் TTS. Tool commands voice transcript மூலம் தானாக execute செய்யப்படாது.
- Stop button உடனே active listening coroutine cancel செய்யும்.
- Wake word user switch மூலம் foreground service start/stop; privacy காரணமாக wake-word listening on-device recognizer மட்டும், cloud இல்லை.
- System recognition proxy self-recursion தடுக்கப்பட்டது.

## Windows APK build
1. இந்த ZIP-ஐ extract செய்யவும். Java 17 / Android SDK 35 நிறுவியுள்ளதா உறுதிப்படுத்தவும்.
2. CMD-ல் extract folder திறந்து `BUILD_DEBUG_WINDOWS.cmd` இயக்கவும்.
3. `BUILD SUCCESSFUL` வந்தால் `app\build\outputs\apk\debug\app-debug.apk` உருவாகும்.
4. பழைய debug app-ன் signing key அதே என்றால் `INSTALL_DEBUG_USB.cmd` மூலம் update செய்யலாம். Debug மற்றும் release package வேறு என்பதால் ஒன்றுக்கு ஒன்று update ஆகாது.
5. Google / Samsung Speech recognition சேவை இல்லாவிட்டால் Android Settings → Apps → Default apps / Voice input-ல் install/enable செய்யவும். Tamil/English TTS voice pack phone-ல் install செய்ய வேண்டியிருக்கலாம்.

## எதை உறுதிப்படுத்த முடிந்தது?
- Kotlin Gemini ToolSchema நிஜ code + local JSON/model stubs மூலம் compile செய்து sample schema tests PASS.
- XML manifest parsing / Android package queries PASS.
- Source static regression checks PASS.
- **Android SDK/Gradle dependencies இந்த isolated container-ல் இல்லை**; முழு APK build, Samsung Android 12 test, Gemini API live calls இன்னும் செய்யப்படவில்லை.
- API keys, keystore, Exness credentials இந்த source-ல் இணைக்கப்படவில்லை.
- GitHub Actions-ல் build/release run செய்யவில்லை.

## எதிர்கால release
v1.1.8 versionCode 9. Signed release-க்கு முதல் release keystore-யே பயன்படுத்த வேண்டும். APK verify/compile வெற்றிபெறாமல் `update.json` அல்லது public release publish செய்யக் கூடாது.
