# JARVIS BLUE MOBILE v1.1.9 — responsive UI + opt-in update checks

See [Tamil v1.1.9 release notes](V119_RESPONSIVE_AUTO_UPDATES_TAMIL.md).

**Development source only — no verified Android APK release.**

## v1.1.6 — Exness read-only integration and institutional-style structure

See [EXNESS_SETUP_TAMIL.md](EXNESS_SETUP_TAMIL.md). No live orders, no Android APK compiled, no broker secrets included.

# JARVIS BLUE MOBILE v1.1.5 — STRONG XAU/USD Research Update

**NEW:** Studio → Gold now supports strictly gated STRONG BUY / STRONG SELL / WAIT, reference Entry, candle-close Exit, protective Stop Loss, TP1 and TP2. See `docs/GOLD_XAUUSD_TAMIL.md` and `GOLD_V115_CHANGELOG_TAMIL.md`.

**IMPORTANT:** Not an AI prediction, not live quotes without your API key, not auto-trading. Play Protect checks must not be bypassed. Android build has not been verified in this environment.

# JARVIS BLUE MOBILE 1.1.4 — Friendly UI refresh

**New:** Midnight Blue + Cyan Home dashboard, one-tap shortcuts to actual Chat / Studio → Gold / Windows Control / Voice screens, expandable friendly Settings, encrypted-key input, hidden advanced provider options, persisted Dark/Light appearance, confirmation before destructive data actions. Ollama remains removed from mobile. The v1.1.3 in-app update module is preserved inside **Settings → App updates & about**.

**Build status:** source-only package. This environment does not have a complete Android SDK/Gradle build toolchain. Run `BUILD_DEBUG_WINDOWS.cmd` with JDK 17 / Android SDK 35 on your own PC; inspect Play Protect warnings rather than bypassing them. The app has NOT been installed or runtime-tested from this version. Read `UI_REFRESH_TAMIL.md`.

**Safe upgrade:** v1.1.3 → v1.1.4 debug APK retains `com.jarvisblue.mobile.debug` and needs the SAME debug signing certificate to preserve app data. Do not uninstall a working copy as a first troubleshooting step. A release build needs your private release keystore and must retain the existing signer to update it. No private release keystore or API keys are included here.

---

**Update v1.1.3 — In-app updater:** Settings → App updates. Enter your trusted HTTPS update feed URL once, then check/download/verify/install from inside the app with Android user confirmation. This is a source-only, sideload-oriented build. See [IN_APP_UPDATES_TAMIL.md](IN_APP_UPDATES_TAMIL.md).

**Update v1.1.2:** See `PROVIDER_REFRESH_README_TAMIL.md` for current provider setup. Ollama support has been removed from this Android build; older sections below describe the original 1.1.0 release and may be outdated.

# JARVIS BLUE MOBILE

**Important:** This is modified SOURCE, not a compiled/tested APK. Android startup crash and earlier Play Protect concerns are not proven resolved. Build and test on your own device.

**A private, voice-controlled Android AI assistant that can actually do things on
your phone — under a permission and risk gate you control.**

Kotlin · Jetpack Compose · Clean Architecture · Room + SQLCipher · Android Keystore ·
OpenAI / Gemini / MiniMax · Tamil, English, French · **minSdk 31 (Android 12)**

---

## NEW — XAU/USD Gold Intelligence (research only)

Open **Studio → Gold** for XAU/USD analysis with EMA20/50, RSI14, MACD, ATR14,
recent support/resistance, BUY WATCH / SELL WATCH / WAIT, illustrative ATR
stops/targets, and risk-sizing in troy ounces. Use your own Twelve Data key
for manual live-data refresh or paste CSV candle data. Three explicitly
synthetic BUY/SELL/WAIT demonstrations are included.

**No automatic trading, broker order placement, guaranteed outcomes or
unverified machine-learning price predictions.**
See [`docs/GOLD_XAUUSD_TAMIL.md`](docs/GOLD_XAUUSD_TAMIL.md).

---

## What it is

JARVIS BLUE MOBILE is a *system-integrated* assistant, not a chat wrapper. It can
launch apps, read notifications you allow it to read, set alarms, draft messages
for you to send, organise files you grant it access to, inspect TLS on networks
you own, run a quantum-circuit simulation, and monitor your always-on Windows
JARVIS BLUE desktop over a private tunnel.

Every one of those actions goes through a single gate that validates the
arguments, checks your grants, evaluates the risk and asks you to confirm when
it matters. The AI can *request* an action; it can never perform one.

---

## Quick start

```bash
# 1. Toolchain: JDK 17 + Android SDK 35 (see docs/BUILD.md)
export ANDROID_HOME="$HOME/Android/sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 2. Build
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # requires private release keystore

# 3. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk  # same debug signing key required
```

Then open the app, go to **Settings → AI providers**, paste an API key, and
start talking. Full walkthrough in [`docs/INSTALL.md`](docs/INSTALL.md).

---

## Architecture at a glance

```
        ┌──────────────┐   requests only   ┌──────────────┐
        │  AI provider │ ────────────────▶ │  ToolExecutor│
        │  (OpenAI,    │                   │  ★ THE GATE  │
        │   Gemini,    │                   └──────┬───────┘
        │   minimax,   │                          │
        │   Ollama)    │        schema · risk · permission · confirmation
        └──────────────┘                          │
                                                  ▼
                                   Android intents · SAF files · alarms
                                   media keys · diagnostics · Windows bridge
```

Full detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Modules

| Module | What it holds |
|--------|---------------|
| `core:model` | Pure Kotlin domain models — no Android dependency |
| `core:common` | Result types, credential redaction, fuzzy text matching |
| `core:security` | Android Keystore, secret vault, grants, biometrics, app lock, emergency stop |
| `core:database` | Room + SQLCipher, encrypted at rest |
| `core:ai` | 5 providers, routing, health, budgets, tool schemas, offline fallback |
| `core:tools` | Tool registry, schema validation, risk gate, device control, security scanner, quantum simulator |
| `core:voice` | Wake-phrase matcher, on-device-first STT, TTS, language detection |
| `core:windows` | Signed HTTPS/WebSocket client for the desktop bridge |
| `core:ui` | The blue-and-black Compose design system |
| `feature:chat` | Conversation + live tool-call trace |
| `feature:assistant` | Orb, waveform, wake word, push-to-talk |
| `feature:studio` | Coding studio · cyber lab · quantum lab |
| `feature:command` | Permissions · tasks · memory · Windows · settings |
| `app` | Manifest, services, DI wiring, navigation |
| `windows-bridge/` | Reference Python desktop server |

---

## Feature status — what works, what is limited, what is absent

### ✅ Working

* **AI chat** with OpenAI, Gemini, minimax, any OpenAI-compatible gateway and
  your own Ollama server; automatic failover; provider health checks; daily
  token budget.
* **Tool calling** with schema validation — the model cannot smuggle extra
  parameters.
* **Tamil, English and French** with automatic language detection, per-language
  TTS and an honest *On-device ASR* vs *Cloud ASR* badge.
* **Wake phrase** "Hey Jarvis Blue" with fuzzy matching and Tamil/French
  variants.
* **Default-assistant integration** (`VoiceInteractionService` + system
  `RecognitionService`).
* **23 registered tools**: app launch, settings deep-links, alarms, dialler,
  message drafts, media transport, share, device diagnostics, notification
  summaries, screen text, file listing/organising, network inspection, security
  review, code quality checks, quantum simulation, memory, and the Windows
  bridge.
* **Encrypted everything**: SQLCipher database, Keystore-backed key vault,
  `allowBackup=false`.
* **Permission dashboard** with 18 capability buckets and per-tool switches.
* **Emergency stop** that survives restarts and blocks every tool.
* **Audit log** — every call, permission change and confirmation, exportable.
* **Windows bridge** with HMAC-signed requests and replay protection, plus a
  reference desktop server.
* **Quantum laboratory** — a classical state-vector simulator that is honest
  about being a simulation.

### 🟠 Limited, and the app says so

* **Background wake word** — Android 14+ forbids starting a microphone
  foreground service from the background. The service is implemented; it must be
  started from the visible app.
* **On-device ASR** — needs an on-device language pack for the locale.
* **Screen reading** — MediaProjection consent is required every time; the
  bundled analyser reports frame statistics rather than running OCR.
* **Code execution** — static analysis only. Generated code is never executed
  on-device; real builds are delegated to your Windows machine.
* **TTS** — falls back to English when a language pack is missing rather than
  reading gibberish.

### ⬜ Not included

* Acoustic hotword model (the seam is `WakeWordEngine`).
* On-device LLM (the seam is `ProviderRegistry`).
* Git operations on-device.
* Quick-settings tile / home-screen widget.
* An unrestricted remote desktop (deliberately — use a separate approved tool).

Full table with the platform reasoning: [`docs/ANDROID_COMPATIBILITY.md`](docs/ANDROID_COMPATIBILITY.md).

---

## Security properties

* **Deny by default.** 18 capability buckets, 18 in-app grants and 12 runtime
  permissions — all independent, all revocable.
* **The model never acts.** `ToolExecutor` is the only path to an effect and
  the only place that can grant or refuse one.
* **Arguments are validated.** Unknown properties, wrong types, out-of-range
  values and non-enum values are all rejected.
* **Risk is argument-aware.** A message body mentioning OTPs or bank details is
  escalated to CRITICAL regardless of the model's framing.
* **Fresh confirmations.** High-risk tools require a decision for *that* call.
* **Secrets never leak.** `Redactor` scrubs credentials on every write path —
  logs, audit rows and exports.
* **Data is encrypted.** Room + SQLCipher, keys in the Android Keystore, cloud
  backup disabled.
* **Remote access is signed.** HMAC-SHA256 over method, path, timestamp, nonce
  and body hash, with replay protection. No remote shell, ever.

Threat model: [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md).

---

## Documentation

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Module map, the execution gate, the reasoning loop, data-at-rest table |
| [`docs/PERMISSIONS_MATRIX.md`](docs/PERMISSIONS_MATRIX.md) | Every bucket, every tool, every manifest permission and why |
| [`docs/ANDROID_COMPATIBILITY.md`](docs/ANDROID_COMPATIBILITY.md) | Per-feature platform reality and Samsung/Xiaomi/OnePlus/Pixel notes |
| [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) | Trust boundaries, 12 threats with mitigations, residual risks |
| [`docs/MILESTONES.md`](docs/MILESTONES.md) | Stage-by-stage status and the defect log |
| [`docs/BUILD.md`](docs/BUILD.md) | Toolchain, signing, low-memory hosts, troubleshooting |
| [`docs/INSTALL.md`](docs/INSTALL.md) | Sideload installation, first-run setup, pairing, upgrade, uninstall |
| [`docs/TESTING.md`](docs/TESTING.md) | Test inventory, results, device test plan |
| [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) | Everyday use of every screen |

---

## Windows bridge

```powershell
cd windows-bridge
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python -m jarvis_bridge.server --generate-secret
$env:JARVIS_BRIDGE_SECRET = "<paste>"
python -m jarvis_bridge.server --host 127.0.0.1 --port 8765
```

Pair from the phone with the same secret. Reach it over Tailscale, WireGuard or
an SSH tunnel — **never** by opening the port on your router. The bridge
signing scheme is verified byte-for-byte against the Android client by a
cross-implementation test.

---

## Licence

Private project. Not published to any store. The bundled signing key is for
your own device only — generate your own before sharing the APK.
