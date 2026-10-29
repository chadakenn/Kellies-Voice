# Signed QA updates without a flash drive (draft)

Status: **experimental feature branch**, no QA OTA release has been published by this change. Production/stable and your existing .debug app are untouched.

## How it works
- Install the **separately signed** `app-qa.apk` (package `com.example.tvvoiceprobe.qa`) once, via approved local ADB sideload, TV browser download, or another normal Android install method. This is **not** `app-debug.apk`, and it does not upgrade a debug/production package in place.
- Later open **Kellie's Voice QA → SETTINGS → Updates & account → Check, download or install updates → Check for signed QA update**. This queries *public* published GitHub QA prereleases and offers the highest numeric `vX.Y.Z-qa.N` with exact `app-qa.apk` and `update.json` assets.
- Tap Download, then approve Android's system installer. The app checks HTTPS host, exact asset path, SHA-256, installed package and signature, and matching APK versionName/versionCode with anti-downgrade before installation.
- QA does not silently check, download, or install. The regular production app still checks stable releases and has its own credentials and settings.

## Maintainer-only signing and publishing
The existing `Build Kellie's Voice Signed APK` workflow has an explicit `publish_qa` checkbox. Publishing is permitted only when run manually on **dev**, not a PR branch, and when `publish_release` is false. It requires the normal private signing secrets; no key/PAT is embedded in the APK. Build verification runs before upload, and publication creates a GitHub **prerelease**, never a stable/latest release.

To publish a new QA update after code review and approval:
1. First merge tested source into `dev`; the workflow must be available on the chosen ref. Run the workflow manually on `dev` with `publish_qa=true`, `publish_release=false`, and **strictly increasing** `qa_build_number` and `qa_version_code` compared with any QA APK already installed. First candidate defaults to `qa.1` / code `55` for the current stable code `54`. Second candidate would be `qa.2` / code `56`, if the production base hasn't changed.
2. Workflow uses the same trusted release signing identity to build the non-debuggable QA package, validates wake-word native assets, manifest identity and version, then publishes `app-qa.apk` and matching SHA-256 `update.json` under `vX.Y.Z-qa.N`.
3. Never reuse a tag or lower the QA code. Keep any future production stable Android versionCode above the most recent installed dev build when sharing the same production package; QA is a *separate* package, so its independent version codes don't force stable upgrades.
4. Existing signed app data survives an in-place QA install only when Android package and certificate are unchanged. Changing from .debug to .qa is a **new installation** with separate Home Assistant setup.

The workflow needs a manual publication run to create a publicly accessible prerelease. A private GitHub Actions artifact is not fetchable by the updater without GitHub authentication, and no token is placed inside the app. Publishing a prerelease makes **the QA APK publicly downloadable**, although it never updates stable users.

## Hardware tests before calling this working
- Install the initial signed QA package (not PR debug); configure its independent HA connection.
- Publish a deliberately authorized **newer** signed QA prerelease on `dev`.
- Use the QA Update menu, check and download; verify version change with settings and HA login intact after install.
- Confirm missing prerelease, invalid checksum, wrong package, different signer, older version and untrusted URL are refused without installer launch.
- Verify stable signed app still receives only normal stable updates; no QA prompt.
- Check no microphone or camera starts as a result of update checking. Verify manual Android installer confirmation and restart stability.
