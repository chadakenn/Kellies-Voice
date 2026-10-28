# Voice and Timers

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

## Voice conversation

**New in v0.16.14:** The optional **Okay Nabu** local wake-word listener lives under **SETTINGS → Voice & microphone → Wake word** and is OFF by default. It can open the voice overlay without pressing TALK when Android exposes a suitable microphone. [See privacy behavior, reboot limitations and the owner-tested video](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/WAKE_WORD.md).


Activate **TALK** on the TV and speak a natural question. Kellie's Voice sends the audio to your configured Home Assistant Assist pipeline and displays (and optionally speaks) the answer in a floating overlay. You do **not** need to say "Julia".

The Google Assistant button on the remote is not intercepted. The app checks for usable audio inputs, but microphone detection is still unreliable on some Philips TVs (issue #71), including subsequent releases (see [issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71)); they may incorrectly show Listening and then receive no recognized speech. A USB microphone may be required.

## Optional follow-up listening (v0.16.11)

Use TV **Settings → Follow-up listening** to choose OFF (default) or ON with 5/8/10/15 seconds of listening after a *spoken answer* finishes. A real onn 4K Pro test confirmed a second question hands-free, 8-second timeout with the Android mic indicator clearing, and STOP cancellation. Other TVs and Back/Close/disconnection cases are not yet hardware verified. The HA Follow-up silence control adjusts time only; it cannot enable recording without the TV setting.

## Named TV timers (v0.16.11)

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

You can create Assist-exposed scripts or custom sentence triggers that call the actions above. See the full examples and honest caveats in the [YAML Cookbook](YAML-Cookbook.md#timers) and the repository's [docs/VOICE_TIMERS.md](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/VOICE_TIMERS.md).

The maintainer has confirmed voice start/stop on an onn 4K Pro *after* adding home-specific automations. This is not automatic for new installs.

**Optional native Assist timer bridge in HACS v0.16.11:** OFF by default. Direct TV timer actions and user-configured scripts remain supported. The native bridge still needs physical QA for #57; don't expect generic timers to work without enabling/configuring it deliberately.

## Next

- [YAML Cookbook](YAML-Cookbook.md)
- [Troubleshooting](Troubleshooting.md)

---

[← All documentation](Home.md) · [Next: YAML Cookbook →](YAML-Cookbook.md)
