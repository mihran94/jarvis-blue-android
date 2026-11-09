# JARVIS BLUE MOBILE v1.1.4 — Friendly UI / Settings update

## மாற்றங்கள்

- Midnight Blue + cyan, பெரிய JARVIS orb மற்றும் தமிழ் உதவியாளர் subtitle உடன் புதிய Home.
- Home shortcuts: **Talk to JARVIS**, **AI Chat**, **Gold intelligence (Studio → Gold)**, **Creative studio**, **Device control**, **Set up your AI**. எல்லா buttons-க்கும் real navigation உள்ளது.
- Settings categories: AI Brain & Providers, Voice & Personality, Appearance, Gold Intelligence, Privacy & Security, App Updates & About.
- Gemini, OpenAI, MiniMax, custom provider configuration அனைத்தும் individual expandable cards. API key password போல மறைக்கப்படும்; saved key முழுவதுமாகக் காட்டப்படாது.
- URL/model advanced settings கண்ணுக்கு முதலில் தெரியாது; தேவையான போது மட்டும் திறக்கலாம். Test AI connections button உள்ளது.
- Midnight Blue dark theme default. Settings மூலம் light theme தேர்வு செய்தால் app முழுவதும் மாறும்; மறுபடியும் திறந்த பிறகும் தொடரும் (Android SharedPreferences).
- Memory deletion / audit clearing முன் confirmation. Emergency stop shortcut retained.
- Ollama mobile provider தேர்விலிருந்து நீக்கப்பட்டபடி உள்ளது. Database compatibility enum இருப்பது பழைய records படிக்க மட்டுமே.
- XAU/USD intelligence, Windows bridge, in-app update manager மாற்றப்படவில்லை. **No automatic trading.**

## Windows build (உங்கள் existing setup)

1. ZIP extract செய்து புதிய folder திறக்கவும்.
2. `BUILD_DEBUG_WINDOWS.cmd` இயக்கவும். JDK 17 மற்றும் Android SDK API 35 தேவை.
3. `BUILD SUCCESSFUL` வந்ததும் `app\build\outputs\apk\debug\app-debug.apk` இருப்பதை உறுதி செய்யுங்கள்.
4. USB debugging phone-ல் enable செய்யப்பட்டு `adb devices` மூலம் connection உறுதி செய்யுங்கள்.
5. `INSTALL_DEBUG_USB.cmd` என்பது சாதாரண Android installation மட்டுமே செய்யும்; security verification bypass செய்யாது.
6. Play Protect warning வந்தால் disable செய்யாமல் APK permissions/services-ஐ ஆய்வு செய்யவும்.

## UI smoke-test checklist

- [ ] App open & stay open (Android 12+)
- [ ] Home → Talk to JARVIS; microphone permission user-controlled
- [ ] Home → Chat
- [ ] Home → Gold, Studio-வின் Gold tab உண்மையில் திறப்பது
- [ ] Home → Studio, Dev tab திறப்பது
- [ ] Home → Control, permissions & tasks காட்டுவது
- [ ] Settings → AI providers: save key / health check / advanced fields
- [ ] Settings → Appearance: Midnight Blue / Light; restart after setting
- [ ] Settings → Privacy: data delete confirmation; do not delete accidentally
- [ ] Settings → Updates: feed இல்லாத நிலையில் no fake update; trusted HTTPS feed only
- [ ] Secrets/keystore private; no API keys in screenshots or GitHub

## உண்மையான சோதனை வரம்புகள்

Source ZIP static validation மட்டுமே இங்கே நடந்தது; Android Gradle compilation / connected device test செய்யப்படவில்லை. APK இல்லை. உங்களிடம் இருந்த Keystore crash / Play Protect warning இந்த UI update காரணமாக தீர்ந்ததாக அர்த்தமில்லை.
