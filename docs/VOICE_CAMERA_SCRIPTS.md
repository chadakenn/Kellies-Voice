# Home Assistant voice scripts for Kellie's Voice 🎙️📺

This is the **public, copy-and-paste setup** for opening, timing, and closing camera windows by voice. Nothing here is tied to the original developer's TV or Home Assistant installation.

**About the name “Julia”:** Julia is the maintainer's **personal name for their Home Assistant assistant**, not a required name or wake word for Kellie's Voice. Use the conversation agent **you** configured in Assist and speak without a name prefix after activating listening. Some older examples include “Julia” because they were recorded during the maintainer's testing, not because the app requires it. [More about naming](../README.md#kellies-voice-vs-julia-whats-the-difference).

## What you need first

1. **Android TV app v0.16.2 or newer**, installed in place from Kellie's Voice → Settings → Check for app updates. Do not uninstall the app.
2. **Home Assistant Kellie's Voice integration v0.16.2 or newer**, installed through HACS. Restart Home Assistant after upgrading. HACS does **not** install the Android APK.
3. On the TV, set **SETTINGS → Alerts & cameras → TV notifications & camera popups → ON**; allow **Display over other apps**. Open the main app after an update to restart the opt-in event receiver. The app can be closed by Android when in standby; alerts are not guaranteed continuously.
4. Pair the TV under Home Assistant **Settings → Devices & services → Kellie's Voice**. For manual pairing, show the ID inside TV Settings → Home Assistant layout → Show this TV's pairing ID. Never publish your own pairing ID in a screenshot.
5. Confirm `camera.doorbell` and/or `camera.driveway` exist in Home Assistant and support streaming. **Replace all example `camera.*` IDs below with your own entities**. Any Assist-compatible conversation agent can work with these scripts; Ollama is optional.

### Creating a script from the UI

Home Assistant → **Settings → Automations & scenes → Scripts → Create script**. Choose a new script and select **Edit in YAML** from its editor menu. Paste **one** of the complete examples below, save it, then repeat for the others you want.

The snippets below use the **single-script UI editor** format (starting with `alias:`). If editing `scripts.yaml` manually instead, nest the same content under a unique script ID, for example `kellies_voice_close_camera:`, and indent the snippet beneath it. Reload scripts afterward.

To make your Home Assistant Assist agent call a script, open **Settings → Voice assistants → Expose**, and expose the newly created script to **Assist** (not Alexa/Google unless you want those assistants too). Enable tool/script access in the conversation agent if your particular agent requires it. **Exposing does not guarantee that every LLM recognizes every phrase**; test the request.

## 1. Show the live doorbell camera (duration can be spoken)

Create a script called **Show doorbell camera live on TV**.

```yaml
alias: Show doorbell camera live on TV
description: >
  When asked to show the doorbell camera, show a moving live doorbell
  feed on Kellie's Voice TV. If the user specifies a duration from
  15 to 180 seconds, pass duration_seconds to this script.
  Otherwise use 90 seconds.
icon: mdi:cctv
mode: single
fields:
  duration_seconds:
    name: Duration (seconds)
    description: Optional length, from 15 to 180 seconds
    example: 30
    selector:
      number:
        min: 15
        max: 180
        step: 1
        mode: box
sequence:
  - action: kellies_voice.show_live_camera
    data:
      entity_id: camera.doorbell
      title: Live doorbell
      duration_seconds: "{{ duration_seconds | default(90, true) | int }}"
```

Try **"Show me the doorbell camera on the TV"** or **"Show the doorbell camera for 30 seconds."** Passing the number depends on the conversation agent; use the fixed script in section 3 if the agent forgets it. The overlay automatically closes after the requested duration.

## 2. Show the live driveway camera

Create a separate script called **Show driveway camera live on TV**. This follows the same timed-action pattern and helps your conversation agent select the intended camera.

```yaml
alias: Show driveway camera live on TV
description: >
  Show a moving live driveway camera feed on Kellie's Voice TV.
  Use duration_seconds if the user requests 15 to 180 seconds.
  Otherwise default to 90 seconds.
icon: mdi:cctv
mode: single
fields:
  duration_seconds:
    name: Duration (seconds)
    description: Optional length from 15 to 180 seconds
    example: 30
    selector:
      number:
        min: 15
        max: 180
        step: 1
        mode: box
sequence:
  - action: kellies_voice.show_live_camera
    data:
      entity_id: camera.driveway
      title: Live driveway
      duration_seconds: "{{ duration_seconds | default(90, true) | int }}"
```

## 3. Fixed 30-second camera script (most reliable with basic voice agents)

If your conversation agent opens a camera but ignores the spoken duration, create a *separate*, clearly named script so it does not need to infer or pass numerical parameters:

```yaml
alias: Show doorbell camera for 30 seconds on TV
description: >
  When asked to show the doorbell for 30 seconds, use this script.
  Open a moving doorbell video overlay for exactly 30 seconds.
icon: mdi:cctv
mode: single
sequence:
  - action: kellies_voice.show_live_camera
    data:
      entity_id: camera.doorbell
      title: Live doorbell (30 seconds)
      duration_seconds: 30
```

For a driveway version, duplicate this script, change the title/alias to **Show driveway camera for 30 seconds on TV**, and use `entity_id: camera.driveway`. Expose each script to Assist separately.

## 4. Close the current camera immediately

Create a script called **Close camera on TV** and expose it to Assist:

```yaml
alias: Close camera on TV
description: >
  When asked "close the camera", "hide the live camera",
  "stop showing the doorbell" or "close the driveway view",
  dismiss the currently displayed Kellie's Voice camera window.
  Do not stop recordings or switch off the physical camera or TV.
icon: mdi:close-box
mode: single
sequence:
  - action: kellies_voice.hide_camera
    data: {}
```

After activating listening, say **"Close the camera on the TV."** The action dismisses a live or snapshot camera window right away and releases its player, but **does not dismiss an unrelated text announcement**, start a microphone, turn off the TV, or stop Frigate recording. Camera views still auto-close by timeout if no voice action is sent.

## Targeting one TV instead of every paired TV

By default the actions above broadcast to **every TV paired with Kellie's Voice**. For a specific TV, add this entry inside the `data:` block of each script's `kellies_voice.*` action:

```yaml
      device_id: "PASTE_YOUR_TV_PAIRING_ID_HERE"
```

For the close script, this goes inside its existing `data:` block instead of `data: {}`. Use the TV's **Kellie's Voice pairing ID**, **not** the Home Assistant device-registry ID, the Home Assistant access token, or the camera entity ID. Don't publish your real ID.

## 5. Optional automation: Frigate person-detection snapshot

Automatic detection does not need voice scripts. Use the included [Frigate person popup blueprint](../custom_components/kellies_voice/blueprints/frigate_person_popup.yaml) or this Home Assistant automation in the UI YAML editor:

```yaml
alias: Front door person popup on TV
mode: single
triggers:
  - trigger: state
    entity_id: binary_sensor.doorbell_person_occupancy
    to: "on"
actions:
  - action: kellies_voice.show_camera
    data:
      entity_id: camera.doorbell
      title: Person at the door
      duration_seconds: 15
  - delay:
      seconds: 60
```

Change both entity IDs to the actual Frigate entities in **your** Home Assistant instance. This is a **still snapshot** alert, not an automatic live video feed.

## Integrated Android TV decoder safety (issue #67)

Public v0.16.4 can interrupt Philips HDMI playback when its overlay reserves the hardware video decoder. For those TVs, use `show_camera` snapshots until a validated fix ships.

v0.16.5 and newer include per-TV **Settings → Live camera** choices: safe software decoder (default), snapshots only, or opt-in hardware decoder for known-good boxes. Safe mode **never tries a hardware video decoder**. Try a low-resolution H.264 substream exposed as a separate Home Assistant camera entity, since 1080p HLS may be too demanding for software decoders on older TVs. An honest still-image fallback is not a live-playback success.

QA must verify HDMI stays visible **during and after** playback, Netflix/TiviMate stays intact, audio remains uninterrupted, and repeated popup/hide/timeout calls release resources. On onn 4K Pro, verify existing live performance (and opt-in hardware mode if needed). Do not merge on CI alone; physical Philips and onn testing is required.

## Snapshot controls for older TVs (verified in v0.16.5)

On v0.16.5+ Android and HACS, `kellies_voice.show_camera`
accepts three **optional** settings without changing existing scripts:

```yaml
action: kellies_voice.show_camera
data:
  entity_id: camera.driveway
  show_header: false
  image_fit: fit
  refresh_seconds: 3
  duration_seconds: 30
```

- `show_header: false` removes both the heading and "Home Assistant camera snapshot" caption. Existing cards keep their current header by default.
- `image_fit: fit` shows the whole 4:3 (or other) camera frame; black bars are preferable to cutting off part of the driveway. Default `crop` preserves existing appearance.
- `refresh_seconds: 0` (default) disables automatic updates; 2–30 requests one still approximately every N seconds, within the popup lifetime. No streaming/video decoder is used.
- Repeating the same camera call with unchanged header/fit/title reuses the visible window and refreshes the image without blinking or stealing D-pad focus. Requests for a different camera/layout replace the popup normally.
- Network fetches use the existing authenticated same-HA-host `camera_proxy` URL; callbacks from stale cards are discarded, pending requests are canceled and refresh timers stop on close. This is pseudo-live, **not** HLS moving video, and may be slower than the chosen refresh interval.

The original reporter verified these snapshot changes on both Philips TVs in v0.16.5, including no flicker and no cropping.

## Live popup options (v0.16.7, issue #79)

With matching Android and HACS v0.16.7, `show_live_camera` also
accepts `show_header`, `image_fit` and `refresh_seconds`, just like snapshots:

```yaml
action: kellies_voice.show_live_camera
data:
  entity_id: camera.driveway
  show_header: false
  image_fit: fit
  refresh_seconds: 3
  duration_seconds: 6
```

- `show_header: false` removes the live title and status line. Video still
  becomes visible at the **actual first frame**, even with no status label.
- `image_fit: fit` letterboxes both the **snapshot placeholder** and
  the actual muted HLS video, using the video-size aspect ratio. Default
  `crop` preserves the existing player presentation.
- `refresh_seconds` defaults to **0** (off). When set to **2–30**, only the
  underlying still snapshot is refreshed while HLS connects; it stops fetching
  at the first video frame, on dismissal or when the popup is replaced.
- `duration_seconds` now accepts **4–180** (previously 15–180). A **6-second**
  popup may close before a slow HLS stream displays moving frames.
- Philips tester reported ~7-second HLS startup on a cold camera, nearly
  immediate after enabling Home Assistant's **Preload camera stream**
  for that entity. Preloading may increase Home Assistant/Frigate resource
  use; opt in only where useful.
- This shipped in **v0.16.7** via [PR #81](https://github.com/chadakenn/Kellies-Voice/pull/81). Philips/onn physical validation is pending in #79.

## Test and troubleshoot

- Start with Home Assistant **Developer tools → Actions**, running `kellies_voice.show_live_camera` for a known streaming camera, or run the new scripts from **Settings → Automations & scenes → Scripts**.
- Next call `kellies_voice.hide_camera` during a visible camera popup. If the action does not exist, upgrade the **HACS integration to v0.16.2** and restart Home Assistant.
- If the action succeeds but the TV ignores it, verify **the Android app is also v0.16.2**, that TV announcements & cameras are ON, the main app has been opened since upgrading, the TV is online, and any `device_id` matches its pairing ID.
- If your chosen Assist conversation agent fails to understand the phrase but tapping **Run** works, this is an **Assist tool exposure/intent** issue, not necessarily a TV-streaming error. Expose the script to Assist and use short, distinctive names; a 30-second fixed script avoids parameter-passing issues.
- Don't confuse a camera **snapshot fallback** with successful moving live video. Both driveway and doorbell live video have been verified on the onn 4K Pro with Android v0.16.1; v0.16.2 close-command behavior still needs the next on-device test.
- The floating popup is intentionally non-focusable and does not capture remote keys. Back-button dismissal is **not** part of this version. Use the voice/script close command or wait for the timeout.

**Security:** Only expose trusted camera scripts to your conversation agent. Avoid scripts that accept arbitrary camera URLs, RTSP credentials or shell commands; Home Assistant keeps camera access and the TV fetches its temporary stream over the authenticated connection.

## Microphone sensitivity (no camera script)

This is a device-page select, not a voice script. HACS must include the **Microphone sensitivity** control, and the TV app must be new enough to read it. Restart Home Assistant after the HACS update. On the TV, turn **Settings → Home Assistant layout** on and open Kellie's Voice once. The APK and the HACS download are different updates. Do not uninstall the app.

The choices are **Quiet room**, **Normal**, and **TV on**. Normal is the existing default. Changing it does not start the microphone, enable auto-listen, or turn on TV alerts. It applies to the next question. The same choice remains in the TV settings menu and syncs back while layout control is on.

```yaml
action: select.select_option
target:
  entity_id: select.your_tv_microphone_sensitivity
data:
  option: "TV on"
```

Replace `select.your_tv_microphone_sensitivity` with the entity on your Kellie's Voice device. Do not publish a pairing ID or token. Login, permissions, alert opt-in, and app updates stay on the TV. Later setup may move more safe preferences to Home Assistant, but those local controls are staying.
