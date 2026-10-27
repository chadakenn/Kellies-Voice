# v0.16.5 community test checklist (issues #67–#71)

**Public early test release.** The user-approved community update is published
through the normal GitHub Releases channel so installed televisions can use
**Settings → Check for app updates**. Its APK retains the release signing key,
uses versionCode 22 (from v0.16.4's code 21), and has versionName 0.16.5.

### Install Android + matching HACS

1. In the existing TV app, open **Settings → Check for app updates**.
   The signed GitHub Release must contain both `app-release.apk` and
   `update.json` for the verified in-app update to work.
   Approve the Android installer without uninstalling the previous app.
2. In **HACS → Kellie's Voice**, update the integration to **0.16.5**
   and restart Home Assistant. The TV's **non-admin alert subscription**
   and **new snapshot options** require the corresponding HACS code.
   If HACS hasn't indexed the release yet, use the manual installation
   steps in [INSTALL.md](INSTALL.md). Always back up the config first.
3. Home Assistant and Android may need a reconnect; on the TV check
   that **TV announcements & cameras** are enabled.

### Report the following device tests

- #67: Philips/Google TV HDMI video stays visible while camera popups open,
  software H.264 live mode works if supported, snapshot fallback is honest.
- #68: A dedicated non-admin HA token with read permission for the TV's
  alert position entity can receive announcements, cameras, forecast and timer.
- #69: Configured mistyped pairing IDs stay OFF/never in
  **TV alert receiver connected**; correct connected receivers report ON.
  A successful HA service event isn't yet proof that a popup rendered.
- #70: Headerless snapshot (`show_header:false`), full-frame fit
  (`image_fit:fit`) and in-place refreshing (`refresh_seconds:3`).
- #71: No accessible Philips microphone should show a useful error
  instead of sending silence. Test a compatible USB microphone if available.
- onn 4K Pro regression: existing TALK/STOP, STT/TTS, timers, camera
  snapshots, live driveway/doorbell, forecast and D-pad navigation.

Use [the issue tracker](https://github.com/chadakenn/Kellies-Voice/issues)
to report device model, firmware, APK/HACS versions and redacted errors.
Do not post Home Assistant tokens, signed camera URLs or private recordings.

### Earlier branch previews

The old `test/community-67-71-qa1` signed APK is an **archived test build**,
not the in-app release. The standard updater only considers stable releases
with a strictly higher versionCode. Don't install a `.debug` APK over
the release-signed package.
