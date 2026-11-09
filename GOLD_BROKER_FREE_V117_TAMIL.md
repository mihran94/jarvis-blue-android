# JARVIS BLUE MOBILE v1.1.7 — Broker-independent Gold (தமிழ்)

## மாற்றங்கள்
- Gold analysis இப்போது Exness authentication அல்லது பிரான்ஸ்/இலங்கை account region மீது சார்ந்திருக்கவில்லை. **Twelve Data API** (உங்கள் key) அல்லது **manual OHLC CSV** பயன்படுத்தலாம். இது முன்பே இருந்த திறன்; UI-ல் தெளிவாகக் காட்டப்பட்டுள்ளது.
- புதிய **Require higher-timeframe agreement** toggle, default **ON**.
  - M15 → H1 + H4
  - H1 → H4 + Daily
  - H4 → Daily
  - Daily → higher timeframe இல்லாததால் WAIT; toggle OFF எனில் single-timeframe research மட்டுமே.
- Strong signal தோன்றினால் மட்டுமே higher timeframe provider requests நடக்கும். API usage கூடலாம்.
- Missing, stale, contradictory, quota-limited higher timeframe feeds → **WAIT**; Entry/Close/SL/TP அளவுகள் அனைத்தும் மறைக்கப்படும்.
- Demo / manual CSV: **NOT live, NOT higher-timeframe confirmed** என்று குறிப்பிடப்படும்.
- Broker API credentials APK-ல் சேர்க்கப்படவில்லை; Windows Exness connector மாறாமல் optional read-only.

## எங்கு பார்க்க வேண்டும்
App → Studio → Gold. Twelve Data API key field-ல் அந்த provider-ன் key மட்டும் உள்ளிடுங்கள்; Exness key அல்ல. Manual CSV-க்கு 70+ முடிந்த XAU/USD bars தேவை. Provider key RAM-ல் மட்டுமே இருக்கும்; HTTPS request-ல் provider-க்கு அனுப்பப்படும்.

## வரம்புகள்
- இந்த logic deterministic indicator/price action research மட்டும்; உண்மையான வங்கி order-flow அல்ல; இலாப உத்தரவாதம் அல்லது win probability அல்ல.
- OHLC விலைகள் broker executable bid/ask அல்ல; spreads, slippage, economic news, fees, taxes மற்றும் account suitability மதிப்பிடப்படவில்லை.
- Twelve Data API access, credits மற்றும் API plan உங்கள் provider-ஐப் பொறுத்தது.
- Automatic order placement இல்லை. Exness read-only connector-க்கான HTTP 403 இந்த feature-ஐ பாதிக்காது.
- Previous in-app update feed configured இல்லாவிட்டால் ZIP extract/build/USB install தேவை.

## Windows build
```
cd /d "C:\Users\Mihran\Desktop\and\JARVIS-BLUE-MOBILE-v1.1.7-BROKER-INDEPENDENT-GOLD"
BUILD_DEBUG_WINDOWS.cmd
```
Java 17 மற்றும் Android SDK தேவை; உங்கள் PC-ல் இவை முன்பு அமைக்கப்பட்டிருந்தன. Build ஆன பிறகு app/build/outputs/apk/debug/app-debug.apk. ஏற்கெனவே உள்ள debug app-ஐ data clear/uninstall செய்யாமல், அதே debug signing key-யுடன் update செய்யுங்கள். Play Protect warning வந்தால் bypass செய்ய வேண்டாம்.
