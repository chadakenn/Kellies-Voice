#!/usr/bin/env python3
"""Create a complete, *isolated* full-app debug beta kit.

No tokens, credentials, production signing keys or existing HA configuration.
Use only on the reviewed beta draft PR after tests pass.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
HA = ROOT / "dist/kellies_voice_v3qa_ha.zip"
GUIDE = ROOT / "docs/FULL_APP_BETA_TEST_GUIDE.md"
OWNER_GUIDE = ROOT / "docs/HA_V3_OWNER_APPROVED_LOGIN_BETA_QA.md"
OUT = ROOT / "dist/Kellies-Voice-Full-App-Beta-Kit.zip"

for name, path in (("debug Android APK", APK), ("isolated HA ZIP", HA),
                   ("test guide", GUIDE), ("owner-approved login guide", OWNER_GUIDE)):
    if not path.is_file() or path.stat().st_size < 100:
        raise SystemExit(f"Missing {name}; do not publish an incomplete beta kit")

with ZipFile(APK) as archive:
    if archive.testzip() is not None or "AndroidManifest.xml" not in archive.namelist():
        raise SystemExit("Android debug APK invalid")
with ZipFile(HA) as archive:
    names = set(archive.namelist())
    frontend = ("custom_components/kellies_voice_v3qa/"
                "frontend/kellies-voice-v3qa-owner-panel.js")
    if archive.testzip() is not None or frontend not in names:
        raise SystemExit("HA owner panel asset missing")
    if not names or any(not n.startswith(
            "custom_components/kellies_voice_v3qa/") for n in names):
        raise SystemExit("Refusing to package stable or unrelated HA integration")
    manifest = json.loads(archive.read(
        "custom_components/kellies_voice_v3qa/manifest.json"))
    if manifest.get("domain") != "kellies_voice_v3qa":
        raise SystemExit("Wrong HA integration domain")

items = (
    ("app-debug.apk", APK),
    ("kellies_voice_v3qa_ha.zip", HA),
    ("FULL_APP_BETA_TEST_GUIDE.md", GUIDE),
    ("OWNER_APPROVED_LOGIN_TEST.md", OWNER_GUIDE),
)
checksums = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {name}\n"
                    for name, p in items)

OUT.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(OUT, "w") as kit:
    for name, path in items:
        kit.write(path, arcname=name, compress_type=ZIP_STORED)
    kit.writestr("SHA256SUMS.txt", checksums, compress_type=ZIP_DEFLATED)

with ZipFile(OUT) as kit:
    if kit.testzip() is not None or set(kit.namelist()) != {
        name for name, _ in items} | {"SHA256SUMS.txt"}:
        raise SystemExit("Beta kit verification failed")

print(f"PASS: {OUT}, {OUT.stat().st_size} bytes; stable app untouched")
