# Voice and Timers

## Voice conversation

Activate **TALK** on the TV and speak a natural question. Kellie's Voice sends the audio to your configured Home Assistant Assist pipeline and displays (and optionally speaks) the answer in a floating overlay. You do **not** need to say "Julia".

The Google Assistant button on the remote is not intercepted. The app checks for usable audio inputs, but microphone detection is still unreliable on some Philips TVs in v0.16.8 (see [issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71)); they may incorrectly show Listening and then receive no recognized speech. A USB microphone may be required.

## Named TV timers (v0.16.8)

Kellie's Voice can show small countdown pills on the TV. When a timer finishes it plays repeating chimes (and an optional spoken phrase) until you dismiss it with **Back** or the on-screen Dismiss button.

**Direct Home Assistant actions work today.** Spoken timer commands do **not** work automatically with Home Assistant's native timer intent. If you say "set a five-minute pizza timer" and hear "this device doesn't support timers", the native Assist timer handler was used instead of Kellie's Voice. Extra Home Assistant configuration (scripts or carefully scoped sentence triggers) is required for voice routing. Do not assume timers work with zero setup.

### Direct actions (Developer tools → Actions)

**Start a timer** (required: `device_id` = TV pairing ID, `duration_seconds`):

```yaml
action: kellies_voice.start_timer
data:
  device_id: YOUR_TV_PAIRING_ID
  name: Pizza
  duration_seconds: 300
  speak_alert: false
  # Optional: alert_phrase: "Your pizza is ready."
```

**Cancel a named timer:**

```yaml
action: kellies_voice.cancel_timer
data:
  device_id: YOUR_TV_PAIRING_ID
  name: Pizza
```

**Dismiss all ringing timers** (leaves running timers alone):

```yaml
action: kellies_voice.dismiss_timers
data:
  device_id: YOUR_TV_PAIRING_ID
```

Timers are never broadcast by default — you must target a specific pairing ID.

### Optional voice routing

You can create Assist-exposed scripts or custom sentence triggers that call the actions above. See the full examples and honest caveats in the [YAML Cookbook](YAML-Cookbook#timers) and the repository's [docs/VOICE_TIMERS.md](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/VOICE_TIMERS.md).

The maintainer has confirmed voice start/stop on an onn 4K Pro *after* adding home-specific automations. This is not automatic for new installs.

## Next

- [YAML Cookbook](YAML-Cookbook)
- [Troubleshooting](Troubleshooting)
