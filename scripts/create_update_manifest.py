#!/usr/bin/env python3
"""Generate an in-app updater JSON feed from a locally built APK (never uploads keys)."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit


def main():
    p = argparse.ArgumentParser(description="Create JSON update feed for a trusted HTTPS APK")
    p.add_argument("--apk", required=True, type=Path)
    p.add_argument("--apk-url", required=True, help="Public HTTPS URL to the exact APK bytes")
    p.add_argument("--package-id", required=True, help="From aapt2 dump badging")
    p.add_argument("--version-code", required=True, type=int, help="Must be greater than installed code")
    p.add_argument("--version-name", required=True)
    p.add_argument("--notes", default="Bug fixes and improvements")
    p.add_argument("--output", type=Path, default=Path("update.json"))
    args = p.parse_args()
    if not args.apk.is_file() or args.apk.suffix.lower() != ".apk":
        p.error("--apk must identify an existing APK")
    u = urlsplit(args.apk_url)
    if u.scheme != "https" or not u.hostname or u.username or u.password or u.port not in (None, 443):
        p.error("--apk-url must be HTTPS without credentials or a custom port")
    if args.version_code < 1 or not args.version_name.strip():
        p.error("Version must be valid")
    digest = hashlib.sha256()
    with args.apk.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    data = {
        "packageId": args.package_id,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "apkUrl": args.apk_url,
        "sha256": digest.hexdigest(),
        "notes": args.notes[:2000],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Created {args.output}: SHA-256 {digest.hexdigest()}")
    print("IMPORTANT: Confirm APK package ID/version/signature using aapt2 + apksigner before publishing.")


if __name__ == "__main__":
    main()
