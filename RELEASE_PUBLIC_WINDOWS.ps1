# JARVIS BLUE 1.1.9 — build, validate and optionally publish without GitHub Actions.
# Place in the SOURCE ROOT. This script never uploads or logs signing secrets.
param([switch]$Publish)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$Version = "1.1.9"
$VersionCode = 10
$PackageId = "com.jarvisblue.mobile"
$PublicRepo = "mihran94/JARVIS-Mobile-Beta"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $Root

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "STOP: $Message" }
}

function RunExternal([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "STOP: $Command failed with exit code $LASTEXITCODE"
    }
}

function OutputExternal([string]$Command, [string[]]$Arguments) {
    $result = & $Command @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "STOP: $Command failed with exit code $LASTEXITCODE"
    }
    return ($result | Out-String)
}

try {
    Write-Host "JARVIS BLUE $Version local release validation (no GitHub Actions)."
    $BuildFile = Join-Path $Root "app\build.gradle.kts"
    Require (Test-Path -LiteralPath $BuildFile) "Run this script in the Android source root."
    $GradleText = Get-Content -LiteralPath $BuildFile -Raw
    Require ($GradleText.Contains('versionName = "1.1.9"')) "Android versionName is not 1.1.9"
    Require ($GradleText.Contains('versionCode = 10')) "Android versionCode is not 10"
    Require (Test-Path -LiteralPath "keystore\keystore.properties") "Original release signing keystore.properties is missing."
    Require (-not [string]::IsNullOrWhiteSpace($env:JARVIS_RELEASE_CERT_SHA256)) "Set the original installed RELEASE certificate SHA-256 in JARVIS_RELEASE_CERT_SHA256."
    $ExpectedCert = ($env:JARVIS_RELEASE_CERT_SHA256 -replace '[^a-fA-F0-9]', '').ToLowerInvariant()
    Require ($ExpectedCert -match '^[0-9a-f]{64}$') "Release certificate fingerprint must have 64 hex digits."
    Require (Test-Path -LiteralPath "gradlew.bat") "Gradle wrapper missing."
    Require (Test-Path -LiteralPath "scripts\create_update_manifest.py") "Update manifest generator missing."
    Require (Get-Command py -ErrorAction SilentlyContinue) "Python launcher 'py' is required."
    Require (Get-Command java -ErrorAction SilentlyContinue) "Java 17 is required."

    $Sdk = $env:ANDROID_HOME
    if ([string]::IsNullOrWhiteSpace($Sdk)) { $Sdk = $env:ANDROID_SDK_ROOT }
    if ([string]::IsNullOrWhiteSpace($Sdk)) { $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    Require (Test-Path -LiteralPath $Sdk) "Android SDK directory not found."
    $BuildToolsDir = Join-Path $Sdk "build-tools"
    $Tools = Get-ChildItem -LiteralPath $BuildToolsDir -Directory |
        Sort-Object { try { [version]$_.Name } catch { [version]"0.0" } } -Descending |
        Select-Object -First 1
    Require ($null -ne $Tools) "Android SDK build-tools are missing."
    $Aapt = Join-Path $Tools.FullName "aapt2.exe"
    $Signer = Join-Path $Tools.FullName "apksigner.bat"
    Require (Test-Path -LiteralPath $Aapt) "aapt2.exe is missing."
    Require (Test-Path -LiteralPath $Signer) "apksigner.bat is missing."

    Write-Host "Building RELEASE (not debug). Signing uses only the existing local keystore."
    RunExternal ".\gradlew.bat" @(":app:assembleRelease", "--no-daemon", "--console=plain")
    $Apk = Join-Path $Root "app\build\outputs\apk\release\app-release.apk"
    Require (Test-Path -LiteralPath $Apk) "Signed release APK not found."
    Require ((Get-Item -LiteralPath $Apk).Length -gt 1024) "Release APK is empty."
    $Verify = OutputExternal $Signer @("verify", "--verbose", "--print-certs", $Apk)
    $CertMatch = [regex]::Match($Verify, "certificate SHA-256 digest:\s*([a-fA-F0-9]{64})")
    Require ($CertMatch.Success) "Could not read APK signing certificate."
    $ActualCert = $CertMatch.Groups[1].Value.ToLowerInvariant()
    Require ($ActualCert -eq $ExpectedCert) "Certificate mismatch. This APK cannot update the original installed app."
    $Package = OutputExternal $Aapt @("dump", "badging", $Apk)
    $PackageMatch = [regex]::Match($Package, "package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'")
    Require ($PackageMatch.Success) "Could not read APK package/version."
    Require ($PackageMatch.Groups[1].Value -eq $PackageId) "APK package ID mismatch."
    Require ($PackageMatch.Groups[2].Value -eq $VersionCode.ToString()) "APK versionCode mismatch."
    Require ($PackageMatch.Groups[3].Value -eq $Version) "APK versionName mismatch."

    $OutDir = Join-Path $Root "verified-release"
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
    $ApkName = "JARVIS-BLUE-MOBILE-$Version-release.apk"
    $CopyApk = Join-Path $OutDir $ApkName
    Copy-Item -LiteralPath $Apk -Destination $CopyApk -Force
    $ApkUrl = "https://github.com/$PublicRepo/releases/download/v$Version/$ApkName"
    $Notes = "JARVIS BLUE MOBILE $Version — responsive layout and user-controlled verified APK updates."
    RunExternal "py" @("-3", "scripts\create_update_manifest.py", "--apk", $CopyApk, "--apk-url", $ApkUrl, "--package-id", $PackageId, "--version-code", "$VersionCode", "--version-name", $Version, "--notes", $Notes, "--output", (Join-Path $OutDir "update.json"))
    $Feed = Get-Content -LiteralPath (Join-Path $OutDir "update.json") -Raw | ConvertFrom-Json
    $ActualHash = (Get-FileHash -LiteralPath $CopyApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Require ($ActualHash -eq $Feed.sha256) "Manifest APK hash does not match."
    Write-Host "Signed APK verified! Version=$Version, versionCode=$VersionCode, SHA256=$ActualHash"

    if (-not $Publish) {
        Write-Host "DRY RUN ONLY. To publish after reviewing your APK: powershell -ExecutionPolicy Bypass -File .\RELEASE_PUBLIC_WINDOWS.ps1 -Publish"
        exit 0
    }

    Require (Get-Command gh -ErrorAction SilentlyContinue) "GitHub CLI gh is required to publish."
    RunExternal "gh" @("auth", "status")
    $PublicInfo = OutputExternal "gh" @("repo", "view", $PublicRepo, "--json", "visibility", "--jq", ".visibility")
    Require ($PublicInfo.Trim() -eq "PUBLIC") "Public download repository is not PUBLIC."
    $ExistingRelease = & gh release view "v$Version" -R $PublicRepo 2>$null
    Require ($LASTEXITCODE -ne 0) "Release tag v$Version already exists: refusing to overwrite."
    $NotesFile = Join-Path $OutDir "release-notes.txt"
    Set-Content -LiteralPath $NotesFile -Value $Notes -Encoding UTF8
    RunExternal "gh" @("release", "create", "v$Version", $CopyApk, "-R", $PublicRepo, "--draft", "--title", "JARVIS BLUE MOBILE v$Version", "--notes-file", $NotesFile)
    RunExternal "gh" @("release", "edit", "v$Version", "-R", $PublicRepo, "--draft=false")

    # Update the feed only after the release and signed APK are public.
    $ManifestPath = Join-Path $OutDir "update.json"
    $ManifestBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($ManifestPath))
    $ExistingSha = ""
    $Existing = & gh api "repos/$PublicRepo/contents/update.json" 2>$null
    if ($LASTEXITCODE -eq 0) {
        $ExistingSha = (($Existing | Out-String) | ConvertFrom-Json).sha
    }
    $ApiArgs = @("api", "repos/$PublicRepo/contents/update.json", "--method", "PUT", "-f", "message=Publish verified JARVIS BLUE update feed v$Version", "-f", "content=$ManifestBase64", "-f", "branch=main")
    if (-not [string]::IsNullOrWhiteSpace($ExistingSha)) { $ApiArgs += @("-f", "sha=$ExistingSha") }
    RunExternal "gh" $ApiArgs

    Write-Host "PUBLIC RELEASE: https://github.com/$PublicRepo/releases/tag/v$Version"
    Write-Host "UPDATE FEED: https://raw.githubusercontent.com/$PublicRepo/main/update.json"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
