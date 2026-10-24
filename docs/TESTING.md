# Field testing and verified milestones

**v0.16.7 issue #54 timer focus regression test:** On the onn 4K Pro, start a 10-second Pizza timer, let it ring, and press D-pad OK **without moving focus**. The Dismiss button should activate and repeating chimes must stop. Repeat with two timers, Down/OK to dismiss only one, and Back to dismiss the remainder. Countdown must not steal focus over YouTube. The Android host unit tests passed, but real-TV behavior remains unverified until the owner tests it.


**v0.16.7 issue #79 live popup parity test:** On both Philips models and onn 4K Pro, call `show_live_camera` with `show_header: false`, `image_fit: fit`, `refresh_seconds: 2`, and `duration_seconds: 6`. Confirm headerless live video still becomes visible when a frame arrives in time, 4:3 placeholder is uncropped, placeholder updates without blink before HLS ready, all HTTP still fetches stop at first video frame, and popup closes at requested short duration. A 6-second view with 7-second cold HLS startup may show only snapshots; this is expected. Retest 30-second duration and audio silence over HDMI/Netflix. Verify scoped non-admin HA alert transport forwards only allowlisted options, old callers keep defaults, and no outgoing arbitrary URL is accepted. Pending physical validation.


**v0.16.6 issue #78 verification matrix (after v0.16.5 Philips report):** On Philips 55OLED856/12 and 65PUS8507/12 using matching Android/HACS v0.16.5, compare TV alert runtime and HA `TV alert receiver connected` after (a) turning TV off/on, (b) Home Assistant YAML reload/restart, (c) networking disruptions, and (d) pressing **Resume TV alerts** while the service holds a stale WebSocket object. Expected: dedicated alert receiver re-establishes the scoped subscription, diagnostic turns ON, new popups work without Android Force stop. Verify setup timeouts, HA ping/pong heartbeat timeout, bounded retry/backoff, and old socket callbacks not affecting the fresh socket. Confirm a denied token/HA permission stays actionable, not an endless reconnect; disabling TV alerts halts watchdog; no microphone/background audio starts. The Android host JUnit tests cannot simulate Philips network standby, so physical QA is mandatory before claiming resolved. Keep #78 open until the original Philips tester confirms the receiver re-subscribes without Force stop.


**Draft #67 compatibility test matrix:** Philips integrated Android TV HDMI1 and foreground Netflix/TiviMate; safe software live H.264 (prefer low-res substream), snapshot fallback when decoder unavailable, HDMI still present during and after popup, no TV audio change; repeat popup/close/timeout/error 4001 and verify cleanup. onn 4K Pro: test safe software live plus optional explicit hardware choice, verify original cameras remain moving and muted. Full device QA required; the code is not a public v0.16.4 fix.

**Draft issue #70 physical testing (requires #67 branch):** Philips 4:3 driveway snapshot with show_header=false: no title/caption and no missing top-of-frame car. image_fit=fit preserves whole frame, crop preserves old appearance. Test 2–30s refresh, repeated show_camera calls for same camera at 2s intervals without popup teardown/flicker, and changing camera/title/fit resets window correctly. Network failures/reconnects, hide_camera and timeouts stop refresh; only authenticated HA camera_proxy requests are permitted. No camera video-decoder usage and no D-pad/audio focus regression. Matching HACS+APK required, physical QA pending.

**Combined draft checks (#67–#71):** Verify the full test APK+HACS combination on real Philips and onn hardware. Snapshot header/fit/refresh options must survive non-admin scoped WebSocket sanitation. Check that live decoding does not blank HDMI, wrong pairing IDs stay unverified, and inputless TVs do not send silent PCM. CI alone does not prove functionality. See [community QA](COMMUNITY_QA.md).

**Community release testing (October 24, 2026):** Android/HACS **v0.16.7** includes the candidate fixes for #67–#71; physical hardware validation is still in progress. The prior **v0.16.4** was verified on the maintainer's onn 4K Pro: voice/TTS, live driveway and doorbell video, weather popup, timer countdown/chimes and Back dismissal, plus named timer voice start/stop with custom HA automations. **Unverified or known issues:** new timer Dismiss/OK fix requires physical verification, main-app-wide theme sync, native Assist generic timer routing, other TV hardware, follow-up/wake-word features and action buttons from draft PRs. For an accurate first-time setup, see **[Install and test Kellie's Voice v0.16.7](INSTALL.md)**. **The milestones below are historical and may refer to earlier versions; they are not the current release checklist.**

Updated historical milestone: **2026-10-20**. Device: maintainer's **onn 4K Pro / Google TV**, with Android builds through GitHub Actions.

Distinguish what has actually worked on hardware from compiled features. A green build is **not** proof of behavior on a TV.

## v0.13 Home Assistant disconnection recovery (pending TV test)

- [ ] With saved credentials, launch full-screen Kellie's Voice and verify Home Assistant changes to connected **without selecting Reconnect**
- [ ] Open ⭐ overlay and confirm auto-listen and speech playback still work
- [ ] Temporarily disable the TV's network or restart HA; verify the overlay/full-screen screen reports **automatic reconnection** and recovers after network returns, without manual Reconnect
- [ ] With a lost connection, press ⭐ or TALK once. If reconnection completes within 30 seconds, it may start listening; if it takes longer, the microphone must **not** turn on automatically later
- [ ] Interrupt the network during listening: confirm microphone streaming stops and UI doesn't remain stuck in the listening state
- [ ] Leave the app in the background, then reopen it; it should retry immediately instead of waiting for the existing retry countdown
- [ ] With an intentionally invalid token **in a safe, test-only setup**, verify the authentication error is shown and retrying stops; restore the proper token afterward
- [ ] Check that restarting HA, normal overlay CLOSE/BACK, and intentionally forgetting credentials never trigger zombie sockets or unexpected microphone use
- [ ] Keep original `com.example.tvvoiceprobe` and signed release key; install over working APK without wiping HA configuration

CI success confirms compilation/signing only. These checks require actual hardware to distinguish Android TV network sleep, multicast-DNS and HA restarts from app logic. A permanently running Home Assistant event receiver and 24/7 socket service are intentionally **not** part of v0.13.

## v0.12 overlay layout / copper settings acceptance (pending hardware)

Signed [build #108](https://github.com/chadakenn/Kellies-Voice/actions/runs/39598350715) passed compilation and APK signature verification. This **does not** yet establish TV compatibility.

- [ ] Install `kellies-voice-signed/app-release.apk` over the working TV version without uninstalling; existing credentials and ⭐ fallback remain
- [ ] Main SETTINGS opens a copper/charcoal D-pad menu; UP/DOWN/OK and Android BACK navigate without accidentally selecting the TALK button behind the dialog
- [ ] SETTINGS → Overlay appearance & position shows position, size, auto-hide, style, auto-listen and ⭐ fallback
- [ ] Default Glass placement and medium size look unchanged from the previous installed app
- [ ] Confirm all four corners, center and bottom center without clipping near the TV's overscan/safe area
- [ ] Confirm small/medium/large fit the screen and the TALK / STOP / CLOSE / SETTINGS buttons are still reachable
- [ ] Confirm auto-hide occurs **after spoken TTS completes** at 5, 7, 10 and 20 seconds, and stays visible for Never
- [ ] Verify unplugging Home Assistant or losing connectivity doesn't reset the stored position/size; verify reopening overlay applies changes
- [ ] Check overlays above YouTube and the Google TV home screen, and verify microphone service stops on CLOSE/BACK
- [ ] Verify source retains original Android package ID and signing key and updater still checks exact approved GitHub release paths

**Not implemented:** remote control of TV settings from Home Assistant, assistant announcement popups, Frigate snapshots/live feed, or weather cards. See [future design](REMOTE_CONFIGURATION_PLAN.md).

## Proven on the onn 4K Pro

| Area | Observation | Status |
| --- | --- | --- |
| Android TV APK launches | Original diagnostic screen appeared on TV | ✅ Tested |
| D-pad remote | After focus fix, UP/DOWN/OK operated the app | ✅ Tested |
| Enumerate audio inputs | Android reported four input devices; this alone doesn't reveal remote mic access | ✅ Tested |
| Built-in mic audio | `VOICE_RECOGNITION` source showed peaks up to 3,484 / 32,768 when speaking toward the box without pressing the remote mic button | ✅ Nonzero audio |
| Plain `MIC` audio | Peaks around 320 / 32,768, and ~303 with remote voice button held | ✅ Measured |
| Remote microphone button | Pressing it opens **Google Assistant**, not our app | ✅ Tested; not supported |
| HA WebSocket token auth | UI displayed **Connected and authenticated** for the user's HA server | ✅ Tested |
| STT pipeline | Captured and displayed user's utterance **“What time is it?”** correctly | ✅ Tested |
| Compact answer-first layout | Shows HOME ASSISTANT SAYS above YOU SAID, with TALK / STOP / SETTINGS | ✅ Verified in user photo |
| TTS playback through TV speakers | `tts_output.url` consumed; HA answered “5:07 AM” and the user confirmed spoken TV audio | ✅ End-to-end tested |
| Permanent APK signing | GitHub Actions run **#31** built, signature-verified and uploaded `app-release.apk` | ✅ CI-tested |
| Migration debug to release | User was preparing to uninstall original debug build and install signed release | ⏳ Awaiting TV test |

**Caution:** Increased audio peak numbers indicate received audio samples, not validated acoustics for every listening distance or guaranteed input-device identity. The box microphone is the probable source based on the separate test and working STT; Bluetooth remote mic access remains unproven.

## v0.4 experimental overlay follow-up

The permanently signed **[build #39](https://github.com/chadakenn/Kellies-Voice/actions/runs/39400677478)** completed compilation and APK signature verification. The following are **not yet hardware-verified**:

- [ ] Signed v0.4 installs *over* signed v0.3 while preserving encrypted HA credentials
- [x] Overlay permission is effective: Google TV displayed our floating panel above its launcher (exact permission-screen steps not documented)
- [x] Floating panel appeared in the upper-right **over Google TV Home**, connected and authenticated to HA; behavior over other apps still needs checking
- [ ] D-pad selects TALK / STOP / CLOSE; BACK dismisses panel and releases controls
- [ ] Microphone capture, HA transcript, reply and TV TTS work through the floating panel
- [ ] Closing the overlay stops the foreground-service notification and microphone activity
- [ ] The separate Kellie's Voice Overlay launcher starts overlay while another TV app is visible
- [ ] Optional onn ⭐ app shortcut can target the overlay launcher if firmware supports it
- [ ] Test a non-DRM video app first; DRM/Netflix overlays and audio focus remain unknown

See [step-by-step overlay tests](OVERLAY_TESTING.md). No remote-microphone button interception, boot-time auto-start or always-on wake word was introduced.

## Current MVP acceptance checklist

- [x] App opens and navigates using regular onn remote
- [x] Authenticated connection to Home Assistant
- [x] Built-in microphone sends speech to Assist and yields a readable transcript
- [x] Stable keystore-backed signed release build succeeds in CI
- [ ] One-time migration to signed `app-release.apk` installed on TV
- [x] Compact answer-first UI visible on TV; actual HA reply is readable
- [x] Home Assistant answer appears correctly in the **HOME ASSISTANT SAYS** area
- [x] TV speakers played HA TTS on the first end-to-end test (longer-term playback reliability still to test)
- [ ] Conversation can be repeated without restarting the app
- [ ] Second signed APK update installs **over** first release while preserving saved HA credentials
- [ ] Safe error messages when TV goes offline, HA is unavailable, or permissions are declined
- [ ] Actual audio-focus behavior while a streaming app is running

## Suggested next test sequence

1. Install first stable signed APK after the original debug build is removed.
2. Configure and connect to Home Assistant; verify auth and microphone permission.
3. **Completed:** “What time is it?” was recognized, “5:07 AM” appeared as the reply, and the user confirmed audio from the TV speakers.
4. **Next:** test repeated TTS, follow-up requests and whether any other TV app audio interferes.
5. Ask another short follow-up, then test **SETTINGS → Start new conversation**.
6. Later, after a second signed build is available, verify an over-the-top upgrade preserves the HA login. No uninstall.
7. After the foreground conversation is stable, begin opt-in ⭐ button launch and overlay tests.

## Known unimplemented areas

Floating over Netflix/Stremio/Nuvio/YouTube, programmable ⭐ shortcut, intercepting Google's microphone button, background voice service, wake words, HA `media_player` entity, automation announcements, Frigate/go2rtc camera PiP, and dashboard-on-demand are **not shipped**.

For each new bug, note build run number, app version, device firmware, exact status text, test steps and whether it reproduces after restarting. **Never include access tokens, private signing keys or raw voice recordings.**
