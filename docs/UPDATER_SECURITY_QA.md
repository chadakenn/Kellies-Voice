# PR #184 updater security: on-TV acceptance checklist

**Status:** review-only draft against the signed v0.16.14 baseline. This document does **not** authorize publishing a release, installing an APK on the maintainer's TV, or merging the PR. It is for a controlled signed build **after** the test candidate is deliberately approved.

## Test safely on the existing stable TV

1. Record installed Android app **versionName and versionCode**, HACS version, Google TV/Android version, and current Stable/Development preference. Keep TV credentials and settings untouched.
2. On installed **stable v0.16.14**, open **SETTINGS → Updates & account → Update channel**. Confirm **Stable** is selected by default.
3. Choose **Check now**. If the latest public stable is still v0.16.14 (code 54), expect **up to date**, not a download or installer. This verifies the **existing installed updater**, not PR #184, until a PR-derived signed APK is actually installed.
4. Enable **Development** only if intentionally investigating published prereleases; **do not install** a development APK just to test detection. Return to Stable. Current APK versionCodes prevent an in-place downgrade.
5. Confirm that **TALK**, camera overlays, Home Assistant authentication, and Okay Nabu OFF/ON choice are unchanged. Do not capture a token, private IP, camera view or household audio in reports.

## Before any production-signature install test of PR #184

- Review all changed code and new tests against current `main`. CI must pass actual JNI-enabled Android APK/unit tests and Home Assistant tests.
- **Do not** install a PR-generated **debug APK** over the stable app. Its package name/signing identity are separate. A debug build can validate host tests and some negative paths, not the real signed upgrade.
- A positive end-to-end install requires a **newer, manually approved, correctly signed release candidate** with matching `update.json`, `app-release.apk`, SHA-256, versionCode and versionName. A random Actions artifact is not such a release. Never reuse published tag `v0.16.14`.
- The release workflow stays manual-only; never turn on automatic publishing merely to test this PR. Keep a backup and a rollback plan. Android won't downgrade in place to a lower versionCode.

## Candidate acceptance when a newer signed release is approved

| Scenario | Expected |
| --- | --- |
| Matching signed release, manifest version and SHA-256 | **Download verified**, then Android's **user-confirmed** installer; TV retains HA connection and settings |
| Wrong APK package ID, signature, hash, versionName or versionCode | Refuse before launching Android installer |
| Same / older versionCode than installed, including after switching from Development to Stable | No downgrade offered or installed |
| Close/reopen TV settings or Activity during check/download | No stale popup, progress, or callbacks to a destroyed Activity |
| Change Stable ↔ Development while a check/download is pending | Stale offer is cleared, no cross-channel install |
| Reopen TV after a download was cached but installation deferred | Only a still-safe, newer, correctly signed APK should be installable; inspect cache handling |
| Interrupted download / interrupted network | No partial or mismatched APK reaches installer |
| Real TV after update and reboot | HA/TALK, TV alerts, camera opening/closing, Okay Nabu saved OFF/ON behavior and in-app updater still work |

## Limits and explicit sign-off

- GitHub CI tests do not demonstrate that the actual Android package installer was reached or accepted an upgrade on the onn.
- **New in this draft:** a private `app-release.verified` receipt is written only after the APK passed hash, archive identity, version and signature checks. An Install click, including after Activity recreation, re-reads the receipt, hashes the whole APK again off the UI thread, verifies the currently selected channel, and checks package/signature/newer version before Android is asked to install. Changed/shortened APKs or absent receipts are rejected, with host regression tests. Physical Android installer acceptance is still pending.
- Record exact observed TV results and candidate version in PR #184 before marking it ready. Keep update security work separate from the optional signed-APK provenance attestation issue #185.
