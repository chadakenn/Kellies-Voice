# Compose TV Settings: draft PR #192 hardware QA

**Status:** in-device QA, **not a stable release**. Production `SETTINGS` remains the safe legacy hub. Updated development debug and signed QA builds have **one `SETTINGS` button opening Compose** (with legacy recovery routes available). Older `0.16.14-test` debug builds showed both `SETTINGS` and `COMPOSE`; those historical test notes refer to that older APK. Auto-pairing is planned independently in [#193](https://github.com/chadakenn/Kellies-Voice/issues/193).

## Confirmed on maintainer's onn 4K Pro (October 28, 2026)

- [x] Install the `0.16.14-test` package next to the stable release and launch COMPOSE.
- [x] D-pad left category/right settings navigation, OK and visible focus.
- [x] Select Neon Obsidian; selected skin persists across category switching.
- [x] Open Frigate URL editor and dismiss unsaved changes via remote Back without exiting Settings.
- [x] Preview existing Frigate dashboard with camera images, then Back returns to Compose Screensaver.
- [x] Complete manual pairing in HA, configure the separate **debug app** credentials, authenticate Assist, enable TV alerts, and confirm HA's TV alert receiver reports connected and app version.
- [x] Deliver a targeted announcement to the debug TV; the TV renders it, and HA reports `Popup created on TV`.
- [x] Reproduce previous bug: alert popup looks copper despite Compose selecting Neon Obsidian. **Code fix added afterward but not yet verified on TV**.

## Automated ADB/MCP QA report (October 28, 2026)

[Full privacy-safe report](qa/PR-192-onn-TV-ADB-2026-10-28.md) from the owner's connected real-TV testing agent. The test used the **existing** configured `0.16.14-test` debug app; the exact installed source SHA is unknown.

- [x] D-pad toolbar/Back and normal category/pane navigation **after** focus recovers; theme appearance and supported Aurora/Night theme persistence; local announcement preview; safe URL-editor Back; alert-position round-trip through authenticated HA
- [ ] **Initial-focus bug found:** immediately after entering Compose, Down sometimes did not navigate. ADB hierarchy initially focused the instructional footer. Source fix now explicitly focuses the Home Assistant category and removes footer click-focus; **must retest on updated APK**
- [ ] **Stale HA style error found:** style rejection remained visible after later successful theme/position writes. Source fix now clears outdated feedback upon a later successful write and guards out-of-order acknowledgments; **must retest on updated APK**
- [ ] **Neon/HACS compatibility:** selecting Neon was rejected by the *installed four-style* HACS integration. This is not proof of a broken fifth-style implementation; update the HA integration separately under controlled testing
- [ ] **Camera playback safety:** automated agent observed Hardware (advanced) already selected. It did **not** enable/change that mode or launch camera playback. Owner approval is needed before hardware-decoder playback experiments

## October 29: signed QA APK and single-Settings transition (source changes pending next build)

The owner installed the **persistently signed** QA APK `com.example.tvvoiceprobe.qa` (`0.16.14-qa`, versionCode 54); the dedicated [manual signing workflow](https://github.com/chadakenn/Kellies-Voice/actions/runs/40786436228) verified signature, package and JNI/model contents. The actual APK's exact commit was not attested by the device report.

[Latest direct onn ADB results](https://github.com/chadakenn/Kellies-Voice/pull/192#issuecomment-6492488935):
- [x] Three clean launches: initial OK→Down advanced to Voice & microphone; all six categories, Back, supported theme persistence and local Neon-styled announcement sample passed
- [x] The old HA rejection notice cleared after a later successful sync; this test involved supported styles, not an installed HACS Neon option
- [ ] **Post-editor focus failure:** after an unsaved URL/keyboard edit and re-entry, selected Home Assistant appeared highlighted but actual focus was missing; subsequent OK→Down initially selected Home Assistant instead of advancing. This is **not fixed on the installed build**
- [ ] **Aurora edit field contrast:** entered URL text appeared hard to read. Formal contrast testing was not performed

**Changes committed after that test (not yet device-verified):**
- QA main toolbar now exposes exactly **one SETTINGS** button and opens Compose. Original Java sections are accessible from **specific advanced/recovery entries inside Compose** so HA connection/recovery, microphone diagnostics, TV-alert diagnostics, screensaver and verified update/install/Forget remain reachable. The original stable release is not changed and keeps legacy SETTINGS; ordinary debug keeps its original controls while the QA design is validated.
- Android window-focus epoch + editor state + LazyColumn scroll ensure the actual selected category is focusable after keyboard dismissal or Activity re-entry; selected/not-focused is no longer shown with the same strong highlight.
- Both editable URL and camera fields use readable theme text/cursor/outline/container colors. Overlay auto-listen and launcher shortcut fallback controls were added to Compose.
- [ ] Install a **newer signed QA APK** and retest the post-editor focus, Save/Cancel/Back with on-screen keyboard, selected-vs-focused appearance, color contrast, and advanced/recovery routes before declaring a single Settings UI done.
- [ ] Confirm update check/install and Forget confirmation still require deliberate owner actions; this is not yet fully native Compose parity.

## Must still verify before release / merging behavior into production

- [ ] Install build containing `TvPopupPalette`, including Neon Obsidian action/forecast/camera styles. Confirm cyan-on-dark appearance after **reopening** any popup; old windows retain the previous skin.
- [ ] Open floating voice overlay with Neon Obsidian active: verify header/text/wave/remote focus match theme and TALK/STOP/CLOSE remain usable. This was added after the first QA build.
- [ ] Inspect camera popup snapshot and live video for media decoder/Back/focus/audio regressions across skins.
- [x] **Hardware decoder confirmation safety (onn 4K Pro):** Owner selected **COMPOSE → Alerts & cameras → Hardware decoder**, confirmed a warning appeared, chose **Cancel**, and verified the previously selected camera mode remained unchanged. This validates the cancel/guard path only; actual hardware decoding was not enabled or tested.
- [x] **Invalid camera input and Back safety (onn 4K Pro):** Owner entered `invalid_camera_test` in Compose → Frigate screensaver → Edit camera entities. Save rejected the invalid `camera.*` ID, and Back preserved the original saved camera list. Owner confirmed behavior as expected; not a valid-save test.
- [x] **Valid camera save and reopen (owner-confirmed onn 4K Pro):** In Compose → Frigate screensaver → Edit camera entities, owner kept existing valid `camera.*` entities, selected **Save cameras**, reopened the editor and confirmed those entries remained saved. No production Frigate URL changed. This tests normal Save persistence across editor reopen, not full TV reboot.
- [x] **Compose local alert preview (real onn 4K Pro):** Owner selected **COMPOSE → Alerts & cameras → Preview announcement popup** and confirmed the popup visibly appeared. This tests the on-TV preview control and overlay permission path, **not** HA delivery (validated separately) or the newer theme palette.
- [x] **Physical alert placement and HA sync (onn 4K Pro):** Owner changed Compose → Alerts & cameras → TV alert popup position from **Top right** to **Top left**, invoked **Preview announcement popup**, and confirmed the actual TV popup appeared in the **top-left corner**. HA's select was still Top right while Compose was open, then after owner returned to the main screen HA showed `Top left` with sync generation **1 → 2**; receiver remained connected.
- [x] **Popup size + authenticated HA sync (onn 4K Pro):** Owner changed Compose → Alerts & cameras → TV alert popup size to **Large**, opened **Preview announcement popup**, and confirmed the rendered popup became visibly larger. HA initially still showed `Small` (generation 2) while Compose was open. After owner returned to main, HA reported **Large**, sync generation **3**, alert position remained **Top left**, and TV receiver stayed connected. The online deferred publish path is verified for both alert position and size.
- [x] **Spoken announcement playback (onn 4K Pro):** Owner enabled Spoken announcements in Compose → Alerts & cameras, selected **40%**, pressed **Test spoken announcement**, and confirmed Kellie's Voice spoke through the TV speakers. This confirms the speech test path works with that configured setting, **not** calibrated output loudness or unaffected media/audio focus.
- [ ] Verify spoken announcement volume changes audibly between e.g. 40% and 80%, and alert speech leaves TV media volume/audio focus unchanged.
- [ ] Verify weather preview settings and forecast popup style in every supported skin.
- [x] **Basic HA layout sync (physical onn 4K Pro):** Owner changed Compose **Overlay size Small → Medium** and returned to the app main screen. Read-only HA inspection immediately confirmed the paired debug TV's `select.kellie_s_voice_tv_new_overlay_size` = `Medium`, with generation **2 → 3**, while alert receiver stayed connected. This validates the authenticated deferred publish path for this single online setting, **not** offline retry or all controls.
- [x] **Restart persistence (owner-confirmed on onn 4K Pro):** After closing/restarting the configured debug app, **Medium remained selected** in the on-TV settings. A fresh Home Assistant check continued to show overlay size `Medium`, sync generation **3**, TV alert receiver **on** and app version `0.16.14-test`. This tests app restart, not a TV power cycle.
- [ ] Test matching HACS fifth theme option (Neon Obsidian), other layout controls, disconnect/offline behavior and retry guarantees; installed HACS still has only four style choices.
- [ ] Confirm each settings category and Android TV remote Back/OK/focus works with both columns scrolled, especially text editors with on-screen keyboard.
- [ ] Restart the debug app and TV; ensure skin, screensaver, HA login, receiver permissions and wake-word opt-in behavior persist, with no spontaneous mic activation.
- [ ] Regression-test stable signed app independently. Never overwrite it with a debug APK.

## Two independent update constraints

1. **APK:** GitHub PR CI's `Kellies-Voice-test-apk` uses disposable `.debug` signing and is **not** suitable for tester updates: a new runner certificate already caused an `app conflicts` error. The branch defines a permanent, non-debuggable, visibly named **Kellie's Voice QA** package (`com.example.tvvoiceprobe.qa`) with COMPOSE available. The existing [manual signed APK workflow](https://github.com/chadakenn/Kellies-Voice/actions/workflows/build-apk.yml), run **on branch `feature/compose-tv-settings`** with **Publish release = OFF**, now builds and verifies `kellies-voice-qa-signed` / `app-qa.apk` using the same permanent private signing identity as the public app, but with a different package ID. GitHub PR CI only compiles QA without signing secrets. **Do not claim a QA APK exists before a successful manual signed run.** Later signed QA APKs keep their own encrypted HA login, pairing ID and settings on update if the package ID/certificate stay unchanged and Android version code does not decrease. The new QA package still needs **one initial** Home Assistant login/pairing. Keep existing production and `.debug` apps untouched; no public release is published by this manual run.
2. **HACS:** Android and HA are distinct installations. The current installed HA `Overlay style` select had only four options, though the new Android app supports five. PR #192 adds `Neon Obsidian` to HA `STYLES`, but this choice will appear only after the matching integration is installed and reloaded in a **separate controlled test**. Do not change HACS in production merely to test Android styling.

## Useful diagnostics (never include tokens or private camera URLs)

- On TV main debug screen record `Home Assistant voice` and `TV alerts` labels.
- In HA check the paired debug device's `TV alert receiver connected`, `TV app version (reported)`, and `TV last popup receipt` entities. A manually added device is not proof of authenticated TV connectivity.
- If a popup fails, report which skin, what action fired, whether Android overlay permission was granted, whether the TV received the message, and whether HA reported `Popup created on TV`.
- Never paste a long-lived access token, full pairing UUID, private Frigate URL, camera screenshot, or saved credentials into GitHub comments.
