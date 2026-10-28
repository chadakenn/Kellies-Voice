# Install Kellie's Voice: start here (new testers)

**Release guide: v0.16.14 (October 28, 2026)** · Use only the [latest published signed release](https://github.com/chadakenn/Kellies-Voice/releases/latest), not an Actions debug artifact.

Welcome! **Kellie's Voice** turns an Android TV / Google TV into a voice companion for **your own Home Assistant**. It has two separate parts:

1. **Android TV app**: listens after you press TALK, speaks Home Assistant's answer through the TV, and displays floating voice, timer, weather, announcement, and camera windows.
2. **Home Assistant custom integration (via HACS)**: registers the TV and exposes per-TV controls and actions such as `kellies_voice.announce`, `show_camera`, `show_forecast`, and `start_timer`.

**For v0.16.14, install both the signed Android TV APK and matching v0.16.14 HACS integration** now that v0.16.14 is publicly available. Installing the APK does **not** install HACS, and HACS does **not** install the APK. No separate AI server is needed on your TV.

**Quick recovery if announcements, timers, or camera popups stop:** Open the Kellie's Voice TV app (not Android's system Settings). Choose **SETTINGS → Alerts & cameras → TV alert status → Resume TV alerts**. Resume is inside the status popup; if missing, first enable **TV announcements & cameras**, configure Home Assistant, and allow Android **Display over other apps**. A working voice/TALK connection does not guarantee the separate alert receiver is connected. [Detailed recovery steps](TROUBLESHOOTING.md#tv-camera-or-announcement-popups-missing-find-resume-tv-alerts).

> **Julia/Julie is not required.** That is only the maintainer's chosen name for their own Home Assistant voice assistant, **not** a second app or the wake word. Use your existing Home Assistant Assist agent, with any name or no name. After activating TALK, just speak naturally. Ollama and wake-word listening are optional, not required.

## What's new in v0.16.14

**Hands-free Okay Nabu** is now available under **SETTINGS → Voice & microphone → Wake word**. It is OFF by default. Speak a single-breath request such as "Okay Nabu, turn on office lights". Optional wake-only media ducking (Off/25%/50%/75%) lowers playback after the wake phrase, then restores it. The settings hub now groups Home Assistant, Voice & microphone, Overlay & appearance, Alerts & cameras, Screensaver, and Updates & account. This release also preserves explicit wake ON across TV reboots, but Android 14+ may require opening Kellie's Voice once before microphone listening resumes; it cannot automatically begin audio capture directly at boot. See [v0.16.14 notes](releases/v0.16.14.md) for hardware-test scope and privacy.

## Previously introduced in v0.16.12

**Signed Android build 50 and matching HACS 0.16.12:** The opt-in HA camera screensaver can show four muted live camera tiles. Set camera selectors through the TV's paired Home Assistant device page, leave **Frigate web URL blank** to use these HA cameras and enable **HA camera grid playback** for Live (1/2/4 cameras). The 2×2 grid starts with snapshots by default; live HLS and audio depend on compatible HA cameras and video decoders. Four live streams can tax smaller devices.

**Remote camera control:** D-pad to highlight a tile and press **OK** to open it full-screen. Only the selected camera's audio turns on, **if that stream has audio**. Press physical **Back** to return to four muted cameras; a gold focus outline identifies **Back to cameras** and **Close**. The maintainer confirmed 4 live feeds, automatic screensaver/reboot, full-screen Doorbell audio and Back/focus on onn Google TV. Other devices remain community-test targets. [Complete camera setup](FRIGATE_SCREENSAVER.md) and [stable release notes](releases/v0.16.12.md).

## Previous v0.16.11 improvements, still included

**Important for v0.16.14:** Update the Android signed **versionCode 54** APK and matching HACS **0.16.14** component. Install the APK over the previously signed release (v0.16.12 code 50 or experimental dev builds through code 53) without clearing saved logins. Updating HACS requires a Home Assistant restart to load the component.

- **Follow-up listening:** On the TV, **Settings → Follow-up listening → On (8 seconds)**. This is OFF by default. A second question can be heard after Kellie's spoken answer without pressing TALK; an 8-second silence timeout and STOP both close the microphone. Tested on the maintainer's onn 4K Pro; other TVs and Back/Close/disconnect cases still need testing.
- **Optional spoken alerts and speech gain:** Keep **Settings → Alerts & cameras → TV notifications & camera popups** on, and use **Settings → Alerts & cameras → Announcement voice volume** for a separate 20–100% speech level (default 60%). An HA `kellies_voice.announce` with a short `speak` sentence reads it aloud; without `speak` the announcement stays silent. A real Home Assistant speech test at 40% was audibly quieter on onn 4K Pro.
- **Camera voice commands:** Home Assistant must have the matching camera scripts exposed to Assist. If the AI says the camera is showing but no popup appears, check the **TV alert receiver connected** sensor and choose **Settings → TV alert status → Resume TV alerts**. For voice dismissal, a script must call `kellies_voice.hide_camera`, not merely fire an old custom event. Local conversation agents that send oversized prompts or bad tool JSON may need tuning (#39).
- **Timers, pairing, microphones:** The native Assist TV timer bridge is OFF by default and remains unverified on owner hardware (#57); existing TV timer scripts remain supported. Manual pairing confirmation (#69) and 48 kHz microphone fallback (#71/#112) still need testing on affected hardware. Remote Google's Assistant mic button is not intercepted, and Fire TV is unverified (#41).

See the [current release notes](releases/v0.16.14.md), [hardware checklist](TESTING.md), and [troubleshooting](TROUBLESHOOTING.md).

## What you need

- **Home Assistant** running and reachable from the TV, with a working **Assist pipeline** containing speech-to-text (STT), a conversation agent, and text-to-speech (TTS) if you want spoken answers. Test that pipeline in Home Assistant first.
- An **Android TV / Google TV** device where you can sideload an APK, use the remote's D-pad/OK, and grant microphone permission. **The onn 4K Pro with its built-in microphone is the principal field-tested device.** Other models, including Fire TV devices, are welcome for testing but are **not verified compatible**; some devices have no accessible microphone.
- The TV and Home Assistant must be able to reach one another on your network. A valid Home Assistant URL and a **long-lived access token**, created under your own Home Assistant user profile's **Security** settings.
- [HACS](https://www.hacs.xyz/) installed in Home Assistant to use the recommended integration setup. Manual custom-component installation is also possible.

**Philips microphone follow-up (#71):** The v0.16.8 microphone input allowlist is present, but physical retests on two Philips TVs still incorrectly start listening with only non-microphone input devices. This remains pending alternate-TV hardware validation in v0.16.11. A compatible USB microphone may help; please share the TV app's non-sensitive Hardware microphone diagnostics. [Report findings on #71](https://github.com/chadakenn/Kellies-Voice/issues/71).

**v0.16.9 TV alert startup recovery (issue #78):** An HA Core restart can temporarily expose a paired TV before its alert-position control is ready. v0.16.9 now retries this specific condition without bypassing actual user permissions; a true permission denial remains terminal. The owner reproduced the bug on an onn 4K Pro with v0.16.8 and verified manual Resume, but automatic recovery with v0.16.9 still needs a physical retest. The separate Home Assistant `TV alert receiver connected` sensor confirms subscription, not specific popup delivery. [Report re-tests here](https://github.com/chadakenn/Kellies-Voice/issues/78). No microphone is started by this fix.

**Never post your HA token, authentication headers, camera URLs containing credentials, or signing keys** in GitHub issues, screenshots, or testing reports. Prefer a valid trusted HTTPS connection to Home Assistant; HTTP on a LAN does not encrypt the token or audio.

## Step 1: Install the signed Android TV APK

1. Open the **[latest public Kellie's Voice release](https://github.com/chadakenn/Kellies-Voice/releases/latest)**. For the new wake/settings features choose **v0.16.14** from the published Releases list; v0.16.12 is the preceding stable release.
2. Download the release asset **`app-release.apk`**. Do **not** download a PR's `app-debug.apk` or GitHub Actions test artifact for a first installation.
3. Transfer it to the TV. Options include a USB stick, a TV-compatible file manager, **Send Files to TV** on your phone and TV, or ADB (`adb install -r app-release.apk`).
**Alternative: Downloader app.** On TVs where [Downloader](https://www.aftvnews.com/downloader/) is available, enter the official releases page URL `https://github.com/chadakenn/Kellies-Voice/releases/latest` in its browser, open the newest public release, and choose the asset named `app-release.apk`. GitHub redirects and TV browsers differ; if the APK does not download correctly, use **Send Files to TV**, USB, or the ADB option above. **There is no official Kellie's Voice Downloader short code** in this guide; do not rely on codes posted by unknown third parties.

**ADB alternative from your computer:** If your TV's developer options and ADB network access are already enabled and the TV is reachable from your computer, download `app-release.apk` on the computer and run `adb install -r app-release.apk`. ADB connectivity and device pairing depend on your Android TV/Google TV model. Do not expose ADB over the public internet.

4. On the TV, open the APK and grant **Install unknown apps** to the installer/file manager if Android asks. Complete the installer.
5. Launch **Kellie's Voice** from the TV's Apps list.

**If updating an existing permanently signed Kellie's Voice app:** use **Settings → Check for app updates** (or install a newer release-signed APK over it), and **do not uninstall or clear data**. That preserves the HA URL, encrypted access token, TV pairing, and settings.

**If Android says the signature conflicts:** stop rather than deleting the installed app. Only very old *debug-signed* builds require the one-time uninstall-and-reconfigure migration documented in [Troubleshooting](TROUBLESHOOTING.md) and [Signing](SIGNING.md). Do not assume a signature error on an existing signed release is normal.

## Step 2: Connect Kellie's Voice to Home Assistant

1. Open **Kellie's Voice → SETTINGS → Home Assistant → Configure Home Assistant** on the TV.
2. Enter your Home Assistant base URL, including scheme and port if applicable (for example `https://ha.example.net` or `http://homeassistant.local:8123` if your TV can resolve it). Enter the **long-lived access token** you generated. Keep it private.
3. Choose **Save and connect**. The TV should report **Connected and authenticated**.
4. Grant the **microphone permission** when prompted, then select **TALK** using the TV remote's D-pad and OK.
5. Wait for **LISTENING**, speak a simple question such as **“What time is it?”**, and select **STOP** if needed. Verify that your words appear on-screen, the Home Assistant answer appears, and audio plays through the TV speakers if TTS is configured.
6. If connection or audio fails, open [Troubleshooting](TROUBLESHOOTING.md) and check the HA Assist pipeline, URL, TV sound, and on-screen error first.

**Important:** The standard Google Assistant microphone button on the physical remote may still launch **Google**, not Kellie's Voice. On the tested onn 4K Pro, voice input uses the TV device's built-in microphone. A remote's Bluetooth mic is **not guaranteed accessible** to third-party apps. You do not need to say “Julia” to start a command. The optional **Okay Nabu** wake word starts hands-free listening in v0.16.14 once enabled; it does not take over Google Assistant's remote microphone button.

**Tip:** The Google TV/Google Home phone remote can make URL and token entry easier; keyboard/clipboard behavior varies. There is no QR-code pairing flow yet.

## Step 3: Install the matching Home Assistant integration with HACS

1. If HACS is not installed, [install HACS](https://www.hacs.xyz/docs/use/) first.
2. In **Home Assistant → HACS → ⋮ → Custom repositories**, add:
   `https://github.com/chadakenn/Kellies-Voice`  
   Select category **Integration**. This is usually needed only once.
3. Open [Kellie's Voice in HACS](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) and install/download the **matching v0.16.14** integration from the HACS Releases selector.
4. **Restart Home Assistant** so the custom integration and services load.
5. On your TV, enable **Kellie's Voice → Settings → Home Assistant → Home Assistant layout**. Leave the TV online and connected while pairing.
6. In Home Assistant, open **Settings → Devices & services** and add the discovered **Kellie's Voice** device. If discovery doesn't find it (for example across VLANs), choose **Add integration → Kellie's Voice**, then enter the pairing ID from **TV → Settings → Home Assistant layout → Show this TV's pairing ID**.
7. Open the newly added TV device in Home Assistant. Confirm its position, size, overlay theme/style, alert settings, and other controls appear. Changes can require the next TV connection or next popup to become visible.

**Don't confuse the IDs:** the TV's **Kellie's Voice pairing ID** is used when adding the device and in actions such as `kellies_voice.start_timer`. A separate Home Assistant **device-registry ID** is used internally for Assist source-device tracking, an internal value, not the pairing ID.

**Manual alternative:** copy the complete `custom_components/kellies_voice` folder into your HA `config/custom_components/kellies_voice`, then restart HA and add the integration as above. HACS is preferred for testers because it can manage updates.

## Step 4: Enable TV popups (optional, but needed for cameras, forecasts, and timers)

1. On the TV, open **Settings → Alerts & cameras → TV notifications & camera popups** and turn it **ON**. It is opt-in.
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

## Current release status (October 28, 2026)

| Status | Features |
| --- | --- |
| **Publicly downloadable: v0.16.14** | Signed Android build 54 and matching HACS 0.16.14, six-category TV Settings hub, opt-in Okay Nabu and post-wake ducking, existing Assist, screensaver, alerts, timers and cameras |
| **Verified on the owner's onn 4K Pro** | Basic talk/TTS, moving doorbell/driveway video, four-camera screensaver with sound on selected full-screen camera, forecast, custom-script timer start/stop, early Okay Nabu one-breath command and volume duck/restore on a development build |
| **Still requiring device acceptance** | Stable wake reactivation after reboot and permission loss; Philips phantom microphone inputs (#71), TV pairing typo/delivery (#69), native timer phrase routing (#57), other device compatibility (#41) |
| **Development only, not yet released** | Draft voice silence fallback #183 and APK version-security checks #184, and future custom wake/TV-off modes. CI green is not hardware approval. |

**Install only a published signed [v0.16.14 APK](https://github.com/chadakenn/Kellies-Voice/releases/tag/v0.16.14) and matching HACS integration** for normal use. Do not mistake GitHub Actions debug APKs, drafts, or PR branches for stable releases.

## New tester checklist

Please start with these quick, low-risk checks before experimenting with locks, doors, or complex automations:

- [ ] Record **TV brand/model**, Android/Google/Fire TV OS version, **Kellie's Voice APK version**, **HACS integration version**, and Home Assistant version.
- [ ] Confirm the signed v0.16.14 APK installs, Home Assistant authenticates, and **TALK → answer → TV speech** works twice in a row.
- [ ] Check **overlay appearance**, D-pad focus, and whether the normal TV audio resumes after an answer.
- [ ] Enable **TV announcements & cameras** and verify a local preview, HA text popup and, if available, one valid camera snapshot.
- [ ] Test an on-demand **live** camera only if your HA camera supports streaming; confirm actual moving frames, not merely a snapshot fallback.
- [ ] With the matching HACS integration, test your TV's overlay and alert positioning from the HA device page.
- [ ] If interested, test a daily weather forecast and a 20-second timer using **Developer tools → Actions**. Note which of **Back** and **OK/Dismiss** works.
- [ ] Reboot, resume from standby, interrupt Wi-Fi briefly, and check whether the app/event receiver reconnects without unnecessary mic activation.
- [ ] Check whether your device's built-in or remote microphone is accessible. **Do not assume the physical remote's Google Assistant button will launch this app.**

**Report results (include the actual installed version, ideally v0.16.14):** open a [GitHub issue](https://github.com/chadakenn/Kellies-Voice/issues), with a short title, device/OS version, app/HACS versions, exact steps, exact non-sensitive on-screen error or transcript, expected versus actual behavior, and optionally **redacted** photos/video. Mark whether the test was with the **public v0.16.14 release** or an explicitly labeled preview. Report partial successes too: e.g. “snapshot loads but live frames don't move,” or “Back dismisses timer but OK doesn't.”

Further information: [Troubleshooting](TROUBLESHOOTING.md) · [Camera scripts](VOICE_CAMERA_SCRIPTS.md) · [Weather setup](WEATHER_FORECAST.md) · [Timer setup](VOICE_TIMERS.md) · [Signed app updates](UPDATES.md) · [Testing history](TESTING.md) · [Security](../SECURITY.md).

**Testers are welcome; installing Kellie's Voice does not install or enable door unlocking, always-on microphones, or other consequential Home Assistant automations.**


**Optional spoken announcements:** After installing, open the TV app → SETTINGS → Alerts & cameras → Spoken announcements → ON. Then Home Assistant `kellies_voice.announce` can include a `speak` sentence. Other audio is ducked temporarily and restored. Default remains silent.
