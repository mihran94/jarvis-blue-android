# JARVIS BLUE MOBILE — v1.1.5 Gold Signals

**மாற்றங்கள்**

1. பழைய BUY WATCH / SELL WATCH நிலைகளை `STRONG BUY · WATCH` / `STRONG SELL · WATCH` / `WAIT · NO SETUP` ஆக மாற்றியது.
2. புதிய confirmation: EMA trend/slope + RSI + MACD + completed candle direction ஆகிய 4/4 வேண்டும்; previous 20-candle range breakout மேலும் அவசியம்.
3. பாதுகாப்புக் கட்டுப்பாடு: ATR volatility, candle-body size, over-extension, 70 completed candles, stale live feed suppression; intraday timestamps explicitly requested as UTC (daily exchange timezone retained).
4. தனித்தனி **Entry**, **Close/Exit trigger**, **Stop Loss**, **Take Profit 1**, **Take Profit 2**.
5. `WAIT` வந்தால் entry/exit/TP numbers உருவாக்கப்படாது.
6. 12 pure Kotlin checks locally PASS; Android device app build / launch not verified here.
7. Demo buttons SYNTHETIC labels உடன் மட்டுமே; live XAU/USD விலை/real-time signal இல்லை.

## முக்கியக் குறிப்பு

இது perfect AI prediction அல்ல. இது தெளிவான ruleset கொண்ட **research tool**. எந்த signal-க்கும் லாப உறுதி இல்லை. Gold CFDs/forex leverage மிகப் பெரிய இழப்பை ஏற்படுத்தலாம். Paper account-ல் சோதித்த பிறகே தனிப்பட்ட முடிவுகளை எடுக்கவும்.
