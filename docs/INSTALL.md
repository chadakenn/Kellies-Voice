# Install Kellie's Voice: start here (new testers)

**Current public release: v0.16.9 (October 24, 2026)** · **Documentation checked: October 24, 2026**

Welcome! **Kellie's Voice** turns an Android TV / Google TV into a voice companion for **your own Home Assistant**. It has two separate parts:

1. **Android TV app**: listens after you press TALK, speaks Home Assistant's answer through the TV, and displays floating voice, timer, weather, announcement, and camera windows.
2. **Home Assistant custom integration (via HACS)**: registers the TV and exposes per-TV controls and actions such as `kellies_voice.announce`, `show_camera`, `show_forecast`, and `start_timer`.

**Install both parts at v0.16.9** for the full supported feature set. Installing the APK does **not** install HACS, and HACS does **not** install the APK. No separate AI server is needed on your TV.

**Quick recovery if announcements, timers, or camera popups stop:** Open the Kellie's Voice TV app (not Android's system Settings). Choose **SETTINGS → TV alert status → Resume TV alerts**. **TV alert status** is farther down the SETTINGS list; **Resume TV alerts** is a button at the bottom of its status pop-up, *not* an item in the TV announcements menu. If the button is missing, turn **SETTINGS → TV announcements & cameras** ON, configure Home Assistant, and allow Android **Display over other apps**. A working voice/TALK connection does not guarantee the separate alert receiver is connected. [Detailed recovery steps](TROUBLESHOOTING.md#tv-camera-or-announcement-popups-missing-find-resume-tv-alerts).

> **Julia/Julie is not required.** That is only the maintainer's chosen name for their own Home Assistant voice assistant, **not** a second app or the wake word. Use your existing Home Assistant Assist agent, with any name or no name. After activating TALK, just speak naturally. Ollama and wake-word listening are optional, not required.

## What you need

- **Home Assistant** running and reachable from the TV, with a working **Assist pipeline** containing speech-to-text (STT), a conversation agent, and text-to-speech (TTS) if you want spoken answers. Test that pipeline in Home Assistant first.
- An **Android TV / Google TV** device where you can sideload an APK, use the remote's D-pad/OK, and grant microphone permission. **The onn 4K Pro with its built-in microphone is the principal field-tested device.** Other models, including Fire TV devices, are welcome for testing but are **not verified compatible**; some devices have no accessible microphone.
- The TV and Home Assistant must be able to reach one another on your network. A valid Home Assistant URL and a **long-lived access token**, created under your own Home Assistant user profile's **Security** settings.
- [HACS](https://www.hacs.xyz/) installed in Home Assistant to use the recommended integration setup. Manual custom-component installation is also possible.

**Philips microphone follow-up (#71):** The v0.16.8 microphone input allowlist is present, but physical retests on two Philips TVs still incorrectly start listening with only non-microphone input devices. This remains unresolved in v0.16.9. A compatible USB microphone may help; please share the TV app's non-sensitive Hardware microphone diagnostics. [Report findings on #71](https://github.com/chadakenn/Kellies-Voice/issues/71).

**v0.16.9 TV alert startup recovery (issue #78):** An HA Core restart can temporarily expose a paired TV before its alert-position control is ready. v0.16.9 now retries this specific condition without bypassing actual user permissions; a true permission denial remains terminal. The owner reproduced the bug on an onn 4K Pro with v0.16.8 and verified manual Resume, but automatic recovery with v0.16.9 still needs a physical retest. The separate Home Assistant `TV alert receiver connected` sensor confirms subscription, not specific popup delivery. [Report re-tests here](https://github.com/chadakenn/Kellies-Voice/issues/78). No microphone is started by this fix.

**Never post your HA token, authentication headers, camera URLs containing credentials, or signing keys** in GitHub issues, screenshots, or testing reports. Prefer a valid trusted HTTPS connection to Home Assistant; HTTP on a LAN does not encrypt the token or audio.

## Step 1: Install the signed Android TV APK

1. Open the **[latest public Kellie's Voice release](https://github.com/chadakenn/Kellies-Voice/releases/latest)**. The current release is **v0.16.9** (community verification release).
2. Download the release asset **`app-release.apk`**. Do **not** download a PR's `app-debug.apk` or GitHub Actions test artifact for a first installation.
3. Transfer it to the TV. Options include a USB stick, a TV-compatible file manager, **Send Files to TV** on your phone and TV, or ADB (`adb install -r app-release.apk`).
4. On the TV, open the APK and grant **Install unknown apps** to the installer/file manager if Android asks. Complete the installer.
5. Launch **Kellie's Voice** from the TV's Apps list.

**If updating an existing permanently signed Kellie's Voice app:** use **Settings → Check for app updates** (or install a newer release-signed APK over it), and **do not uninstall or clear data**. That preserves the HA URL, encrypted access token, TV pairing, and settings.

**If Android says the signature conflicts:** stop rather than deleting the installed app. Only very old *debug-signed* builds require the one-time uninstall-and-reconfigure migration documented in [Troubleshooting](TROUBLESHOOTING.md) and [Signing](SIGNING.md). Do not assume a signature error on an existing signed release is normal.

## Step 2: Connect Kellie's Voice to Home Assistant

1. Open **Kellie's Voice → SETTINGS → Configure Home Assistant** on the TV.
2. Enter your Home Assistant base URL, including scheme and port if applicable (for example `https://ha.example.net` or `http://homeassistant.local:8123` if your TV can resolve it). Enter the **long-lived access token** you generated. Keep it private.
3. Choose **Save and connect**. The TV should report **Connected and authenticated**.
4. Grant the **microphone permission** when prompted, then select **TALK** using the TV remote's D-pad and OK.
5. Wait for **LISTENING**, speak a simple question such as **“What time is it?”**, and select **STOP** if needed. Verify that your words appear on-screen, the Home Assistant answer appears, and audio plays through the TV speakers if TTS is configured.
6. If connection or audio fails, open [Troubleshooting](TROUBLESHOOTING.md) and check the HA Assist pipeline, URL, TV sound, and on-screen error first.

**Important:** The standard Google Assistant microphone button on the physical remote may still launch **Google**, not Kellie's Voice. On the tested onn 4K Pro, voice input uses the TV device's built-in microphone. A remote's Bluetooth mic is **not guaranteed accessible** to third-party apps. You do not need to say “Julia” to start a command, and a hands-free wake word is **not** part of public v0.16.8.

**Tip:** The Google TV/Google Home phone remote can make URL and token entry easier; keyboard/clipboard behavior varies. There is no QR-code pairing flow yet.

## Step 3: Install the matching Home Assistant integration with HACS

1. If HACS is not installed, [install HACS](https://www.hacs.xyz/docs/use/) first.
2. In **Home Assistant → HACS → ⋮ → Custom repositories**, add:
   `https://github.com/chadakenn/Kellies-Voice`  
   Select category **Integration**. This is usually needed only once.
3. Open [Kellie's Voice in HACS](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) and install/download the **v0.16.9** integration (not an older v0.6 or v0.16.0 copy).
4. **Restart Home Assistant** so the custom integration and services load.
5. On your TV, enable **Kellie's Voice → Settings → Home Assistant layout**. Leave the TV online and connected while pairing.
6. In Home Assistant, open **Settings → Devices & services** and add the discovered **Kellie's Voice** device. If discovery doesn't find it (for example across VLANs), choose **Add integration → Kellie's Voice**, then enter the pairing ID from **TV → Settings → Home Assistant layout → Show this TV's pairing ID**.
7. Open the newly added TV device in Home Assistant. Confirm its position, size, overlay theme/style, alert settings, and other controls appear. Changes can require the next TV connection or next popup to become visible.

**Don't confuse the IDs:** the TV's **Kellie's Voice pairing ID** is used when adding the device and in actions such as `kellies_voice.start_timer`. A separate Home Assistant **device-registry ID** is used internally for Assist source-device tracking, a feature still in review for a future release.

**Manual alternative:** copy the complete `custom_components/kellies_voice` folder into your HA `config/custom_components/kellies_voice`, then restart HA and add the integration as above. HACS is preferred for testers because it can manage updates.

## Step 4: Enable TV popups (optional, but needed for cameras, forecasts, and timers)

1. On the TV, open **Settings → TV announcements & cameras** and turn it **ON**. It is opt-in.
2. Grant Android's **Display over other apps** permission to Kellie's Voice when requested.
3. Use the TV's **Preview announcement** action to confirm a floating popup appears. It should not start listening or interrupt your movie.
4. In **Home Assistant → Developer tools → Actions**, test a text popup:

```yaml
action: kellies_voice.announce
data:
  title: Test
  text: Hello from Home Assistant!
  duration_seconds: 10
```

5. To test an existing camera entity, run (replace the example entity ID with **your** camera):

```yaml
action: kellies_voice.show_camera
data:
  entity_id: camera.your_camera
  title: Camera snapshot test
  duration_seconds: 15
```

This is a **still image**, not live video.

**Live-camera compatibility (#67, in v0.16.5 for testing):** In TV Settings, choose **Live camera**: *Safe software (default)*, *Snapshots only*, or *Hardware (advanced)*. Safe software only selects software video decoders so an integrated TV's hardware decoder stays free for HDMI, Netflix or TiviMate. Use a lower-resolution H.264 camera/substream for best results; if software decoding is unavailable the popup stays on the snapshot and does **not** claim moving video. Hardware is opt-in and potentially disrupts HDMI on integrated TVs. Both Philips models were confirmed playing live video safely in v0.16.5; please report regressions on other devices.

For an on-demand **muted live HLS camera**, see [Voice camera scripts](VOICE_CAMERA_SCRIPTS.md) and [TV events](TV_EVENTS.md). The TV must receive events over its active HA connection, and a camera must support the relevant HA snapshot/stream functionality.

**Snapshot display improvements (#70, in v0.16.5):** `kellies_voice.show_camera` can optionally use `show_header: false` (hide title/caption), `image_fit: fit` (show full uncropped 4:3 frame), and `refresh_seconds: 2..30` (in-place periodic snapshots; `0` turns auto-refresh off). Repeated calls to the same camera with the same layout update the image without tearing down the window. No new camera URLs or background microphone permissions are required. This improvement was confirmed working on both Philips TVs in v0.16.5.

**v0.16.5 community test release:** The update integrates Philips HDMI-safe live decoder, full-frame in-place snapshots, non-admin scoped TV alert WebSocket, per-TV alert receiver connectivity, and safe microphone route diagnostics. Install the matching **v0.16.8 Android APK and v0.16.8 HACS integration** to test the current release; see [the community test guide](COMMUNITY_QA.md). The fixes are released for public testing and still need hardware feedback.

**Live camera improvements (#79, v0.16.7):** `kellies_voice.show_live_camera` now supports `show_header: false`, `image_fit: fit`, `refresh_seconds: 2..30` for the loading snapshot (default 0), and `duration_seconds: 4..180`. A short view may close before the first actual HLS video frame arrives; enabling HA's **Preload camera stream** for that camera can improve startup while using extra resources. See [camera examples](VOICE_CAMERA_SCRIPTS.md). Philips physical checks are still requested on [#79](https://github.com/chadakenn/Kellies-Voice/issues/79).

**Main app theme synchronization (#52, v0.16.8):** When you choose **Overlay style** in Home Assistant or TV Settings, the full-screen Kellie's Voice main screen, D-pad focus, Settings dialogs and diagnostics now use the corresponding Copper Glass, Compact Charcoal, Night Signal or Aurora Glass palette. Changing the HA style repaints the main screen without restarting Assist; reopen a Settings dialog to see its latest palette. Please test on onn 4K Pro and report mismatches on [#52](https://github.com/chadakenn/Kellies-Voice/issues/52).

**Multiple TVs:** leaving the optional `device_id` out of announcement/camera actions targets every configured Kellie's Voice TV. Add the **specific TV pairing ID** to target one. Timers require a targeted TV and are not broadcast by default.

**After a reboot, standby or Wi-Fi drop:** the TV alert listener may not restart automatically because Android TV can block background starts. Open the Kellie's Voice app's **SETTINGS**, scroll down to **TV alert status**, open it, and select the **Resume TV alerts** button on the status dialog. This is **not** the separate **TV announcements & cameras** toggle. In Home Assistant, the paired TV's **TV alert receiver connected** diagnostic should change to ON after a successful subscription. It confirms a connection, **not** delivery of a specific event: send a *new* test announcement or timer to verify. Missed events are not queued/replayed. The receiver is **not** guaranteed to operate 24/7.

## Step 5: Try the optional camera, timer and weather features

| Feature | First test | Extra setup or limitation |
| --- | --- | --- |
| Voice conversation | Press TALK, ask “What's the weather?” | Depends on your selected HA Assist pipeline and exposed devices |
| Aurora Glass / overlay theme | Change **Overlay style** on the TV or HA device page | Overlay style is shipped; **main-screen theme synchronization** is still being tested in PR #53 |
| Seven-day forecast | Run `kellies_voice.show_forecast` | Requires a daily-capable HA `weather.*` entity and popup receiver; [forecast guide](WEATHER_FORECAST.md) |
| Countdown timer | Run the sample `kellies_voice.start_timer` below | Native HA Assist timer phrases do **not** automatically route to Kellie's Voice. Voice routing needs extra HA configuration; [timer guide](VOICE_TIMERS.md) |
| Camera close | Run `kellies_voice.hide_camera` during a visible live/snapshot card | Voice version requires your own Assist-exposed script; [camera guide](VOICE_CAMERA_SCRIPTS.md) |

**Test a TV timer** (replace the TV ID with the paired ID shown in its settings):

```yaml
action: kellies_voice.start_timer
data:
  device_id: REPLACE_WITH_PAIRED_TV_ID
  name: Pizza
  duration_seconds: 20
  speak_alert: false
```

The timer should display a countdown pill; after expiry, chimes repeat until dismissed. **Back** worked on the tested onn 4K Pro. In v0.16.7, [PR #55](https://github.com/chadakenn/Kellies-Voice/pull/55) provides improved D-pad focus and an OK/Enter fallback on the ringing **Dismiss** button; physical onn 4K Pro verification remains pending in [#54](https://github.com/chadakenn/Kellies-Voice/issues/54). To prevent the alert before it rings, run `kellies_voice.cancel_timer` with the same ID and name.

The maintainer has also confirmed **starting and stopping named timers through their TV microphone** after configuring custom Home Assistant voice automations. Those automations are **home-specific**, not automatically installed for new users. If “Set a timer” answers *“Sorry, timers are not supported on this device”*, HA's **native** timer handler was invoked instead of the Kellie's Voice action. Follow the [timer setup guide](VOICE_TIMERS.md) or explore the [name-free timer automation draft](https://github.com/chadakenn/Kellies-Voice/blob/docs/voice-timers-llm-scripts-v0164/docs/VOICE_TIMERS_PLAIN_SPEECH.md) only if you're comfortable configuring and testing HA sentence triggers. Be especially cautious in homes with other Assist satellites because unscoped triggers can catch their commands too.

For weather, the recommended initial test is **Developer tools → Actions**, not a spoken request. Once direct actions work, [create and expose the forecast script](WEATHER_FORECAST.md) to your chosen Assist conversation agent. You do **not** need to name that agent Julia.

## Where development stands (October 24, 2026)

| Status | Features |
| --- | --- |
| **Publicly downloadable: v0.16.9** | Authenticated HA voice/TTS, remote-driven overlay, per-TV layout controls, on-screen text announcements, camera snapshots, experimental on-demand live HLS, Aurora overlay styling, seven-day forecast popup, named TV countdowns |
| **Confirmed on maintainer's onn 4K Pro** | Basic conversation/TTS, driveway and doorbell moving live popups, weather forecast card, TV countdown/chimes with **Back** dismissal, and named timer voice start/stop after custom HA setup |
| **Known public-release issues** | Philips microphone route detection still fails on some Philips TVs (#71); v0.16.9 alert retry needs real HA restart/TV validation (#78); native Assist may intercept generic timer speech |
| **Code/CI in review; NOT shipped in v0.16.9** | Opt-in follow-up listening/microphone privacy [#62](https://github.com/chadakenn/Kellies-Voice/pull/62) / [#63](https://github.com/chadakenn/Kellies-Voice/pull/63); optional “Okay Nabu” wake word [#59](https://github.com/chadakenn/Kellies-Voice/pull/59); spoken/actionable TV buttons [#51](https://github.com/chadakenn/Kellies-Voice/pull/51) / [#61](https://github.com/chadakenn/Kellies-Voice/pull/61) |

**Do not mistake a passing PR build, a debug APK, or a draft integration branch for a public release.** PR #64's old v0.16.4 preview was closed. Install the signed public **v0.16.9 GitHub Release** unless you're explicitly testing a newer branch.

## New tester checklist

Please start with these quick, low-risk checks before experimenting with locks, doors, or complex automations:

- [ ] Record **TV brand/model**, Android/Google/Fire TV OS version, **Kellie's Voice APK version**, **HACS integration version**, and Home Assistant version.
- [ ] Confirm the signed v0.16.9 APK installs, Home Assistant authenticates, and **TALK → answer → TV speech** works twice in a row.
- [ ] Check **overlay appearance**, D-pad focus, and whether the normal TV audio resumes after an answer.
- [ ] Enable **TV announcements & cameras** and verify a local preview, HA text popup and, if available, one valid camera snapshot.
- [ ] Test an on-demand **live** camera only if your HA camera supports streaming; confirm actual moving frames, not merely a snapshot fallback.
- [ ] With the matching HACS integration, test your TV's overlay and alert positioning from the HA device page.
- [ ] If interested, test a daily weather forecast and a 20-second timer using **Developer tools → Actions**. Note which of **Back** and **OK/Dismiss** works.
- [ ] Reboot, resume from standby, interrupt Wi-Fi briefly, and check whether the app/event receiver reconnects without unnecessary mic activation.
- [ ] Check whether your device's built-in or remote microphone is accessible. **Do not assume the physical remote's Google Assistant button will launch this app.**

**Report results (include version v0.16.9):** open a [GitHub issue](https://github.com/chadakenn/Kellies-Voice/issues), with a short title, device/OS version, app/HACS versions, exact steps, exact non-sensitive on-screen error or transcript, expected versus actual behavior, and optionally **redacted** photos/video. Mark whether the test was with the **public v0.16.9 release** or an explicitly labeled preview. Report partial successes too: e.g. “snapshot loads but live frames don't move,” or “Back dismisses timer but OK doesn't.”

Further information: [Troubleshooting](TROUBLESHOOTING.md) · [Camera scripts](VOICE_CAMERA_SCRIPTS.md) · [Weather setup](WEATHER_FORECAST.md) · [Timer setup](VOICE_TIMERS.md) · [Signed app updates](UPDATES.md) · [Testing history](TESTING.md) · [Security](../SECURITY.md).

**Testers are welcome; installing Kellie's Voice does not install or enable door unlocking, always-on microphones, or other consequential Home Assistant automations.**
