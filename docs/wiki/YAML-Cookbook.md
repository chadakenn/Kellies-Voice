# YAML Cookbook

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

Copy-and-paste Home Assistant examples for Kellie's Voice. All snippets use safe placeholders (`YOUR_TV_PAIRING_ID`, `camera.example_driveway`, `weather.home`, etc.). Replace them with your own entity IDs and the pairing ID shown on the TV under **Settings → Home Assistant layout → Show this TV's pairing ID**.

**Where to put each snippet:**

- **Developer tools → Actions** — paste the `action:` block and run it immediately for testing.
- **Scripts** — create a script (Settings → Automations & scenes → Scripts → Create script → Edit in YAML), paste a complete script starting with `alias:`, then expose it to Assist if you want voice control.
- **Automations** — paste into the automation YAML editor.
- **Custom sentences / intent_script** — advanced; see the timer section and the repository's VOICE_TIMERS.md. These are optional and not required for the direct actions to work.

**Current public release:** v0.17.0. Camera popups, the Frigate/HA screensaver and Home Assistant actions are available with the matching signed TV app and `kellies_voice` HACS integration. Some advanced actions and camera behavior depend on supported hardware and opted-in settings.

## Announcements

**Where:** Developer tools → Actions (or an automation/script).

```yaml
action: kellies_voice.announce
data:
  title: Laundry
  text: The washer has finished.
  duration_seconds: 10
  # Optional: device_id: YOUR_TV_PAIRING_ID   # leave out to send to all TVs
```

**Expected result:** a short text popup appears on the TV(s).  

**Optional in v0.16.11:** Enable **Settings → Spoken announcements** on the TV and add `speak: The washer is finished.` to the announcement above. Omit `speak` to keep the TV alert silent. The speech volume can be adjusted per TV.
**Troubleshooting:** confirm **TV announcements & cameras** is ON and the alert receiver is connected (see [Resume TV alerts](Troubleshooting.md#resume-tv-alerts-exact-steps)).

## Snapshot camera

**Where:** Developer tools → Actions.

```yaml
action: kellies_voice.show_camera
data:
  entity_id: camera.example_driveway
  title: Driveway snapshot
  duration_seconds: 15
  # Optional (v0.16.5+):
  # show_header: false
  # image_fit: fit
  # refresh_seconds: 3
```

**Optional in v0.16.11:** Add `speak: Someone is at the door.` to `show_camera` or `show_live_camera`. The sentence is spoken with the same popup when spoken announcements are enabled on the TV; the default remains quiet.

**Expected result:** a still image popup. `image_fit: fit` shows the whole frame; `refresh_seconds` updates the still periodically (not live video).

## Live camera (experimental HLS)

**Where:** Developer tools → Actions. Verified moving video on onn 4K Pro with driveway and doorbell cameras (v0.16.1+). Other cameras/devices need testing.

```yaml
action: kellies_voice.show_live_camera
data:
  entity_id: camera.example_driveway
  title: Live driveway
  duration_seconds: 30
  # Optional (v0.16.7+):
  # show_header: false
  # image_fit: fit
  # refresh_seconds: 3   # refreshes the loading snapshot only
```

Short durations (as low as 4 seconds) are allowed, but a slow HLS start may close before moving frames appear. Enabling Home Assistant's **Preload camera stream** on the camera entity can help startup at the cost of extra resources.

## Hide camera

**Where:** Developer tools → Actions (or an Assist-exposed script).

```yaml
action: kellies_voice.hide_camera
data:
  # Optional: device_id: YOUR_TV_PAIRING_ID   # leave out for all TVs
```

Closes a live or snapshot camera popup immediately. Does not affect announcements or the voice overlay.

## Frigate person automation / blueprint

The repository includes a ready blueprint: `custom_components/kellies_voice/blueprints/frigate_person_popup.yaml`.

Copy it to `config/blueprints/automation/kellies_voice/`, reload automations, then create an automation from **Kellie's Voice Frigate person popup**.

Manual example automation (replace entities):

```yaml
alias: Person at door — TV snapshot
mode: single
triggers:
  - trigger: state
    entity_id: binary_sensor.example_door_person_occupancy
    to: "on"
actions:
  - action: kellies_voice.show_camera
    data:
      entity_id: camera.example_doorbell
      title: Someone is at the door
      duration_seconds: 20
  - delay:
      seconds: 90
```

This shows a **still snapshot**, not live video.

## Seven-day weather forecast

**Where:** Developer tools → Actions for testing, or a Script exposed to Assist.

```yaml
action: kellies_voice.show_forecast
data:
  entity_id: weather.home
  # Optional: duration_seconds: 20
  # Optional: device_id: YOUR_TV_PAIRING_ID
```

Requires a weather entity that supports daily forecasts (Home Assistant 2024.4+). The card is not a camera and does not start the microphone.

Script example (create as a Script and expose to Assist):

```yaml
alias: Seven day forecast on TV
description: Show the next seven days of weather on Kellie's Voice.
mode: single
sequence:
  - action: kellies_voice.show_forecast
    data:
      entity_id: weather.home
```

## Timers

See also [Voice and Timers](Voice-and-Timers.md).

**Start** (required `device_id` and `duration_seconds`):

```yaml
action: kellies_voice.start_timer
data:
  device_id: YOUR_TV_PAIRING_ID
  name: Pizza
  duration_seconds: 300
  speak_alert: true
  alert_phrase: Your pizza timer is finished.
```

**Cancel** and **dismiss** examples are on the [Voice and Timers](Voice-and-Timers.md) page.

**Honest note on spoken timers:** native Home Assistant Assist may reply "this device doesn't support timers" because the generic timer intent is not automatically mapped to Kellie's Voice. Direct actions above work today. Optional custom sentence files or home-specific automations can route speech, but they are extra setup and must be scoped carefully if you have other Assist satellites. Do not claim timers work with no additional configuration.

## Multiple TVs

- Leave `device_id` empty (or omit it) on announce / camera / forecast actions to target **all** configured Kellie's Voice TVs.
- Set `device_id: YOUR_TV_PAIRING_ID` to target one specific TV.
- Timer actions always require a `device_id` and are never broadcast.

Use the pairing ID from the TV Settings screen, never a Home Assistant device-registry ID.

## Overlay style example (device page select)

```yaml
action: select.select_option
target:
  entity_id: select.your_tv_overlay_style
data:
  option: Aurora Glass
```

Replace the entity ID with the one on your Kellie's Voice device page. The four shipped themes are Copper Glass, Compact Charcoal, Night Signal, and Aurora Glass.

## Validation notes

The core examples were originally checked against `custom_components/kellies_voice/services.yaml` for v0.16.9 and the optional speech notes have been updated for v0.16.11. Confirm supported fields against your installed HACS version. Required fields are marked. Placeholders avoid any personal credentials, tokens, or real IPs. Test each action from Developer tools before relying on voice or automations.

---

[← All documentation](Home.md) · [Next: Troubleshooting →](Troubleshooting.md)
