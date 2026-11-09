# JARVIS BLUE MOBILE v1.1.5 — XAU/USD Strong-Setup Research (தமிழ்)

**உண்மையான வர்த்தகத்தை தானாகச் செய்வதில்லை.** "STRONG" என்பது 4/4 rules + previous 20-candle breakout என்பதையே குறிக்கும்; win probability / உறுதியான லாபம் **அல்ல**.

## Gold screen (Studio → Gold)

- **STRONG BUY · WATCH:** EMA20 > EMA50 with rising EMA20; RSI14 53–70; MACD histogram positive; latest **completed** bullish candle; candle closes **above previous 20-candle high**; ATR, candle size, EMA-extension filters pass.
- **STRONG SELL · WATCH:** inverse conditions; RSI14 30–47; last completed bearish candle **below previous 20-candle low**.
- **WAIT:** one or more required confirmations missing, price too extended, extreme/flat volatility, no genuine breakout or stale live feed. There are **no trade levels** when WAIT.

### Levels for a confirmed synthetic or data-based setup

- **ENTRY:** reference price = most recent *completed* candle close. This is not executable bid/ask.
- **CLOSE / EXIT:** if a *subsequent completed candle* closes past the calculated invalidation price **against** your trade direction, treat the hypothesis as invalidated. This is separate from the stop-loss.
- **STOP LOSS:** 1.5 × ATR14 away from reference entry (potential slippage/gaps may exceed this).
- **TAKE PROFIT 1:** 1.5 × initial risk (1.5R).
- **TAKE PROFIT 2:** 2.5 × initial risk (2.5R).
- Actual fills, spreads, commissions, leverage/margin, macroeconomic news and market liquidity are **not** evaluated.

### Candle freshness / data sourcing

- Twelve Data API key is entered manually (kept in memory, not written to project or in-app persistent storage).
- Requests are user-triggered only; the current potentially unfinished bar is excluded from analysis.
- Live-provider feed **fails closed** if the timezone or latest timestamp cannot be verified, or the latest completed bar is stale relative to the selected interval.
- CSV (70–1,500 candles, oldest first) and synthetic examples are explicitly **not** live data. No attempt to represent historic examples as today's recommendation.
- No broker API, no order execution, no auto trading. Do not treat an indicator signal as personalized financial advice.

### Tests and build (Windows)

1. Extract the ZIP on Windows 10/11 with Android SDK API 35 and Java 17 installed.
2. Run `TEST_GOLD_WINDOWS.cmd` for engine tests.
3. Run `BUILD_DEBUG_WINDOWS.cmd` to build the project. APK is expected at `app\build\outputs\apk\debug\app-debug.apk` after `BUILD SUCCESSFUL`.
4. Use standard Android installation only when the phone accepts the APK. **Do not disable/bypass Play Protect.**
5. Version name is 1.1.5 (debug application ID remains `com.jarvisblue.mobile.debug`). Update-in-app requires a trusted published APK with matching signing identity and Android confirmation.

### Accuracy limitations

This release implements a **deterministic ruleset, not an AI/ML predictive model**. A strict label does not establish a backtested or forward-tested success rate. For serious use: historical evaluation on realistic bid/ask prices, out-of-sample/walk-forward tests, transaction-cost simulation and paper trading are needed; any real trading still involves substantial risk.
