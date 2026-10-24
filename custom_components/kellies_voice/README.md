# Kellie's Voice for Home Assistant

**Give your TV a voice, useful popups, and quick access to security-camera views without replacing your TV launcher.** Kellie's Voice consists of a native Android TV/Google TV app and this Home Assistant integration. The TV handles speech and on-screen overlays; the integration lets HA automations and controls talk to it.

**Why it matters:** ask your Assist agent about the house while a show plays, get a text alert when the washing machine finishes, or see a Frigate driveway/doorbell snapshot when a person approaches. On-demand live video is available as an **experimental** option; moving driveway **and doorbell** video is now confirmed on the onn 4K Pro with Android v0.16.1, though other cameras/devices need verification.

| User-facing feature | Home Assistant feature |
| --- | --- |
| Move the floating voice overlay | Per-TV position, size, style, auto-hide and auto-listen entities |
| Show a quiet on-TV notification | `kellies_voice.announce` |
| See a Frigate camera still when someone appears | `kellies_voice.show_camera`, plus included person-detection blueprint |
| Request a temporary live camera feed | `kellies_voice.show_live_camera` (experimental HLS, muted) |

**You also need the [signed Android TV app](https://github.com/chadakenn/Kellies-Voice/releases/latest).** HACS installs only this integration. Enable **Home Assistant layout** in the TV settings and separately enable **TV announcements & cameras** for notifications and camera popups.

**Actual screenshots:** we're collecting real onn 4K Pro captures of the current app and overlays. Promotional artwork and legacy previews are not actual captures. See the [screenshot checklist](../../docs/screenshots/README.md).

This integration changes overlay layout and can send announcement and camera events to configured TVs. It does not start the microphone, replace the TV login, or install an APK.

**Pairing diagnostic (v0.16.8 test release):** The Home Assistant TV device will expose a **TV alert receiver connected** diagnostic when its matching Android app opens the newer read-scoped notification channel. An ID that was manually mistyped will remain OFF/"never", rather than appearing verified. This is a socket-presence indicator, not a guarantee that a specific popup appeared. Older APKs on the legacy event subscription cannot report this new diagnostic. Requires matching v0.16.8 HACS and Android builds. Community hardware QA is in progress.

Home Assistant never connects directly to the TV for layout changes. The TV can advertise through mDNS or you can enter its stable pairing ID manually. Either way, the TV reads those controls using its existing Home Assistant login.

Announcement and camera actions stay inside Home Assistant too. They fire events. They do not open a port on the TV, and they do not put tokens or stream passwords in the event.

## What is “Julia”?

In the maintainer's demonstrations, **Julia** is simply the name they gave their own **Home Assistant voice assistant**. **Kellie's Voice** is the TV app and HACS integration, not the assistant's name. You **do not** need to create, download, or rename an assistant “Julia.” Use your own Home Assistant Assist pipeline and conversation agent; the built-in agent or an Ollama/LLM agent may be used according to your setup.

**No name or wake word is required for normal voice commands.** Activate the TV's listening overlay, then ask a question or give a command naturally. A wake word is an optional, separately configured feature, not synonymous with “Julia.” References to Julia in older developer test instructions are **examples only**. See the [main README explanation](../../README.md#kellies-voice-vs-julia-whats-the-difference).

For **first-time testers**, start with the [full v0.16.8 installation, setup and tester checklist](../../docs/INSTALL.md). This HACS page covers the Home Assistant half; the Android TV APK must be installed separately.

## Install with HACS (recommended)

[![Open Kellie's Voice in HACS](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration)

1. Make sure HACS is installed in your Home Assistant instance.
2. **First-time only:** in **HACS → ⋮ → Custom repositories**, add `https://github.com/chadakenn/Kellies-Voice` with category **Integration**. The shortcut badge does not register an unknown repository automatically.
3. Click **[Open in HACS](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration)**. Download the **public v0.16.8** Home Assistant integration to match the [v0.16.8 Android app](https://github.com/chadakenn/Kellies-Voice/releases/tag/v0.16.8), not old `v0.6` / `v0.16.0` builds.
4. Restart Home Assistant, then turn **Home Assistant layout ON** inside the Kellie's Voice TV app. Either add the TV under **Settings → Devices & services → Discovered** or use **Add Integration → Kellie's Voice** for manual pairing with its device ID (shown on the TV under Settings → Home Assistant layout → Show this TV's pairing ID). This works if mDNS is blocked across VLANs.
5. Optionally turn on **TV announcements & cameras** on the TV to receive HA announcements and Frigate camera snapshots.

After custom repository registration, the badge opens Kellie's Voice for future HACS downloads/updates. The first registration and HA restart are still required. HACS installs only the Home Assistant component, not the TV APK.

## Manual installation

1. Copy this folder to `config/custom_components/kellies_voice` on the Home Assistant machine. Include `services.yaml`, `services.py`, and the `blueprints` folder.
2. Restart Home Assistant.
3. On the TV, open **Kellie's Voice → Settings → Home Assistant layout** and turn it on. Leave the app open.
4. In Home Assistant, add the TV under **Discovered**. If it doesn't appear, choose **Add Integration → Kellie's Voice**, then enter the TV's stable ID from **Settings → Home Assistant layout → Show this TV's pairing ID**. Use the exact ID; it is not your HA token.

The device page then has the voice overlay controls (position, size, style, auto-hide, and auto-listen), **Microphone sensitivity** (Quiet room, Normal, or TV on), plus two separate controls for popups: **TV alert position** and **TV alert size**. The same page can also set **Screensaver size** (Small, Medium, or Large) and **Screensaver cameras** (up to four `camera.*` entities, or `none`). Those two change the opt-in Frigate screensaver only. They do not move the voice overlay or start the microphone. Restart Home Assistant after updating the integration so the new controls appear.

The same controls are also on a settings page: sidebar → **Kellie's Voice**. Position, size, style, alerts, microphone sensitivity, forecast, and screensaver size are dropdowns. Screensaver cameras is a checkbox list of the camera entities already in Home Assistant, capped at four. Changing a control sends it to the TV. The microphone stays off. The page is for Home Assistant administrators. The device-page entities remain, so automations can still change them.

Those alert controls move announcement text, Frigate stills, and live camera video. They do not move the microphone assistant. The starting values are **Top right** and **Medium**, which is the popup already used on the TV. Small is three quarters of that size and Large is one and a quarter. Choices are top left, top right, bottom left, bottom right, and center. Live video is fitted inside the frame, and stills fill it without being stretched, so a 16:9 picture stays 16:9.

Home Assistant layout must be turned on in the TV app. Change the selects, then trigger the next announcement or camera popup. An alert that is already on screen stays where it is. The popup does not take the remote or the program's audio; live camera playback stays muted.

The first time the TV sees a new control, it copies the layout already saved on the TV into Home Assistant. After that, changes in Home Assistant win. The on-TV menu still works and updates Home Assistant too.

If the app is closed, a layout change waits until the next time Kellie's Voice connects. Turning the switch off on the TV stops discovery and ignores later Home Assistant layout changes.

The device id used by the services below is the id stored for that TV. It is the same id the TV advertised when you clicked **Add**. It is not a Home Assistant token.

## Announcements and camera popups

These actions fire Home Assistant events. The signed Android app includes an opt-in TV event receiver. On the TV, enable **Settings → TV announcements & cameras** to display them. You can also test the Home Assistant events using Developer Tools before turning on the receiver.

Leaving **TV device id** empty sends one event to every TV you have added. Filling it in sends the event only to that TV. A TV that is not added here is not a target.

Limits, on every call:

- Title: 1 to 65 characters
- Announcement text: 1 to 500 characters
- Duration: 4 to 45 seconds
- Camera: a real `camera` entity already in Home Assistant, never a URL

Nothing in these actions is written to the log except the event name and how many TVs were targeted.

### Announce

Action: `kellies_voice.announce`

Event: `kellies_voice_announcement`

```yaml
action: kellies_voice.announce
data:
  title: Washer
  text: The washer is done.
  duration_seconds: 10
```

The event data is exactly:

```json
{
  "title": "Washer",
  "text": "The washer is done.",
  "duration_seconds": 10,
  "device_id": "the-configured-tv-id"
}
```

### Show a camera

Action: `kellies_voice.show_camera`

Event: `kellies_voice_camera_popup`

```yaml
action: kellies_voice.show_camera
data:
  entity_id: camera.front_door
  title: Front door
  duration_seconds: 20
```

The event data is exactly:

```json
{
  "entity_id": "camera.front_door",
  "title": "Front door",
  "duration_seconds": 20,
  "device_id": "the-configured-tv-id"
}
```

`entity_id` is a Home Assistant camera entity. The event does not include a snapshot URL, an RTSP address, or a token.

### Test both

1. Add at least one TV with the steps above.
2. Go to **Developer tools → Events**. Start listening for `kellies_voice_announcement`.
3. Go to **Developer tools → Actions**. Run `kellies_voice.announce` with a short title and text.
4. Confirm one event per configured TV. Each event has `title`, `text`, `duration_seconds`, and that TV's `device_id`.
5. Listen for `kellies_voice_camera_popup` and run `kellies_voice.show_camera` with a camera that already exists, such as your Frigate camera.
6. Run them again with a **TV device id** and confirm only that TV is in the event.
7. Try a duration of `120`, an empty title, or `entity_id: light.kitchen`. Home Assistant should reject the call and fire no event.

The TV displays the message or snapshot when its **TV announcements & cameras** receiver is enabled. The `show_camera` action displays a **still-image snapshot**, not a live stream. The separate experimental `show_live_camera` action requests HLS video on demand. Neither action starts Kellie's microphone or speaks the notification.

### Frigate person detection

Frigate's Home Assistant integration exposes a person sensor and a camera, for example `binary_sensor.front_door_person_occupancy` and `camera.front_door`. Use your own entity ids.

Copy [`blueprints/frigate_person_popup.yaml`](blueprints/frigate_person_popup.yaml) to `config/blueprints/automation/kellies_voice/frigate_person_popup.yaml`, then reload automations. Create an automation from **Kellie's Voice Frigate person popup** and choose the person sensor, the camera, the title, and how long the popup stays up. Leave the TV device id blank for every configured TV.

The blueprint waits after each popup, so a person standing at the door does not open the camera again on every detection. The camera entity is an input, not a hard-coded camera.

The same action without the blueprint:

```yaml
alias: Front door person
mode: single
triggers:
  - trigger: state
    entity_id: binary_sensor.front_door_person_occupancy
    to: "on"
actions:
  - action: kellies_voice.show_camera
    data:
      entity_id: camera.front_door
      title: Someone is at the front door
      duration_seconds: 20
  - delay:
      seconds: 90
```

Replace both entity ids with yours. Add `device_id` only when you want one TV.

## On-demand live camera viewing (experimental)

The `kellies_voice.show_live_camera` action also supports `show_header: false`, `image_fit: fit`, and `refresh_seconds: 2..30` to update the still preview until moving video starts. These are optional and default to the previous behavior.

The `kellies_voice.show_live_camera` action requests a **live HLS stream** from Home Assistant and displays a **muted, non-focusable overlay** on the TV. It does not cast away your movie or steal focus from YouTube. Default viewing time is 90 seconds (maximum 180). Person-detection alerts continue to use snapshots.

```yaml
action: kellies_voice.show_live_camera
data:
  entity_id: camera.driveway
  title: Live driveway
  duration_seconds: 90
```

For voice, expose an appropriately named script to your Assist/Ollama agent that calls the service with a fixed camera entity. Adding a new HA action alone does not guarantee your selected Assist conversation agent recognizes "show driveway camera". A sentence-triggered HA automation may be used as a fallback.

The TV receiver requests a temporary HLS URL through Home Assistant's authenticated WebSocket `camera/stream` command. Events never carry stream URLs, tokens, or RTSP credentials. Playback is muted and released on timeout, a new popup, or receiver shutdown. HLS has more delay than WebRTC. Both the v0.16.0 or newer HACS integration and the matching signed Android APK with Media3 HLS support are needed. Moving live video has been confirmed on the onn 4K Pro with `camera.driveway` and `camera.doorbell` using Android v0.16.1. Other TV models and cameras have not been field-tested.

## Close a live or snapshot camera on demand (v0.16.2)

Use `kellies_voice.hide_camera` to dismiss **only** a camera window without waiting for its scheduled timeout. This clears live video and releases Media3 playback on the TV; text announcements and the listening overlay are not touched. When `device_id` is omitted, the action targets every configured Kellie's Voice TV. No entity id or URL is needed.

```yaml
action: kellies_voice.hide_camera
data:
  device_id: "your-kellies-voice-tv-id"
```

For your Assist conversation agent (Ollama is optional), expose a Home Assistant script called **Close camera on TV** that calls the action. For “show doorbell for 30 seconds” either expose a parameterized camera script whose `duration_seconds` field is 30 or a specifically named 30-second script. The `kellies_voice.show_live_camera` action already supports integer durations between 4 and 180 seconds; voice model recognition of script arguments is not guaranteed until tested.

**Update both components to v0.16.2** and restart Home Assistant for the new action. The TV tries to resume an already enabled alert receiver after the APK update or a reboot. If the popups do not return, open Kellie's Voice once. No extra Home Assistant script is required for that resume.

### Set up the required Home Assistant scripts for voice requests

For the specific spoken phrases **"show me the driveway"**, **"show doorbell for 30 seconds"**, and **"close the camera"**, create named HA scripts that call `kellies_voice.show_live_camera` or `kellies_voice.hide_camera`. Expose each intended script to Home Assistant **Assist** under Settings → Voice assistants → Expose, and ensure the conversation agent can execute scripts. **No script is automatically installed by HACS**, since each installation has its own camera IDs, TV pairing IDs, and voice agent permissions.

The [complete step-by-step script guide with full YAML](../../docs/VOICE_CAMERA_SCRIPTS.md) shows camera parameters, fixed 30-second variants, close action, device targeting, and the included Frigate blueprint. Do not paste the demonstration camera IDs or TV pairing placeholder without adapting them to your own Home Assistant setup.

## If TV popups stop after an update, reboot, or Wi-Fi drop

This does not change the announcement, camera, or hide-camera actions. The TV app has to be the one that reconnects.

- Alerts stay off unless **Settings → TV announcements & cameras** was already turned on. Resume never starts the microphone.
- After an in-place APK update or a full reboot, Android may start the receiver again. That is allowed only while the system is delivering that boot or update broadcast, and some onn / Google TV builds still refuse it.
- Standby that leaves the app process running reconnects the Home Assistant socket with the existing backoff. A standby that kills the process does not send a boot broadcast, so Android may not start the receiver until the next reboot or until you open the app.
- Dropped Wi-Fi or a Home Assistant restart reconnects while the receiver is still running. Events from the gap are not replayed.
- If automatic start is blocked, open Kellie's Voice. **Settings → TV alert status** explains whether it is connected, reconnecting, missing overlay permission, signed out, or stopped, and **Resume TV alerts** starts it again. A one-time "Resume TV alerts" notification may also appear in the Android TV notification list. It is not a repeating nag.
- Revoking **Display over other apps** or signing out stops popups until those are restored. Disabling TV alerts keeps them off across reboots.

## Install v0.16.3 and configure alert placement

Use HACS to update the Kellie's Voice custom integration to **v0.16.3**, then restart Home Assistant. Independently install the **v0.16.3 Android TV APK** using the app's signed in-place updater. The TV's HA device page now has **TV alert position** (Top left, Top right, Bottom left, Bottom right, Center) and **TV alert size** (Small, Medium, Large). The current layout appears on the **next** camera or announcement popup; the main voice overlay is unaffected. The Android receiver may auto-resume after a reboot or app update if you had enabled it, but Android can prevent background starts. Check the TV's **Settings → TV alert status** when delivery stops. No new scripts are required for these features; the [complete script setup](../../docs/VOICE_CAMERA_SCRIPTS.md) remains valid for voice camera commands.
