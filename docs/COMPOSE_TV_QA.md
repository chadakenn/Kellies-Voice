# Compose TV Settings: draft PR #192 hardware QA

**Status:** in-device QA, **not a stable release**. The public `SETTINGS` menu remains the safe fallback. Debug-only `COMPOSE` is separate from production, and auto-pairing is planned independently in [#193](https://github.com/chadakenn/Kellies-Voice/issues/193).

## Confirmed on maintainer's onn 4K Pro (October 28, 2026)

- [x] Install the `0.16.14-test` package next to the stable release and launch COMPOSE.
- [x] D-pad left category/right settings navigation, OK and visible focus.
- [x] Select Neon Obsidian; selected skin persists across category switching.
- [x] Open Frigate URL editor and dismiss unsaved changes via remote Back without exiting Settings.
- [x] Preview existing Frigate dashboard with camera images, then Back returns to Compose Screensaver.
- [x] Complete manual pairing in HA, configure the separate **debug app** credentials, authenticate Assist, enable TV alerts, and confirm HA's TV alert receiver reports connected and app version.
- [x] Deliver a targeted announcement to the debug TV; the TV renders it, and HA reports `Popup created on TV`.
- [x] Reproduce previous bug: alert popup looks copper despite Compose selecting Neon Obsidian. **Code fix added afterward but not yet verified on TV**.

## Must still verify before release / merging behavior into production

- [ ] Install build containing `TvPopupPalette`, including Neon Obsidian action/forecast/camera styles. Confirm cyan-on-dark appearance after **reopening** any popup; old windows retain the previous skin.
- [ ] Open floating voice overlay with Neon Obsidian active: verify header/text/wave/remote focus match theme and TALK/STOP/CLOSE remain usable. This was added after the first QA build.
- [ ] Inspect camera popup snapshot and live video for media decoder/Back/focus/audio regressions across skins.
- [x] **Invalid camera input and Back safety (onn 4K Pro):** Owner entered `invalid_camera_test` in Compose → Frigate screensaver → Edit camera entities. Save rejected the invalid `camera.*` ID, and Back preserved the original saved camera list. Owner confirmed behavior as expected; not a valid-save test.
- [ ] Verify a **valid** camera entity list can be edited and saved with TV keyboard, then survives a screen re-open. Do not change a production Frigate URL for validation.
- [ ] Test TV announcement popup positioning/size, local preview and spoken announcement volume, if spoken alerts are intentionally enabled. Media/audio focus must remain unchanged.
- [ ] Verify weather preview settings and forecast popup style in every supported skin.
- [x] **Basic HA layout sync (physical onn 4K Pro):** Owner changed Compose **Overlay size Small → Medium** and returned to the app main screen. Read-only HA inspection immediately confirmed the paired debug TV's `select.kellie_s_voice_tv_new_overlay_size` = `Medium`, with generation **2 → 3**, while alert receiver stayed connected. This validates the authenticated deferred publish path for this single online setting, **not** offline retry or all controls.
- [x] **Restart persistence (owner-confirmed on onn 4K Pro):** After closing/restarting the configured debug app, **Medium remained selected** in the on-TV settings. A fresh Home Assistant check continued to show overlay size `Medium`, sync generation **3**, TV alert receiver **on** and app version `0.16.14-test`. This tests app restart, not a TV power cycle.
- [ ] Test matching HACS fifth theme option (Neon Obsidian), other layout controls, disconnect/offline behavior and retry guarantees; installed HACS still has only four style choices.
- [ ] Confirm each settings category and Android TV remote Back/OK/focus works with both columns scrolled, especially text editors with on-screen keyboard.
- [ ] Restart the debug app and TV; ensure skin, screensaver, HA login, receiver permissions and wake-word opt-in behavior persist, with no spontaneous mic activation.
- [ ] Regression-test stable signed app independently. Never overwrite it with a debug APK.

## Two independent update constraints

1. **APK:** GitHub PR CI's `Kellies-Voice-test-apk` is **disposable .debug** and is not suitable for ongoing tester updates: fresh GitHub runners do not retain its original signing key. The original QA attempt was blocked by an `app conflicts` error on the onn, and no existing APK or local credentials were removed. Instead the draft branch adds a permanent, visibly named **Kellie's Voice QA** package `com.example.tvvoiceprobe.qa`: its signed APK is produced only by the existing trusted `dev` signing workflow as `kellies-voice-qa-signed` / `app-qa.apk`. The QA build is non-debuggable, distinct from release and the old `.debug`, but has the Compose preview button. PR CI tests only **compilation**, never restores private keys. **No signed QA APK exists yet**; the new workflow/code must be promoted to the trusted `dev` branch and successfully built before any QA install. Once signed, later QA APKs from that same signing identity update in place, preserving the QA app's encrypted HA credentials, pairing ID and local settings. New QA app still needs **one initial** private HA login/pairing. The existing release and old debug packages remain untouched.
2. **HACS:** Android and HA are distinct installations. The current installed HA `Overlay style` select had only four options, though the new Android app supports five. PR #192 adds `Neon Obsidian` to HA `STYLES`, but this choice will appear only after the matching integration is installed and reloaded in a **separate controlled test**. Do not change HACS in production merely to test Android styling.

## Useful diagnostics (never include tokens or private camera URLs)

- On TV main debug screen record `Home Assistant voice` and `TV alerts` labels.
- In HA check the paired debug device's `TV alert receiver connected`, `TV app version (reported)`, and `TV last popup receipt` entities. A manually added device is not proof of authenticated TV connectivity.
- If a popup fails, report which skin, what action fired, whether Android overlay permission was granted, whether the TV received the message, and whether HA reported `Popup created on TV`.
- Never paste a long-lived access token, full pairing UUID, private Frigate URL, camera screenshot, or saved credentials into GitHub comments.
