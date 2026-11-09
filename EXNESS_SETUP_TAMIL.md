# JARVIS BLUE v1.1.6 — Exness Integration (தமிழ்)

## தற்போதைய implementation
- **Exness Trader API READ ONLY** connector — `windows-bridge/jarvis_bridge/exness_readonly.py`.
- Ed25519 signatures with required `EXN-*` headers; signed GET requests only.
- Gateway discovery from DNS widget -> account Access Point -> account read.
- Local Windows Bridge authenticated endpoint `/api/broker/exness/account` — sanitized account fields only.
- Kotlin Gold UI-ல் institutional-style **price action proxies**: swing highs/lows, BOS, liquidity sweeps, 3-bar FVG.
- **NO API key field in Android APK. NO trading POST/DELETE/PUT. NO order placement.**
- Gold data currently uses Twelve Data or manual CSV. **Exness quotes are NOT wired into Android Gold screen yet.**
- This code is NOT connected to your real Exness account until credentials are configured privately.

## Exness credentials type
The official Trader API needs an **Exness trading account**, `EXN_API_KEY`, **Ed25519 private signing key**, a numeric account ID, and Gateway URL from the **DNS widget** in Key Management.
MetaTrader account logins and Exness Partner API keys are **different** and cannot just be pasted in this integration.

Official docs:
- https://get.exness.help/hc/en-us/articles/27866287512476-Exness-API
- https://www.exness-api.com/documentation/create-api-key
- https://www.exness-api.com/trading/guides/code-examples

## Windows 10/11 private configuration

Windows PowerShell (commands **one at a time**, no secrets in public files):

```powershell
cd 'C:\Users\Mihran\Desktop\JARVIS-BLUE-MOBILE\windows-bridge'
py -m pip install -r requirements.txt
$env:EXN_API_KEY = Read-Host 'Exness Key ID'
$env:EXN_PRIVATE_KEY = Read-Host 'Exness Ed25519 private key' -MaskInput
$env:EXN_ACCOUNT_ID = Read-Host 'Trading Account ID'
$env:EXN_DISCOVERY_BASE_URL = Read-Host 'HTTPS Gateway URL from Exness DNS widget'
py -m jarvis_bridge.exness_readonly
```

**Note:** `-MaskInput` requires PowerShell 7.3+; in Windows PowerShell 5.1, use PowerShell 7 or a local credential manager instead. It still holds your key in process memory while the command runs. Do not save PowerShell transcripts containing keys; do not print key values. Use a proper secrets manager for unattended use. Avoid setting permanent Machine/User environment variables with plaintext secrets.

`py -m jarvis_bridge.exness_readonly` returns only sanitized account metadata. It never sends orders.

To enable the optional Windows bridge read-only account endpoint in same PowerShell session:

```powershell
$env:JARVIS_BRIDGE_SECRET = Read-Host 'Existing Jarvis bridge secret' -MaskInput
py -m jarvis_bridge.server --host 127.0.0.1 --port 8765
```

**Never expose port 8765 directly to public internet.** Use authenticated VPN or private tunnel. The existing Bridge HMAC signature is required to access `/api/broker/exness/account`.

## Gold institutional analysis explanation
`BOS` = close past a confirmed swing pivot; `liquidity sweep` = wick breaches prior 20-candle high/low but closes inside; `FVG` = three-candle gap. These are **price-pattern heuristics**, not proof that any institution placed an order. Exness market data alone cannot reveal all bank order-flow.

## Safe roadmap
1. Test read-only Exness access on Windows, not live-trading permissions.
2. Use verified official instrument list and symbol precision for XAU/USD.
3. Stream quote and OHLC candle data from Exness via server -> secure bridge -> mobile, with freshness/spread checks.
4. Integrate H4/H1/M15 confirmations, macro calendar, slippage and spread filters; default WAIT if missing data.
5. Paper-trade and out-of-sample/backtest with commissions and slippage; include false positives and drawdown.
6. Only after independent validation, consider trade drafting with human biometric approval, maximum loss limits, kill-switch; no auto-execution by default.

## Security
Keep keys out of APK, GitHub, screenshots and chat. If compromised, revoke and rotate. Limit scopes to read-only and selected trading account, use IP restrictions and key expiration where offered. Keep signed request logs disabled.

## Local tests

```powershell
cd windows-bridge
py -m unittest discover -s tests -v
```

The setup never asks you to send your keys to ChatGPT.
