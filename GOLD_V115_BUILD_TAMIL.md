# JARVIS BLUE MOBILE v1.1.5 — XAU/USD Strong Signals

## என்ன சேர்க்கப்பட்டுள்ளது?

- STRONG BUY / STRONG SELL only when EMA trend/slope, RSI, MACD, latest **completed candle** and a prior-20-candle breakout all agree.
- Insufficient confirmation, low/high volatility, overstretched breakout, stale live provider -> WAIT. WAIT has no Entry/TP values.
- Entry (last completed candle close — not a live executable broker quote), Close/Exit on an adverse candle close, protective Stop Loss, TP1 (1.5R) and TP2 (2.5R).
- Historical CSV, synthetic examples labelled NOT LIVE and optional Twelve Data API key entered inside the app.
- Intraday timestamps requested as UTC. Data provider may return the newest still-forming candle, so it is always excluded.
- No broker execution; demo/paper research only. Strong is **not** a win probability, and 12 passing checks are **not** backtesting.

## Windows 10/11-ல் build செய்ய

1. ZIP-ஐ தனி directory-க்கு extract செய்யவும். `BUILD_DEBUG_WINDOWS.cmd` double-click செய்து இயக்கவும். Script உங்கள் JDK 17 path மற்றும் SDK API 35-ஐச் சரிபார்க்கும்.
2. `BUILD SUCCESSFUL` வந்தால் `app\build\outputs\apk\debug\app-debug.apk` உருவாகும். இந்த chat environment-ல் Android APK compile / phone test செய்யப்படவில்லை.
3. USB-debugging இணைந்த phone-ல் `INSTALL_DEBUG_USB.cmd` மூலம் **normal** ADB install மட்டும். இது Play Protect-ஐ disable/bypass செய்யாது.
4. App Settings > Studio > Gold/XAUUSD திரை (உங்கள் navigation layout-ஐப் பொறுத்தது) தேர்வுசெய்யவும். Twelve Data API key-ஐ JARVIS-ல் மட்டும் உள்ளிடவும்; chat-க்கு அனுப்ப வேண்டாம்.
5. Data source, timeframe, last candle timestamp மற்றும் WAIT status பார்க்கவும். Manual CSV பழையதாக இருக்கலாம்; அதனை live என்று கருத வேண்டாம்.

## முக்கிய பாதுகாப்பு

- ZIP-ல் APK அல்லது release-signing keystore இல்லை; debug build-க்கு உங்கள் Windows computer-ன் debug key பயன்படுத்தப்படும்.
- முன்பு நிறுவப்பட்ட app data-ஐ வைத்திருக்க அதே debug signing key வேண்டும். `INSTALL_DEBUG_USB.cmd` uninstall செய்யாது.
- Play Protect எச்சரிக்கை இன்னும் தீர்ந்ததாகச் சொல்ல முடியாது; சுட்டிக்காட்டிய பாதுகாப்புக் குறைகளைத் தீர்க்க வேண்டும்.
- Trading risk: gold/CFD leverage losses, spread, swap, slippage, broker contract sizes and market news are NOT included in theoretical levels.

## Research checks

12 pure Kotlin tests locally PASS: strong buy/sell, ordering of SL/Exit/TP, WAIT cases, CSV validation, risk bounds and historical data order. Android Gradle / physical-device tests NOT run.
