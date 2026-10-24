<div align="center">
  <img src="docs/branding/kellies-voice-approved-banner.webp" alt="Kellie's Voice logo and branding banner" width="100%" />
</div>

# Kellie's Voice 🎙️📺

**Turn your Android TV into a Home Assistant voice companion, without turning your TV into a dashboard.**

Kellie's Voice is a native Android TV / Google TV app with a companion **Home Assistant custom integration**. Talk to your Home Assistant Assist agent, see useful answers in a floating overlay, receive quiet smart-home announcements, and pop up security-camera views **over what you're already watching** when Android allows it.

No replacement launcher, always-open browser tab, or separate AI server on the TV. Your existing Home Assistant setup does the thinking; Kellie's Voice handles the microphone, on-screen experience, and TV audio.

[**Get the Android TV app**](https://github.com/chadakenn/Kellies-Voice/releases/latest) · [**Install the HACS integration**](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) · [**Setup guide**](docs/INSTALL.md) · [**Report a problem**](https://github.com/chadakenn/Kellies-Voice/issues)

> **Camera/announcement popups not arriving after standby or a Home Assistant restart?** On the TV, **open the Kellie's Voice app → SETTINGS → TV alert status → Resume TV alerts** (the *button inside the status pop-up*). You may need to scroll down in SETTINGS to find **TV alert status**. First enable **SETTINGS → TV announcements & cameras**; the Resume button appears only with that setting ON, a configured Home Assistant connection, and Android's **Display over other apps** permission. This restores the *alert receiver*, which is separate from **Connect / reconnect** for voice. See [TV-alert recovery troubleshooting](docs/TROUBLESHOOTING.md#tv-camera-or-announcement-popups-missing-find-resume-tv-alerts).

## Kellie's Voice vs. Julia: what's the difference?

**Kellie's Voice** is the **Android TV app and Home Assistant integration** in this repository. It is named for Kellie.

**Julia** (sometimes written “Julie” in earlier test discussions) is simply the **maintainer's personal name for their Home Assistant voice assistant**, powered by their chosen conversation agent. Julia is **not** another app you need to install, an included AI model, a required device name, or a mandatory phrase to say. **You can use your own Home Assistant Assist pipeline and conversation agent, whatever you call it.** Ollama is optional.

**You do not need to say “Julia” to use Kellie's Voice.** Open the voice overlay and activate **TALK** or your configured shortcut, then speak naturally: “What's the weather this week?”, “Set a five-minute pizza timer,” or “Close the camera on the TV.” Specific actions still depend on your Home Assistant scripts, exposed entities, and voice pipeline configuration.

A **wake word** is a separate, *optional* way to start listening hands-free. It isn't automatically set to “Julia,” and the installed app does not require one. Optional wake-word support is under development and must be enabled deliberately when available. In these guides, references to *Julia* describe the maintainer's own test setup, not a setup step for everyone.

## Why would I use this?

Imagine you're watching YouTube and want to turn on the garage lights. Instead of finding your phone or opening a dashboard, launch the Kellie's Voice overlay, ask Home Assistant, and keep watching. A few other examples:

| You want to… | Kellie's Voice can… |
| --- | --- |
| Ask "Turn off the kitchen lights" or "What's the weather?" | Send your question to your configured Home Assistant Assist pipeline, then show and speak the response. Actual answers and device control depend on that pipeline and its tools. |
| Know when laundry finishes | Show a brief text announcement from a Home Assistant automation. |
| See who's approaching the front door | Display a short **camera snapshot popup** when a Frigate person sensor triggers. |
| Check the driveway on demand | Request an **experimental muted live camera overlay**, without leaving the current TV app. Moving live video **verified with driveway and doorbell cameras on onn 4K Pro using v0.16.1**. Other devices still need testing. |
| Move the assistant out of the way | Set voice-overlay position, size, appearance, auto-hide, and auto-listen from Home Assistant. |
| Stay current | Check for a signed Android app update right inside Kellie's Voice. HACS updates its *separate* Home Assistant component. |

**A key distinction:** automatic Frigate alerts currently show **still snapshots**, not live video. Live HLS streaming is a separate, **on-demand** feature, confirmed playing moving driveway **and doorbell** video on the onn 4K Pro in Android v0.16.1 (HA integration v0.16.0). Other cameras/devices remain unverified. Camera windows are Android overlays, not native system picture-in-picture. Announcements are **on-screen text**, not spoken TTS broadcasts.

## See it in action 📸

The banner at the top is promotional artwork, **not a screenshot**. These are phone photos of Kellie's Voice on a real onn 4K Pro running Google TV. They are not mockups. The camera views are this home's own doorbell and yard.

### Voice overlay

![Kellie's Voice on a real onn 4K Pro answering What time is it with 4:23 AM](docs/screenshots/onn-voice-time.jpg)

The floating card over the Google TV apps screen. The question was "What time is it?" and Home Assistant answered **4:23 AM**.

![Kellie's Voice overlay hearing Show me the doorbell camera](docs/screenshots/onn-voice-doorbell.gif)

The same overlay while Home Assistant is still answering "Show me the doorbell camera."

### Camera snapshot

![Still camera snapshot popup titled Snapshot Test After Reopening App](docs/screenshots/onn-snapshot-popup.jpg)

An opt-in **still snapshot** popup, titled "Snapshot Test After Reopening App," after the app was opened again. This is not live video.

### Live doorbell

![Live doorbell card starting over the voice overlay on Google TV](docs/screenshots/onn-live-doorbell.jpg)

The muted live doorbell card opening on top of the voice overlay. The voice card still says the doorbell camera is live. This photo is one moment from that test, not a recording of the motion. Moving doorbell and driveway video was already checked on this TV with Android v0.16.1.

The older [illustrative concept previews](docs/screenshots/conversation-preview.svg) are still in the repo as design references only. See [docs/screenshots](docs/screenshots/README.md).

## How it works

```text
Android TV / Google TV
  Kellie's Voice (microphone, popup overlays, on-TV speech)
           │ authenticated Home Assistant connection
           ▼
     Home Assistant
  Assist pipeline + your chosen conversation agent
  Devices, automations, Frigate cameras
           ▲
  Kellie's Voice HACS integration
  TV settings + announce / snapshot / experimental live actions
```

Kellie's Voice uses **your existing Home Assistant Assist pipeline**. It works with the conversation agent and speech components you configure in Home Assistant. Local Ollama/LLM agents are optional, not required, and aren't installed by the Android APK.

## What Home Assistant can change

The HACS integration and the Android APK are separate. Update HACS, restart Home Assistant, then install a signed APK over the current app. Do not uninstall it. Turn **Settings → Home Assistant layout** on and leave Kellie's Voice connected once so the new control is adopted. After that, the TV can be closed and a change waits until the next connection.

| Control | Where it lives |
| --- | --- |
| Overlay position, size, style, auto-hide, auto-listen | Home Assistant device page, when layout control is on |
| TV alert position and size | Home Assistant device page. These do not move the voice overlay |
| Microphone sensitivity: Quiet room, Normal, TV on | Home Assistant device page. It changes the pause detector on the **next** question. It does not turn the microphone on |
| Home Assistant URL, access token, pairing ID | TV only |
| Microphone permission, display-over-apps permission, alert opt-in, alert resume | TV only |
| Connect, new conversation, diagnostics, app update, forget login | TV only |

No new voice script is required for sensitivity. The real photos in [See it in action](#see-it-in-action-) show the overlay and camera cards, not the Home Assistant device page. To set the preset from an automation, use your own entity id:

```yaml
action: select.select_option
target:
  entity_id: select.your_tv_microphone_sensitivity
data:
  option: "TV on"
```

Turning **Home Assistant layout** off stops later remote changes. A TV that has never changed sensitivity stays on **Normal**. The on-TV menu still works and, while layout control is on, copies the choice back. These controls are not a plan to remove the TV setup screen. Login, permissions, alert opt-in, and updates stay on the TV.

The full camera script examples, including close and a 30 second view, are in [docs/VOICE_CAMERA_SCRIPTS.md](docs/VOICE_CAMERA_SCRIPTS.md).

## Get started: install both parts

**New testers:** follow the complete [v0.16.8 setup and hardware-testing checklist](docs/INSTALL.md), including TV permissions, HA connection, HACS pairing, and known limitations. The quick steps below are a short reference.

The Android app and the HACS integration are **two different downloads**. You need both for Home Assistant-controlled overlay settings and camera announcements.

### 1. Install the Android TV app

1. On a supported Android TV / Google TV device, install the **signed APK** from [the latest GitHub Release](https://github.com/chadakenn/Kellies-Voice/releases/latest).
2. Open Kellie's Voice and configure its Home Assistant URL, access token, microphone and **Display over other apps** permissions.
3. Test a question through your configured Home Assistant Assist pipeline. Use the on-screen controls or the TV shortcut supported by your device.
4. In **Settings → Home Assistant layout**, turn remote layout management **ON**. To get announcements and camera popups, independently enable **Settings → TV announcements & cameras**. After an app update or TV reboot, that receiver tries to resume by itself. If Android blocks the start, open Kellie's Voice once, or use **Settings → TV alert status → Resume TV alerts**. Android TV may not show a banner for that notice; the status screen is the reliable place to look.
5. Later updates: use **Settings → Check for app updates** and confirm Android's installer. Never uninstall the app just to update, or you may lose your saved connection.

### 2. Install the Home Assistant integration via HACS

[![Open Kellie's Voice in HACS](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration)

1. If this is your first installation: **HACS → ⋮ → Custom repositories → Add**, paste `https://github.com/chadakenn/Kellies-Voice` and select **Integration**.
2. Open Kellie's Voice in HACS and download the **matching v0.16.8 integration**. Restart Home Assistant.
3. Go to **Settings → Devices & services**, choose the discovered Kellie's Voice TV and add it. If mDNS discovery doesn't work, select **Add Integration → Kellie's Voice** and enter the ID shown under the TV app's **Settings → Home Assistant layout → Show this TV's pairing ID**.
4. Open the registered TV device in Home Assistant. Configure voice-overlay position, size, style, auto-hide and auto-listen. You can now call the actions below.

**HACS does not install or update the Android APK.** The in-app updater does not update your HACS integration. Install matching versions of both for new features. [Full integration documentation](custom_components/kellies_voice/README.md).

## What can Home Assistant send to the TV?

<details>
<summary><strong>Example: show an announcement</strong></summary>

```yaml
action: kellies_voice.announce
data:
  title: Laundry
  text: The washer has finished.
  duration_seconds: 10
```

</details>

<details>
<summary><strong>Example: show a Frigate snapshot</strong></summary>

```yaml
action: kellies_voice.show_camera
data:
  entity_id: camera.doorbell
  title: Someone is at the door
  duration_seconds: 15
```

Use an existing Home Assistant `camera.*` entity. A Frigate person-detection automation or [included blueprint](custom_components/kellies_voice/blueprints/frigate_person_popup.yaml) can trigger this for you. The popup is a still image; camera setup and person-detection sensors come from your HA/Frigate configuration.

</details>

<details>
<summary><strong>Example: request live video (experimental)</strong></summary>

```yaml
action: kellies_voice.show_live_camera
data:
  entity_id: camera.driveway
  title: Driveway live
  duration_seconds: 90
```

Requires a streaming-capable HA camera and matching v0.16.0 or newer Android/HA components. This uses a muted, temporary HLS player and **has been verified with moving driveway and doorbell video on the onn 4K Pro using Android v0.16.1**; this doesn't yet establish support for every camera or Android TV model. For Assist/Ollama voice requests, expose separate scripts for authorized cameras and ensure your conversation agent can call Home Assistant scripts. Voice invocation still requires a speaking test.

</details>

Leaving **TV device ID** blank sends an announcement or camera action to all configured Kellie's Voice TVs. Set that field when you have multiple TVs and want just one.

See [TV event setup](docs/TV_EVENTS.md), [HA service details](custom_components/kellies_voice/README.md), and [camera/announcement design notes](docs/ANNOUNCEMENTS_AND_PIP.md).

## Compatibility and important limits

| Capability | Status |
| --- | --- |
| Home Assistant voice conversation and TV speech | **Tested** on onn 4K Pro |
| Floating overlay, remote scrolling, YouTube audio behavior | **Tested** on onn 4K Pro |
| HA changing the assistant overlay position | **Tested** on onn 4K Pro |
| Targeted text announcements over TV | **Tested** on onn 4K Pro |
| Frigate still-image camera popups | **Tested** with driveway camera on onn 4K Pro |
| Person-detection automations for driveway and doorbell | **Configured** in HA; natural detection end-to-end test pending |
| Live HLS camera overlay | **Verified** moving driveway and doorbell video on onn 4K Pro with Android v0.16.1; other cameras and TV devices still need testing |
| Other Google TV and Android TV brands | **Not comprehensively tested** |

Kellie's Voice uses a user-controlled microphone; it **does not intercept the Google Assistant voice button** or promise access to the remote's Bluetooth microphone. Some streaming apps or protected content may limit overlays. The TV event listener is opt-in and Android may stop it while suspended; delivery is not guaranteed 24/7. No external ports need to be opened on the TV for Home Assistant events.

For problems, see [Troubleshooting](docs/TROUBLESHOOTING.md), [Update guide](docs/UPDATES.md), [Known testing procedures](docs/TESTING.md), [Privacy and security guidance](SECURITY.md) and [project vision](docs/PROJECT_VISION.md).

## Seven-day forecast

Ask your configured Home Assistant voice assistant for the week and a card can appear over the current app. It uses your Home Assistant weather entity. It is not automatic, and it is not a camera.

Create this script, replace the weather entity, and expose it to Assist. The full steps are in [docs/WEATHER_FORECAST.md](docs/WEATHER_FORECAST.md).

```yaml
alias: Seven day forecast on TV
description: Show the next seven days of weather on Kellie's Voice.
mode: single
sequence:
  - action: kellies_voice.show_forecast
    data:
      entity_id: weather.forecast_home
```

Omit `duration_seconds` to use the TV's saved **Forecast duration**. Add it under `data` (8 to 45 seconds) only to override the duration for this request.

On the device page, **Forecast source** is the weather entity used when a script leaves `entity_id` empty. **Forecast position**, **Forecast size**, and **Forecast duration** move only that card. The usual spot is bottom center for 20 seconds. Home Assistant 2024.4 or newer is required because the action calls `weather.get_forecasts`. Update HACS and restart Home Assistant, then install the matching Android build over the current app. Do not uninstall it. The card below is a **simulation**, not a photo from the onn.

| Thu 22 | ☀ | 68° / 49° | 10% |
| Fri 23 | ⛅ | 71° / 52° | 20% |
| Sat 24 | ☂ | 64° / 50° | 60% |
| Sun 25 | ☁ | 66° / 48° | 30% |
| Mon 26 | ☀ | 70° / 47° | 5% |
| Tue 27 | ❄ | 42° / 31° | 40% |
| Wed 28 | ☾ | 45° / 33° | — |

## Project status and contributing

### Current release: v0.16.8 (October 24, 2026)

**Android TV APK v0.16.8 + matching Home Assistant integration v0.16.8 are publicly available.** This release includes named countdown pills and repeating timer completion alerts, seven-day weather popups, optional Aurora Glass overlay styling, camera overlays and Home Assistant announcements. On the maintainer's onn 4K Pro, the countdown and chimes, Back-to-dismiss, forecast card, voice questions, and moving driveway/doorbell live camera popups have been tested. Named timer **voice** start/stop worked after the maintainer added Home Assistant automations, not with zero additional setup.

**Known limitations:** microphone detection now rejects Philips echo-reference and line-in devices but still needs USB/onn field testing (#71); the timer alert **OK/Dismiss** fix ships for physical testing (#54); app-wide theme synchronization has shipped for onn TV testing (#52); the HACS README banner is being improved; ordinary timer phrases may invoke Home Assistant's unsupported native timer intent until routed to Kellie's Voice. Follow-up listening, wake-word support, and actionable announcement buttons are **not** included in this public release, even though draft PRs and test builds exist.

For a complete **first-time installation and testing checklist**, start with **[Install Kellie's Voice: new testers](docs/INSTALL.md)**. Other Android/Google/Fire TV models, audio behavior, camera compatibility, and longer-running reliability especially need real tester reports. [Open a GitHub issue](https://github.com/chadakenn/Kellies-Voice/issues) with device model, app/HACS version, reproducible steps and redacted screenshots.

Kellie's Voice is a community-built Home Assistant companion, **not an official Home Assistant or HACS product**.

## Close a camera by voice (v0.16.2)

Kellie's Voice adds a dedicated `kellies_voice.hide_camera` action to **dismiss a live or snapshot camera popup immediately**. It does not turn off video playback from your other TV apps, dismiss text announcements, or operate the microphone. Configure an Assist-exposed script named **Close camera on TV**, then activate listening and say **“Close the camera on the TV.”** You can also call the HA action directly:

```yaml
action: kellies_voice.hide_camera
data:
  # Leave empty for all configured TVs, or select one paired TV:
  device_id: "your-kellies-voice-tv-id"
```

`show_live_camera` supports **4–180 second** durations, plus optional headerless and full-frame display and pre-video snapshot refresh. Expose camera scripts with a `duration_seconds` field if your Assist agent can pass script arguments; a named 30-second camera script is a reliable alternative for simple speech commands. **Voice recognition of these new commands must be checked on your chosen conversation agent**. Both the v0.16.2 Android app **and** v0.16.2 HACS integration are required for the hide action.

### Copy-and-paste Home Assistant script setup

The Android app alone cannot make your selected Home Assistant conversation agent understand new camera phrases. **Create and expose the corresponding Home Assistant scripts**. Detailed full YAML for driveway, doorbell, 30-second requests, instant close, multi-TV targeting, and Frigate automations is in the **[Home Assistant voice-camera script setup guide](docs/VOICE_CAMERA_SCRIPTS.md)**.

**Example: Close camera on TV** (Home Assistant → Settings → Automations & scenes → Scripts → Create script → Edit in YAML):

```yaml
alias: Close camera on TV
description: >
  When asked to close or hide the camera, dismiss the current
  Kellie's Voice camera window without stopping recordings.
icon: mdi:close-box
mode: single
sequence:
  - action: kellies_voice.hide_camera
    data: {}
```

**Example: Show doorbell for 30 seconds** (create as a second script):

```yaml
alias: Show doorbell camera for 30 seconds on TV
description: >
  Open the live doorbell camera on the TV for 30 seconds
  when the user asks for a 30-second view.
icon: mdi:cctv
mode: single
sequence:
  - action: kellies_voice.show_live_camera
    data:
      entity_id: camera.doorbell
      title: Live doorbell
      duration_seconds: 30
```

Substitute your own `camera.*` entity IDs. On Home Assistant's **Settings → Voice assistants → Expose** screen, expose these scripts to Assist. If multiple TVs are paired, add `device_id` inside each action's `data` to target just one. Both the TV app and HACS integration must be at **v0.16.2 or later**, followed by a Home Assistant restart and reopening the TV app so its receiver reconnects. Voice phrases depend on the selected Assist conversation agent.

## v0.16.3: TV camera placement and alert recovery

On your Kellie's Voice TV's **Home Assistant device** page, configure **TV alert position** (top left, top right, bottom left, bottom right or center) and **TV alert size** (small, medium or large). These are separate from the microphone/voice overlay placement. The defaults keep the existing top-right medium popup and changes apply to the *next* announcement, snapshot or live camera window.

Android v0.16.3 also attempts to restart the **already opted-in** TV alert receiver after a reboot or in-place APK update. It never automatically enables alerts or starts the microphone. Because Android TV can block background service startup, go to **Kellie's Voice → Settings → TV alert status → Resume TV alerts** if needed. Wi-Fi disconnects use the receiver's existing reconnect backoff; no stale alerts are replayed. An Android force-stop or some standby conditions still require opening the app.

**Update both parts:** install Android v0.16.3 using **Settings → Check for app updates**, then update the Kellie's Voice HACS integration to v0.16.3 and **restart Home Assistant**. Don't uninstall the Android app or delete settings. On-TV verification of the *new* size/position controls and reboot/update recovery remains to be done. The live `TextureView` camera player and `hide_camera` action are preserved.
