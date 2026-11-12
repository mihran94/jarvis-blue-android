# Play Protect investigation: diagnostic build

The Samsung phone blocked the existing debug APK with the message:
"App blocked to protect your device. This app can request access to sensitive data."

This warning does not identify a specific permission or prove the app is malware.
The existing debug APK is signed by the Android debug key, not a trusted
production release key.

## Diagnostic variant

Build with:

```bash
./gradlew :app:assembleDiagnostic
```

Expected output: `app/build/outputs/apk/diagnostic/app-diagnostic.apk`.

This separate variant removes from the *merged manifest* the Accessibility
Service, Notification Listener, boot receiver, overlay and exact-alarm
permissions. The ordinary debug and release variants are unchanged.

This is an investigative least-privilege configuration, not a bypass for
Google Play Protect. Some JARVIS features are unavailable, and code that
assumes these components exist may need additional guards. Do not distribute
or recommend installation until the diagnostic variant compiles, the merged
manifest and signing are checked, and a security review is complete.

## Verify

1. Build and run tests on CI. Check the exact artifact and SHA-256.
2. Inspect `app/build/intermediates/merged_manifests/diagnostic/` for the
   final manifest (the AGP directory layout may vary).
3. Confirm the removed permissions/services are absent, including any added
   by dependency manifest merging.
4. Check that no startup code crashes when these services are absent.
5. Scan dependencies and sensitive data flows; check whether debug signing
   and APK provenance are contributing factors.
6. If a device test is appropriate, keep Play Protect enabled and respect
   its block. Record the exact warning and Play Protect version.
7. For distribution, use a controlled release-signing process and follow
   official Android/Google Play Protect guidance.

A successful build alone is not evidence of Play Protect clearance.
