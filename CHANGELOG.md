# Changelog

## v0.16.11 (2026-10-25) — Owner-tested spoken TV alerts and follow-up

- Android **versionCode 39**, signed `0.16.11` release, matching HACS manifest `0.16.11`. Preserve existing package ID and signing key.
- Optional local-TV follow-up listening: 8-second second-question, mic privacy timeout and STOP verified on onn 4K Pro. OFF by default. PR #114 and #116, #43 completed for core acceptance.
- Optional spoken announcements: real HACS `speak` and local speech at 40% volume verified quieter on onn 4K Pro. Text-only notices remain silent. #110.
- Post-boot voice session readiness confirmed on onn 4K Pro (#44); alert receiver reconnect and camera display reliability may vary after standby (#39).
- 48 kHz microphone capture fallback (#112), manual pairing confirmation (#111), native Assist timer bridge OFF by default (#113 / #57), and opt-in development channel need additional hardware acceptance outside the tested cases.
- Still open: #39 voice camera+LLM prompt latency, #57 native timer QA, #69 pairing, #71 Philips microphone, #41 Fire TV, #92 dev-update channel tests. Wake-word, Frigate screensaver dashboard and actionable buttons remain experimental and are not included.
- [Detailed release notes](docs/releases/v0.16.11.md) and [new tester install guide](docs/INSTALL.md).

This is an experimental Android TV / Google TV app. Each entry distinguishes **shipped code** from **tested on the onn 4K Pro**.

## v0.16.9 (2026-10-24) — Room-aware Assist and HA restart recovery

- **#50:** When Home Assistant layout sync is enabled, Kellie's Voice learns the matching Home Assistant device-registry ID from the paired TV's control entities and includes that ID in the next Assist pipeline request. This is distinct from the TV pairing ID. Assign an Area to the TV in Home Assistant for room-aware requests; room targeting depends on the conversation agent. The new code has **not yet been confirmed with a physical room-targeting request**.
- **#78 / #86:** The HA integration distinguishes a paired TV whose control select has not loaded yet (`tv_not_ready`) from a genuinely unauthorized TV. Android retries only the temporary condition with 2–30 second capped backoff; missing pairing or read permissions remain denied. The owner reproduced the previous stuck rejection on an onn 4K Pro and confirmed manual recovery on v0.16.8; the new automatic fix is **awaiting physical HA restart regression testing**.
- **#58:** HACS README banner now uses an absolute URL for the existing SVG image.
- **#85:** Adds Wiki source pages and a Home Assistant YAML cookbook under `docs/wiki/`. Publishing to the separate GitHub Wiki remains a distinct step.
- **Not included:** Wake-word support, follow-up listening, Frigate screensaver dashboard, actionable TV buttons. Philips microphone issue **#71 remains open**.
- Signed APK `versionCode 26` / `versionName 0.16.9`; HACS integration manifest `0.16.9`. Install matching Android and HACS versions and restart Home Assistant.


## v0.14-reliability-hardening (2026-10-21) — Merge-tested stabilization

- Merged and reconciled [#9](https://github.com/chadakenn/Kellies-Voice/pull/9): checks the downloaded APK's package name and signing certificate before starting Android's installer, and removes the obsolete Julia-Voice release fallback.
- Merged [#4](https://github.com/chadakenn/Kellies-Voice/pull/4): records an automatic update-check timestamp only after a successful check, so offline errors can be retried on the next launch.
- Rebased and merged [#10](https://github.com/chadakenn/Kellies-Voice/pull/10): shared URL validation helpers, 11 host-side tests, and a repaired Android SDK setup in GitHub Actions. The tests now pass in CI.
- Rebased and merged [#2](https://github.com/chadakenn/Kellies-Voice/pull/2): typed Assist lifecycle callbacks control overlay dismissal instead of parsing status strings. Reply playback preparation cancels idle auto-hide and text-only replies use the saved timeout.
- Rebased and merged [#1](https://github.com/chadakenn/Kellies-Voice/pull/1): prevents a dropped/stale WebSocket from leaving a stuck voice request, and reports an Assist request acceptance result to the overlay. Preserves v0.13 bounded automatic reconnect and explicit-user auto-listening safeguards.
- Rebased and merged [#11](https://github.com/chadakenn/Kellies-Voice/pull/11): fixes outdated documentation and retained v0.4 test history without implying that v0.14 has been hardware-tested.
- Builds remain permanently signed with the same original `com.example.tvvoiceprobe` application ID. `versionCode` advances to **14**, `versionName` to `0.14-reliability-hardening`.
- **Not yet verified on physical TV:** upgrade, websocket reconnection after TV sleep, TTS completion timing, APK pre-install identity check. UI/audio/foreground-service PRs #3, #5, #6, #7 and #8 are still held for reconciliation and device testing. No GitHub Release was automatically published.

## v0.13-ha-auto-reconnect (2026-10-21) — Self-healing Home Assistant sessions

- The authenticated WebSocket now **automatically reconnects** after an unexpected close, network interruption or Home Assistant restart with delays of 2, 4, 8, 16 and up to 30 seconds. Existing 25-second WebSocket pings remain enabled to detect dead connections.
- When the full-screen activity starts with saved credentials it connects automatically; when the app returns to the foreground it reconnects immediately if necessary. The manual reconnect option remains available.
- Late callbacks from an older, closed WebSocket cannot falsely mark a newer authenticated connection offline. Retry timers are canceled when the user explicitly disconnects or closes the app.
- Authentication rejection **never loops retries**; the user must fix or replace the token. Unexpected loss of connectivity cancels any microphone recording immediately.
- If the overlay's user-triggered ⭐ or TALK request arrives while Home Assistant is offline, it attempts to reconnect. Automatic listening will start only if recovery occurs within **30 seconds** of the last explicit request; it won't activate the microphone unexpectedly much later.
- This is **not** an always-on service. Network recovery operates while the full-screen app or a user-opened overlay is alive. Android TV can stop background activities. A future passive Home Assistant announcement listener will be a separate, opt-in component without an always-open microphone.
- Android `versionCode` **13**, `versionName` `0.13-ha-auto-reconnect`, same original Android package ID and release signing key. **Hardware test required** before publishing a public release.

## v0.12-overlay-layout (2026-10-21) — Phase 1 TV layout and settings

- Added seven positions for the floating overlay: keep existing location, top-left, top-right, bottom-left, bottom-right, center, and bottom-center. Preserved the previous Glass vs. Classic default gravity unless a position is explicitly selected.
- Added three overlay width presets: small, medium (same as previous), and large, clamped to fit the TV screen.
- Added configurable auto-hide delay after speech: 5, 7 (original), 10, 20 seconds, or never. Existing `auto_hide`, auto-listen, skin and ⭐ shortcut preferences remain backward-compatible.
- Replaced plain TV alert-list settings and appearance dialogs with copper/charcoal D-pad-friendly menus. Microphone consent, token storage, foreground overlay permissions, and the signed updater are unchanged.
- Documented a future Home Assistant per-device configuration dashboard. This is a plan only; the app does not yet accept remotely pushed settings.
- Updated Android `versionCode` to **12**, `versionName` to `0.12-overlay-layout`. The app ID and release-signing key are unchanged.
- **CI signed build #108 passed, including APK signature verification; still requires field testing on the onn 4K Pro.** No public release was published.

## v0.11-release-url-fix (2026-10-21) — Completed repository rename

- Repository is now `chadakenn/Kellies-Voice`. The v0.10 updater anticipated a lowercase `kellies-voice` path, but GitHub Release asset URLs expose the canonical capitalized spelling. Fixed exact URL checks so the updater can download verified releases after renaming.
- Keep the previous `chadakenn/Julia-Voice` release URL only as a constrained migration fallback. Strict HTTPS host, asset filename, tag, version, SHA-256, size and APK signature verification remain in place.
- Bumped `versionCode` to **11** and `versionName` to `0.11-release-url-fix`; the Android package identifier and signing key are unchanged.
- TVs with v0.10 or earlier may need a **one-time manual installation** of the signed v0.11 APK over the installed app because older URL checks may reject the renamed path. Do **not** uninstall and erase saved settings.
- The v0.11 build is a **development artifact**, not a public Release. Test on the onn 4K Pro before a separately approved publication.

## v0.10-kellies-voice-brand (2026-10-21) — Warm branding

- Rebranded the visible Android TV app, overlay labels, status notifications, and signed APK workflow as **Kellie's Voice**.
- Replaced cool blue/violet UI colors with charcoal, copper, rose gold and warm ivory; created new vector icon, TV banner, animated waveform and startup splash.
- Changed Android `versionCode` to **10** without changing `applicationId` or the private signing key. Install the signed APK **over** the previous one.
- Prepared the OTA updater for the planned `chadakenn/kellies-voice` repo path with a safe fallback to the current `chadakenn/Julia-Voice` Release URL.
- GitHub repository is still named `Julia-Voice`, pending a separate owner rename after TVs are upgraded; no new public release was published.
- The portrait-led marketing renders are separate assets and are not repeated across the live TV overlay.

## v0.9-seamless-shortcut (2026-10-21) — Skip the full-screen flash

- For an **opted-in ⭐ main-launcher shortcut**, check that Home Assistant credentials and both microphone/overlay permissions are ready, then start the floating voice overlay from a tiny transparent Activity instead of creating the full-screen Julia Voice UI first.
- Use a no-preview launcher theme and disable background dimming for the transparent shortcut; start the microphone foreground service while the Activity is visible, then return the task to the background.
- If setup or permissions are incomplete, retain the regular full-screen Julia interface and its permission prompts. On launch exceptions, recover to the regular app rather than leaving a blank screen.
- Continue to open the full UI from the overlay's SETTINGS button; re-used transparent Activities respond to new ⭐ launcher intents.
- Bumped `versionCode` to **9** with the existing package ID and signing key. **Not yet hardware-verified**; OEM/Android launch animations may still appear momentarily even though our full-screen layout is skipped.

## v0.8-star-shortcut-fix (2026-10-20) — onn ⭐ picker fallback reliability

- The onn 4K Pro's native ⭐ shortcut picker lists **Julia Voice** but hides the package's separate Julia Voice Overlay launcher entry (confirmed by user photo).
- The **explicit opt-in** under **SETTINGS → Overlay style and voice settings → ⭐ picker fallback** remains **OFF by default**. Once enabled, a normal launcher open should request the floating overlay; the microphone and draw-over-other-apps permissions must still be granted.
- Fixed startup detection for launchers that omit `CATEGORY_LAUNCHER` or `CATEGORY_LEANBACK_LAUNCHER` while keeping `ACTION_MAIN`, and handled a new launch intent when Android reuses an existing `VoiceActivity`.
- Bumped Android `versionCode` to **8** and `versionName` to `0.8-star-shortcut-fix`. Application ID and release-signing key unchanged.
- **Not yet field-tested on the onn TV**. A successful signed build is not proof that Google's specific ⭐ launch route delivers a detectable intent. Public stable release requires owner approval.

## v0.7-julia-branding (2026-10-20) — Julia Voice identity

- **Repo rename:** GitHub now reports `chadakenn/Julia-Voice`. Fixed v0.7 release download verification for canonical capitalization (`Julia-Voice`) and documented the one-time manual upgrade needed by some installed v0.6 builds. No public v0.7 release has been published.

- Introduced the official **Julia Voice** development branding and the tagline **Your voice. Your home. Your AI.** The repository was renamed to `chadakenn/Julia-Voice` on 2026-10-20; the updater source was adjusted for the canonical release URLs.
- Completed a repository-wide brand sweep: README, installation/testing/security-adjacent guides, SVG screenshot previews, in-app dialogs, notifications, shortcut labels and diagnostics all use **Julia Voice**.
- GitHub Actions is now named **Build Julia Voice Signed APK**, with the new `julia-voice-signed` artifact used by both build and release publishing jobs. The Gradle root project is **JuliaVoice**.
- **[CI run #75](https://github.com/chadakenn/Julia-Voice/actions/runs/39423514042) passed**, including the new artifact. The Android package ID, signing key, and existing Android package signing remain unchanged; the release URL has moved with the renamed repo.
- Added a new [GitHub README hero](docs/branding/julia-voice-header.svg) inspired by approved neon-glass concept images.
- Added new Android **waveform launcher icon**, **Julia Voice TV banner**, and approximately one-second **startup artwork** rendered with Android primitives.
- Updated the launcher, overlay shortcut, foreground-service notification, floating panel, diagnostic heading and settings label to Julia Voice.
- Kept existing `applicationId` (`com.example.tvvoiceprobe`), private signing key, encrypted Home Assistant settings and existing updater logic. Increased Android `versionCode` to **7** and `versionName` to `0.7-julia-branding`.
- **[Signed CI run #69](https://github.com/chadakenn/Julia-Voice/actions/runs/39421241827) passed** compile and APK signature verification. **Not yet tested on TV**, and v0.7 is **not yet a public GitHub Release**. Only owner-approved manual release publishing offers it as an OTA update.

## v0.6-in-app-updates (2026-10-20) — TV-native signed updater

- Added native Android TV **Check for app updates** and **Install downloaded update** menu actions, plus a roughly daily check when the full-screen app opens.
- Fetches **public stable GitHub Releases**; a newer `versionCode` prompts before downloading.
- Downloads APK directly over approved GitHub HTTPS hosts into app-private cache, verifies advertised size and **SHA-256 checksum**, and uses a scoped AndroidX FileProvider URI.
- Opens Android's standard user-approved APK installer; requests **Install unknown apps** permission if required. No root, silent background installation, HA-token upload or sideloading from arbitrary hosts.
- Added owner-controlled GitHub Actions `publish_release` checkbox. If selected in a manual run, signs and verifies the APK, then publishes a GitHub Release containing `app-release.apk` and `update.json`. Ordinary pushes do **not** publish releases.
- Increased Android `versionCode` to **6**, `versionName` to `0.6-in-app-updates`. Existing keystore and application ID are unchanged.
- **[Signed build #58](https://github.com/chadakenn/Julia-Voice/actions/runs/39411724076) passed** build and APK signature verification. **Public v0.6 GitHub Release is published. The in-app installation path is not yet hardware-verified.**
- [First-install and release publishing instructions](docs/UPDATES.md).

## v0.5-glass-voice (2026-10-20) — Glass UI and one-press voice prototype

- ✅ Added original animated cyan/purple waveform and wide translucent dark Glass skin, plus optional compact Classic styling.
- ✅ Added on-device preferences for Glass/Classic, auto-listen (ON by default) and auto-hide after speech (ON by default).
- ✅ Overlay launches a speech session **after Home Assistant authenticates**, eliminating a second TALK press. Added optional local voice-pause detection and 23-second recording safety timeout; manual STOP still works.
- ✅ Added optional main-app launcher fallback for the onn ⭐ app picker; overlay SETTINGS button keeps the configuration screen reachable.
- ✅ Incremented signed Android `versionCode` from 4 to 5; built with same permanent signing key. **[GitHub Actions build #50](https://github.com/chadakenn/Julia-Voice/actions/runs/39409512785) passed all signing and artifact steps**.
- ⚠️ **Awaiting actual onn box testing:** auto-listen, waveform, auto-stop with background video, auto-hide, ⭐ shortcut mapping and preserving credentials during signed upgrade. Previous v0.4 already displayed the overlay on Google TV Home. See [v0.5 testing](docs/V0_5_TESTING.md).

## v0.4-overlay-prototype (2026-10-20) — Opt-in floating Julia Voice panel

- Added an **experimental small, translucent floating voice panel** with D-pad TALK / STOP / CLOSE, recognized speech, HA text answer and speech output.
- Implemented `VoiceOverlayService` as an **explicitly user-started, microphone-typed Android foreground service**. It is not an always-on microphone; recording starts only during TALK, and stopping the overlay closes the service.
- Added consent flow for **Display over other apps** and microphone permission. No overlay is started without permission.
- Added a separate launcher entry **Julia Voice Overlay**, which may be selectable as the onn ⭐ programmable remote shortcut **if the TV firmware allows app launch selection**.
- Version upgraded to `0.4-overlay-prototype` / Android `versionCode 4`. The package ID and **permanent release signing key** are unchanged, so signed-v0.3 users should be able to **update in place** without losing tokens.
- Signed GitHub Actions **[build #39](https://github.com/chadakenn/Julia-Voice/actions/runs/39400677478)** passed compilation, private key restoration and APK signature verification, and uploaded the signed APK artifact.
- **Hardware testing still required:** overlay permission availability on onn Google TV, floating window display over launcher/streaming apps, D-pad focus and TTS during a video, shortcut remapping, task return behavior and in-place credential retention. See [overlay test instructions](docs/OVERLAY_TESTING.md).

## 2026-10-20 — Full voice conversation confirmed; GPLv3 selected

- ✅ **Real TV end-to-end success:** the onn 4K Pro understood “What time is it?”, displayed Home Assistant's reply **“5:07 AM”**, and **played TTS through the TV speakers**.
- ✅ The compact **TALK / STOP / SETTINGS** screen displays HA's answer and the recognized question without cropping the reply.
- ✅ Adopted **GNU GPL version 3, GPL-3.0-only**. Full license at [LICENSE](LICENSE); dependencies and research references retain their own licenses.
- ⏳ Longer-term repeated conversations, foreground audio behavior and the in-place upgrade of a second permanently signed APK remain to be verified.


## v0.3-assist-alpha (2026-10-20) — Signed release

- ✅ Added **persistent private release signing** using encrypted GitHub Actions secrets `TV_SIGNING_KEYSTORE_B64` and `TV_SIGNING_PASSWORD`. First successful signed build: [#31](https://github.com/chadakenn/Julia-Voice/actions/runs/39397223494).
- ✅ Changed downloadable artifact from earlier debug ZIP to `julia-voice-signed` containing `app-release.apk`. Signature verification runs before publishing.
- ✅ Increased Android `versionCode` to 3 and set `versionName` to `0.3-assist-alpha`.
- ✅ Replaced the seven oversized action buttons with a **TALK / STOP / SETTINGS** layout. **The new layout subsequently passed an on-device test**, see the milestone above.
- ✅ Corrected parsing of the Assist TTS event URL from `tts_output.url`. **TV-speaker playback subsequently passed the real-device test.**
- ✅ Added one-time signing migration instructions and permanently ignored private keystores / local recordings.
- ✅ Documentation-only updates no longer automatically start APK builds.

**Migration:** Original debug-signed Android apps cannot be upgraded in place to this signed version. Uninstall the debug app **once** after obtaining a successful signed APK, re-enter the saved HA token, and thereafter install newer releases over the existing signed app. Preserve the signing-key backup privately.

## v0.2-assist-alpha (2026-10-20) — Assist conversation prototype

- Implemented HA `/api/websocket` token authentication, `assist_pipeline/run`, 16 kHz PCM capture and binary framing, answer/transcript display and initial Android `MediaPlayer` TTS.
- Android Keystore-encrypted local connection settings; settings dialog and microphone permission handling.
- **Verified on actual onn 4K Pro:** successfully authenticated and transcribed “What time is it?” through HA Assist.
- Initial app UI had too many buttons and pushed the answer below the screen. TTS audio outcome was not yet confirmed.

## v0.1-probe (2026-10-19 through 2026-10-20) — Hardware diagnostics

- Built and installed diagnostic APK using Send Files to TV.
- Fixed Android TV D-pad focus/navigation; verified arrow keys and OK worked on the real device.
- Enumerated four Android audio inputs. Nonzero built-in box mic levels were recorded through `MIC` and `VOICE_RECOGNITION`.
- Verified Google Assistant captures the physical voice button; third-party Bluetooth remote mic capture remains unsupported/unproven.
- **Known problem:** per-run GitHub debug signing keys caused Android package-update conflicts. Replaced by stable signed releases in v0.3.

See [Field testing](docs/TESTING.md) for precise verification status, and [project vision](docs/PROJECT_VISION.md) for features not yet built.
