# JARVIS BLUE MOBILE — GitHub Release + In-app Update அமைப்பு

## GitHub-ல் ஏற்கெனவே செய்யப்பட்ட வேலை

- PRIVATE Android source: https://github.com/mihran94/jarvis-blue-android
- PUBLIC APK download channel: https://github.com/mihran94/JARVIS-Mobile-Beta
- Android GitHub workflow: https://github.com/mihran94/jarvis-blue-android/actions/workflows/android-release.yml
- Source repo-ல் manual signed APK Release workflow மற்றும் setup documentation commit செய்யப்பட்டுள்ளன.
- APK இன்னும் compile/publish செய்யப்படவில்லை; `update.json` public feed இன்னும் உருவாகவில்லை.
- முக்கியம்: private GitHub-ல் v1.0.0 source மட்டும் உள்ளது; கீழே உள்ள v1.1.7 source-ஐ அதற்கு sync செய்த பிறகுதான் v1.1.7 release இயங்கும்.

## Exness / AI keys

உங்கள் Exness Ed25519 private key, API Key ID, மற்ற AI provider keys அனைத்தும் private Windows bridge / உங்கள் own local settings-லேயே இருக்க வேண்டும். GitHub repository, GitHub Release APK, APK source, ZIP-ல் ஒருபோதும் சேர்க்க வேண்டாம்.

## GitHub → Settings → Secrets and variables → Actions

Private source repo-வில் இதை configure செய்ய வேண்டும் (secrets values ChatGPT-க்கு அனுப்ப வேண்டாம்):

| Secret name | Value |
| --- | --- |
| `JARVIS_RELEASE_KEYSTORE_BASE64` | Existing original release keystore `.p12` file-ன் base64; அதே certificate இருக்க வேண்டும் |
| `JARVIS_RELEASE_STORE_PASSWORD` | Existing keystore store password |
| `JARVIS_RELEASE_KEY_ALIAS` | Existing key alias |
| `JARVIS_RELEASE_KEY_PASSWORD` | Existing key password |
| `JARVIS_BETA_RELEASE_TOKEN` | Fine-grained GitHub PAT — `JARVIS-Mobile-Beta` repo-க்கு மட்டும் **Contents read/write** |

**Repository variable:** `JARVIS_RELEASE_CERT_SHA256` — original installed RELEASE signer certificate SHA256; முன்பு verify செய்த release-ல் `44cb295eb9e11dccd6bef68145290f3ea86d278ef14a207da60ed39509f474a7` கிடைத்தது. இதை உங்கள் உண்மையான APK signature-உடன் சரிபார்த்து வைத்த பிறகு அமைக்கவும்.

GitHub Actions environment `android-release` உருவாக்கி required reviewers approval வைத்தால் இன்னும் பாதுகாப்பானது.

### Windows PC-இல் keystore base64 உருவாக்குதல்

PowerShell-ல் (உங்கள் original keystore இருக்கும் பாதையை மாற்றவும்):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\to\jarvis-release.p12')) | Set-Clipboard
```

இதன் output-ஐ chat/GitHub issue-ல் paste செய்ய வேண்டாம். GitHub Secret value field-ல் மட்டும் paste செய்யவும். Clipboard-ல் இருப்பதால் பிறகு clear செய்யவும்.

## v1.1.7 source sync

உங்கள் PC-ல் v1.1.7 source ZIP extract செய்து, private GitHub repo-வை தனி folder-ல் clone செய்யவும். Android project source மட்டும் copy செய்யவும். `.git`, `.venv`, `.gradle`, `build`, `dist`, `local.properties`, `keystore` (signing material), `.env`, `*.p12`, `*.jks`, `*.apk` ஆகியவற்றை copy செய்ய வேண்டாம். `git status` மற்றும் diff inspect செய்த பிறகு commit/push செய்யவும். GitHub repo v1.0.0 ஆக இருக்கும் வரை release workflow request `1.1.7`-ஐத் தடுத்துவிடும்.

## Release process (பொதுவில் வெளியிடுகிறது!)

1. v1.1.7 source-ஐ private repo-க்கு sync செய்து, build, tests, Android permission review முடிக்கவும்.
2. Original release signing certificate match ஆகிறதா verify செய்யவும்; key இல்லாவிட்டால் release செய்ய வேண்டாம்.
3. GitHub → Actions → **JARVIS BLUE - Publish Android update** → Run workflow.
4. `version_name = 1.1.7`, notes கொடுத்து **confirm_public_release** தேர்வு செய்யவும்.
5. Workflow release APK-ஐ build/sign/verify செய்து, public `JARVIS-Mobile-Beta` release-ல் APK publish செய்யும்; பிறகு அதே repo-வில் `update.json` உருவாக்கும். No automatic publish on push.
6. Phone release app-ல் Settings → App updates & about → Feed URL:

```
https://raw.githubusercontent.com/mihran94/JARVIS-Mobile-Beta/main/update.json
```

இந்த URL first release வரும்வரை 404 காட்டலாம். Release பிறகு GitHub CDN cache காரணமாக சில நேரம் பழைய content இருக்கலாம்.

## குறிப்பு: Debug versus Release

`com.jarvisblue.mobile.debug` என்ற Debug APK-க்கு மேலே உள்ள feed பொருந்தாது. Public release package `com.jarvisblue.mobile`. ஒரே package ID, original signing certificate, அதிகரிக்கப்பட்ட versionCode இருந்தால்தான் பழைய app-ன் மேல் update ஆகும். Debug app-ஐ uninstall செய்யாமல் இருந்தாலும் அதற்கான data Release app-க்கு தானாக மாறாது.

## Safety

- Play Protect-ஐ disable/bypass செய்யாது.
- User confirmation இல்லாமல் install ஆகாது.
- APK public-ஆக அனைவருக்கும் download செய்யக்கூடியதாகும்; வெளியிடுவதற்கு முன் security review அவசியம்.
- Secrets configure செய்யாத நிலையில் workflow release செய்யாது.
- Current v1.1.7 source-only archive, release APK அல்ல.
