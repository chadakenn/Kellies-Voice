## v0.16.12-dev.8 opt-in HA camera-grid HLS (build 47, dev only)

- [ ] Install Android build 47 and matching HACS dev.8. Keep HA dropdown selections; clear Frigate URL and confirm screensaver ON, HA layout ON.
- [ ] Default Snapshots (reliable) stays unchanged, including after an APK upgrade.
- [ ] Choose Live (one camera) in HA. First tile must show moving, muted HLS video; other three remain five-second JPEG snapshots. Verify on manual Preview and automatic Google Ambient activation.
- [ ] On a missing HLS stream or software decoder error, snapshot fallback remains visible with a status. The previously reported HDMI blank decoder failure (#67) must not recur.
- [ ] TV live-camera decoder mode Snapshots only overrides HLS settings. Safe software mode may have no video codec on some devices; show snapshots rather than silently using a hardware decoder.
- [ ] Try Live (two cameras) after one stream succeeds, monitor five minutes of playback and Close. Test expired auth, HA restarts, missing camera, network drops and a stalled stream (18-second fallback).
- [ ] Opt in to Live (four cameras, advanced) only after stable one/two playback. Stop if overheating, HDMI loss, decoder errors or TV stutter. Use Frigate Live webpage for Frigate's own efficient smart streams if necessary.
- [ ] Switching from grid to web URL releases HLS players. Verify muted audio, same-host HLS URL validation, close/back, and no microphone activation.
- [ ] Stable main must not receive this development feature until real TV QA.

## v0.16.12-dev.7 camera-grid HA sync diagnostics (build 46, dev only)

- [ ] Reproduce owner report: configure four HA cameras, clear Frigate web URL in HA, and let the onn Google TV idle. Record current TV diagnostics for **Home Assistant layout**, **Source on TV**, **Cameras saved on TV**, **HA settings link**, and **Last HA source** without disclosing camera IDs or tokens in public logs.
- [ ] If local count is 0 but HA controls show 4, enable TV Home Assistant layout and use **Resync HA cameras**. Verify camera count becomes 4 and the overlay shows the 2×2 grid. Validate HA switching URL to blank is honored and live website does not remain cached.
- [ ] During an intentionally delayed HA sync, verify the enabled HA-managed watcher shows an explanatory placeholder rather than silently dropping the overlay. It must refresh to camera grid on HA state arrival without a TV app restart.
- [ ] Disable the Frigate screensaver. The fallback placeholder **must not** appear over normal TV playback. Verify **Display over other apps** permission is still honored and remote doesn't get trapped by empty grid.
- [ ] Test one HA camera only, invalid/offline camera (tile may stay blank, but overlay and close button must exist), empty configuration, reconnection after HA reboot, OFF/ON, and Google dream-start/dream-stop.
- [ ] Restore Frigate web URL and confirm live multi-camera page, remote cursor, Close, and existing TV alerts/voice behavior remain unchanged.

**Release gate:** Real device verification is necessary. Clear docs that a URL-free grid is JPEG snapshots, not live video, until issue #130 is implemented. Do not claim root cause or real-world success from diagnostics alone.

## v0.16.12-dev.6 HACS auto-camera dropdowns (build 45; dev only)

- [ ] Install HA HACS dev.6 and restart HA. In Kellie's Voice device Controls, confirm four **Frigate snapshot camera** selects appear with friendly names and HA camera IDs from the existing HA camera state registry.
- [ ] Select camera 1 then camera 2. Confirm the existing **Frigate HA camera entities (manual override)** text shows the matching `camera.*` comma list; repeat for 3 and 4. Reboot HA and confirm all selections restore.
- [ ] Select None for camera 1 or camera 2: remaining cameras shift forward. The same camera can't be selected twice. Picking camera 4 first should report "Choose earlier camera slots first."
- [ ] Manually edit the legacy text field (including clearing it), and confirm all selects reflect the change without accidentally starting a microphone or showing a popup.
- [ ] Add/rename/remove a Home Assistant camera entity, verify dropdown choices update, and keep an unavailable selected camera visible long enough to clear. No external camera-scanning credentials are used.
- [ ] With the **Frigate web URL** filled, confirm snapshot selectors have no effect on the Frigate Live webpage. Clear the URL to switch to the 1–4 HA snapshot grid. Restore URL afterward if the owner wants Live mode.
- [ ] Confirm two paired TVs keep independent selections. Verify existing overlay, voice, event popups and automatic Google TV Frigate overlay remain unchanged. Android build 44 can receive the identical `screensaver_cameras` wire format.

**Do not promote this development-only feature to main before owner UI and hardware acceptance.**

## v0.16.12-dev.5 HA settings + Frigate remote navigation (build 44; dev only)

- [ ] Install matching dev.5 HACS integration, restart Home Assistant and signed Android build 44. The TV's device page exposes Frigate screensaver switch, URL, cameras, size, layout, and fit preset. Check that the ordinary TV controls and existing announcements remain unchanged.
- [ ] With **Home Assistant layout ON** on the TV, change fit, size, and page layout in HA while normal Google Ambient Mode runs. Dashboard should update without opening a voice session or activating the microphone.
- [ ] Toggle Frigate OFF then ON from HA while the TV screen is idle. The HA settings sync service must remain running after OFF, and must honor the later ON. Test HA reconnect and app update/TV reboot.
- [ ] Enter invalid URL tokens or noncamera IDs in HA and ensure rejected inputs do not propagate to the TV; clear the URL to activate the 1–4 camera snapshot grid.
- [ ] Set Large + Desktop TV + Fit more cameras: the lower camera row should be more visible. Compare with Balanced and Larger text; no scrolling shortcuts that trap the user.
- [ ] In webpage mode, select **Select camera** → OK, use arrows to position the gold cursor over a Frigate tile, OK opens it. Test choosing another tile, Back, and Close, then normal TV playback. Ensure no accidental selection without entering browser mode.
- [ ] Run without HA controls enabled, or with screensaver OFF, and check the TV continues behaving as before.
- [ ] Verify no token, IP, or camera text appears in debug logs or issue comments.

**Owner onn hardware verification is required before publishing to main.**

## v0.16.12-dev.4 Google TV Ambient Mode watcher recovery (build 43, dev only)

The owner's onn Google TV only exposes Google's Ambient Mode sources (Photos, Art Gallery and Custom AI Art) instead of third-party DreamServices. Frigate Preview renders, and existing announcement overlays show during Ambient Mode, but the dashboard doesn't appear automatically. The old watcher only started when the feature was toggled and wasn't recovered following APK update/reboot.

- [ ] With feature ON and matching Frigate URL, confirm **Settings → Frigate screensaver → Screensaver status & permissions** shows watcher running, overlay permission and configuration.
- [ ] Trigger **Test Frigate as TV overlay**, verify fullscreen Frigate/Close/Back, then close. This is only a manual overlay rendering test.
- [ ] After normal Google Ambient Mode starts, return to diagnostics. If no `DREAMING_STARTED` arrives, this firmware needs a different supported idle detection mechanism. Do not interpret a successful manual overlay as automatic screensaver support.
- [ ] Reboot, then replace installed APK over prior build; verify boot/package-replaced receiver restarts only the already-opted-in watcher, even with TV alerts OFF, without microphone or unrelated Home Assistant changes.
- [ ] Confirm no overlay when feature OFF, permission absent, or dashboard unconfigured. Verify manual Close/Back and Android Dream STOP restore normal playback.

**Owner hardware QA required before main. If Ambient mode emits no Dream broadcast, automatic support remains unverified.**

## v0.16.12-dev.3 Frigate TV desktop layout preview (build 42, dev only)

Owner photo comparison showed Frigate live camera grid on laptop but a single giant camera tile and mobile navigation inside the onn Android WebView. The new **Desktop TV** default uses an installed-Chrome-version desktop browser ID, Android WebView wide/overview fit, and a 1440 CSS-pixel viewport after page load. Existing source URL allowlist and forbidden credentials remain intact.

- [ ] TV **Settings → Frigate screensaver → Frigate page layout: Desktop TV** (default). With **Dashboard size: Large**, preview the same Frigate web address as on laptop and compare the dashboard grid, event strip and left navigation.
- [ ] Confirm live video and camera playback still works, D-pad Back and **Close** exit normally, and on-device picture fills screen rather than reserving the former full-width 72dp black footer.
- [ ] Toggle **Frigate page layout: Automatic**, preview prior mobile view; toggle back to Desktop TV. This is a TV-local presentation setting only and must not reset pairing or change HA/Frigate server config.
- [ ] Repeat in idle-activated dream as well as Preview; try Small/Medium if desired, but full-width Large is recommended. Test one camera group, all cameras and the separate HA snapshot grid (unchanged).
- [ ] Check Frigate group layout is browser-local. Laptop arrangement does **not** transfer automatically to TV WebView.

**Owner TV verification required before promoting to main.**

## v0.16.12-dev.2 configuration UI repair (build 41, dev only)

The signed dev.1 build offered only Frigate ON/OFF and Preview. Build 41 adds a remote-friendly dedicated setup menu without changing user HA/Frigate server configuration. Check:

- [ ] **Settings → Frigate screensaver** opens ON/OFF, Frigate URL, Home Assistant cameras, size, system screensaver chooser, Preview, Back.
- [ ] Enter a valid local Frigate URL, save, and preview its page. Bad URL (including tokens/passwords) must show an error.
- [ ] Clear the URL, enter 1-4 actual `camera.*` Home Assistant entity IDs separated by commas and preview refreshing snapshots. Invalid IDs and 5+ entries must show an error, never silently succeed.
- [ ] If both URL and cameras are set, URL takes priority. Clear URL to select snapshots. Change Small/Medium/Large without changing voice-overlay size.
- [ ] Android system screensaver selection may be unsupported on some TVs; failure should show guidance instead of crash.
- [ ] Existing TV settings rows, voice, camera popups and authenticated alert buttons still work. No Android mic capture on screensaver.

**Hardware QA pending. Do not publish dev.2 to stable main without owner testing.**

## v0.16.12-dev.1 combined feature preview (build 40, not stable)

With matching dev HACS installed deliberately, follow the [action buttons](TV_ACTION_BUTTONS.md) and [Frigate screensaver](FRIGATE_SCREENSAVER.md) QA. Confirm:

- [ ] TV's 21 Settings entries open the correct menu (especially Follow-up, Mic input detection and Frigate screensaver).
- [ ] With screensaver OFF, normal TV behavior is unchanged.
- [ ] Frigate ON: Preview shows camera/page; selecting `Kellie's Voice Frigate` in Android's screensaver list opens the dashboard on idle; Close and Back dismiss it; microphone never opens.
- [ ] Action button event: use `acknowledge` only. TV D-pad OK emits exactly one HA event. Back, timeout and replacement emit none; old popup cannot issue another event.
- [ ] Buttons work on text, snapshot and live camera without breaking video; plain alerts remain unfocusable.
- [ ] Existing voice, follow-up listening/STOP, spoken alerts at 40%, camera and timer functions still work. Reconnect after HA restart.
- [ ] No credentials, Frigate auth tokens or HA pairing IDs leak into web links or test reports.

**Hardware QA remains pending; dev build only.**

## Issue #43: opt-in hands-free follow-up after spoken reply (development QA)

These checks are **not yet completed on a real TV**. Existing spoken TV
announcement success is a separate feature. Test only when we install a
single owner-approved consolidated dev APK, not one APK per feature.

- [ ] Feature is **OFF by default**; with OFF, a normal TALK interaction does
  not reopen the microphone after Kellie's answer.
- [ ] Set follow-up **ON (8 seconds)** on the TV. Ask a first question using
  an explicitly launched voice session. After Kellie's speech playback really
  finishes, the overlay enters the short listening preview. Ask **"What about
  tomorrow?"** without pressing the remote again. Confirm a second, contextual
  answer and that the same conversation context is retained.
- [ ] Remain silent until 5, 8, 10 and 15-second windows expire; the mic
  closes, the Android privacy indicator clears *after* the recorder stops,
  and no empty Assist request is generated.
- [ ] STOP, Back, Close, playback error, missing mic permission, and dropped
  HA/Wi-Fi connection prevent any delayed microphone restart. Reconnect does
  not reopen an expired follow-up.
- [ ] During preview and handoff, Android shows an active microphone and never
  falsely reports that capture is off. In the idle overlay with no capture,
  the mic privacy indicator is not left on merely because a panel is visible.
- [ ] YouTube audio focus fades only during capture/playback and recovers.
- [ ] The HA **Follow-up silence** select changes seconds only when the TV
  feature is locally ON. It may not remotely enable the microphone.
- [ ] Regress the current 30-second post-boot auth limit (Issue #44), existing
  16k/48k routing and disabled-microphone checks, and already-working spoken
  Home Assistant announcements.

**v0.16.8 #71: Philips audio-policy input allowlist field verification:** With
`Echo Reference In` and `line_in` but no real mic on a Philips
65PUS8507/12, TALK must show **No accessible microphone detected**
instead of starting a silent Assist STT session. Unknown/vendor, line
analog/digital, remote submix, BUS and tuner audio input types may not
silently count as microphones. Attach a genuine USB mic and confirm
speech works, test explicit compatibility override separately, and
regress the onn 4K Pro built-in microphone plus STOP/audio focus. The
hardware results, not just JUnit, decide if issue #71 is complete.

# Field testing and verified milestones

**v0.16.8 #52 app-wide theme field verification:** Open the Kellie's Voice main screen on onn 4K Pro, change Home Assistant **Overlay style** from Copper to Aurora and then Night Signal. The floating Julia overlay and main-screen background, headings, transcript, answer text, Settings rows and remote-focus colors should match without restarting Assist. Test local style selection, return from diagnostics, reconnect, dark-mode contrast, and D-pad focus. The original app branding remains. Java palette mapping unit tests must pass, and hardware confirmation is still pending.


**v0.16.7 issue #54 timer focus regression test:** On the onn 4K Pro, start a 10-second Pizza timer, let it ring, and press D-pad OK **without moving focus**. The Dismiss button should activate and repeating chimes must stop. Repeat with two timers, Down/OK to dismiss only one, and Back to dismiss the remainder. Countdown must not steal focus over YouTube. The Android host unit tests passed, but real-TV behavior remains unverified until the owner tests it.


**v0.16.7 issue #79 live popup parity test:** On both Philips models and onn 4K Pro, call `show_live_camera` with `show_header: false`, `image_fit: fit`, `refresh_seconds: 2`, and `duration_seconds: 6`. Confirm headerless live video still becomes visible when a frame arrives in time, 4:3 placeholder is uncropped, placeholder updates without blink before HLS ready, all HTTP still fetches stop at first video frame, and popup closes at requested short duration. A 6-second view with 7-second cold HLS startup may show only snapshots; this is expected. Retest 30-second duration and audio silence over HDMI/Netflix. Verify scoped non-admin HA alert transport forwards only allowlisted options, old callers keep defaults, and no outgoing arbitrary URL is accepted. Pending physical validation.


**v0.16.6 issue #78 verification matrix (after v0.16.5 Philips report):** On Philips 55OLED856/12 and 65PUS8507/12 using matching Android/HACS v0.16.5, compare TV alert runtime and HA `TV alert receiver connected` after (a) turning TV off/on, (b) Home Assistant YAML reload/restart, (c) networking disruptions, and (d) pressing **Resume TV alerts** while the service holds a stale WebSocket object. Expected: dedicated alert receiver re-establishes the scoped subscription, diagnostic turns ON, new popups work without Android Force stop. Verify setup timeouts, HA ping/pong heartbeat timeout, bounded retry/backoff, and old socket callbacks not affecting the fresh socket. Confirm a denied token/HA permission stays actionable, not an endless reconnect; disabling TV alerts halts watchdog; no microphone/background audio starts. The Android host JUnit tests cannot simulate Philips network standby, so physical QA is mandatory before claiming resolved. Keep #78 open until the original Philips tester confirms the receiver re-subscribes without Force stop.


**Draft #67 compatibility test matrix:** Philips integrated Android TV HDMI1 and foreground Netflix/TiviMate; safe software live H.264 (prefer low-res substream), snapshot fallback when decoder unavailable, HDMI still present during and after popup, no TV audio change; repeat popup/close/timeout/error 4001 and verify cleanup. onn 4K Pro: test safe software live plus optional explicit hardware choice, verify original cameras remain moving and muted. Full device QA required; the code is not a public v0.16.4 fix.

**Draft issue #70 physical testing (requires #67 branch):** Philips 4:3 driveway snapshot with show_header=false: no title/caption and no missing top-of-frame car. image_fit=fit preserves whole frame, crop preserves old appearance. Test 2–30s refresh, repeated show_camera calls for same camera at 2s intervals without popup teardown/flicker, and changing camera/title/fit resets window correctly. Network failures/reconnects, hide_camera and timeouts stop refresh; only authenticated HA camera_proxy requests are permitted. No camera video-decoder usage and no D-pad/audio focus regression. Matching HACS+APK required, physical QA pending.

**Combined draft checks (#67–#71):** Verify the full test APK+HACS combination on real Philips and onn hardware. Snapshot header/fit/refresh options must survive non-admin scoped WebSocket sanitation. Check that live decoding does not blank HDMI, wrong pairing IDs stay unverified, and inputless TVs do not send silent PCM. CI alone does not prove functionality. See [community QA](COMMUNITY_QA.md).

**Community release testing (October 24, 2026):** Android/HACS **v0.16.8** includes the candidate fixes for #67–#71; physical hardware validation is still in progress. The prior **v0.16.4** was verified on the maintainer's onn 4K Pro: voice/TTS, live driveway and doorbell video, weather popup, timer countdown/chimes and Back dismissal, plus named timer voice start/stop with custom HA automations. **Unverified or known issues:** new timer Dismiss/OK and main-app-wide theme fixes require physical verification, native Assist generic timer routing, other TV hardware, follow-up/wake-word features and action buttons from draft PRs. For an accurate first-time setup, see **[Install and test Kellie's Voice v0.16.8](INSTALL.md)**. **The milestones below are historical and may refer to earlier versions; they are not the current release checklist.**

Updated historical milestone: **2026-10-20**. Device: maintainer's **onn 4K Pro / Google TV**, with Android builds through GitHub Actions.

Distinguish what has actually worked on hardware from compiled features. A green build is **not** proof of behavior on a TV.

## #44 development QA: first voice request after boot

- [ ] Immediately after an onn 4K Pro reboot, activate TALK before HA auth:
  show Connecting and do not claim an answer is ready.
- [ ] Full-screen TALK while disconnected does **not** queue microphone
  activation; when the socket reports Ready, press TALK again.
- [ ] Explicit floating overlay request with auto-listen enabled and HA auth
  within 30 seconds starts the mic no more than once.
- [ ] Wait past 30 seconds before allowing HA auth. The old request must
  expire and no microphone may start until a fresh press of TALK.
- [ ] Restart HA / interrupt Wi-Fi; existing 2–30-second retry still
  recovers. No stale microphone or surprise recording.
- [ ] Distinguish missing/loading Assist pipeline from STT/agent/TTS errors;
  no raw private server messages may appear in on-screen diagnostics.
- [ ] Recheck normal STT, TTS, timer/alert popups and Back/STOP behavior.

**Pending real-TV testing, not a verified stable release fix.**

## #71 development QA: Philips phantom microphone opt-out

- [ ] Philips 55OLED856/12 / 65PUS8507/12 without real microphone:
  Settings → Mic input detection → **Disable microphone capture on
  this TV**. TALK must display the disabled message *without* opening
  AudioRecord, starting Assist or falsely reporting LISTENING.
- [ ] Enable **Try unverified microphone** while manually disabled.
  The manual disable still blocks audio capture.
- [ ] Re-enable microphone capture. Existing safe route checks resume.
  With a genuine USB microphone installed, test spoken transcription.
- [ ] On onn 4K Pro, manual disable defaults OFF and TALK/STOP/TTS
  with the built-in microphone continue working.
- [ ] Ensure the reversible device-local preference persists across
  signed in-place updates, independently of HACS TV alert settings.

**Workaround only:** this doesn't automatically detect a phantom
microphone. Keep #71 open pending genuine Philips hardware testing.

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
