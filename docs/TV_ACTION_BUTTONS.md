# Optional actionable TV popup buttons (development preview)

**Status:** Draft, not in the stable v0.16.11 release. This feature is being
ported from [PR #61](https://github.com/chadakenn/Kellies-Voice/pull/61)
to current development code and must pass signed onn 4K Pro hardware testing.

You can add up to **three optional focusable buttons** to a normal text
announcement, still camera or live camera. A popup without an `actions` list
is unchanged: it remains non-focusable, silent unless `speak` is set, and
never steals remote input. The weather forecast card does take focus, so Back can close it. A focused card may pause the app underneath. That has not been tested on YouTube, Netflix, or Plex. Alerts do not start the microphone.

## Home Assistant action example

Create a **harmless acknowledge** button before attempting any other workflow:

```yaml
action: kellies_voice.announce
data:
  title: Laundry
  text: The washer finished.
  device_id: YOUR_TV_PAIRING_ID
  duration_seconds: 20
  actions:
    - id: acknowledge
      label: Got it
```

The user must select **Got it** using the TV remote. The resulting,
individually authenticated HA event has type
`kellies_voice_announcement_action`, with only
`device_id`, `announcement_id` (opaque per-popup 32-hex nonce) and
`action: acknowledge`. No Home Assistant service runs by default.

Each action is bound to a specific HA-issued alert/TV, expires with the
popup, and can be claimed **once**. HA checks the signed-in user's read access
to this configured TV before accepting a button press. A replacement alert or
a hide-camera command invalidates the previous buttons. Old HACS installations
without `kellies_voice/press_alert_action` cannot process button presses;
install both components from the same development build.

**Security:** Never wire a general-purpose `unlock`/garage action directly
to this event during community tests. HA automations responding to an event
are authored by the HA owner and are not automatic access authorization.
For sensitive operations, implement explicit authorization/confirmation
requirements in the HA automation itself. Do not expose access tokens,
camera URLs, or pairing IDs in public screenshots.

## TV acceptance checks

- Harmless button on a text alert is visible and selectable with D-pad/OK.
- Press OK repeatedly: exactly **one** HA event. Another popup can send a new event.
- Press Back or let the popup timeout: **zero** button events.
- Replace button popup with a new alert: old IDs cannot be used again.
- Test button on still and live camera, while video remains muted.
- Without buttons: TV playback retains the remote focus; no focus steal.
- Spoken alert volume and TTS duck/restore still behave as v0.16.11.
- A non-admin TV token with normal per-TV read permissions works through
  scoped WebSocket; wrong/offline TV IDs or unauthorized users are refused.
- Mic follow-up, timer dismiss and Wi-Fi reconnect still work.

This port remains a **draft** until these tests pass on real hardware.
