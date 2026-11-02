# Optional actionable TV popup buttons (development preview)

**Status:** Button support is present in the released v0.17.3 Android and HACS code. The core one-use button behavior was **hardware verified on a Philips 55OLED856/12 OLED** using matching signed v0.17.4-dev.3 / HACS v0.17.4-dev.3 on 2026-11-02: D-pad/OK, exactly one event, replacement invalidation, Back/timeout, still/live camera buttons and non-focusable passive alerts passed. See [tester report](https://github.com/chadakenn/Kellies-Voice/issues/261#issuecomment-6581439136) and [remaining hardware checklist #304](https://github.com/chadakenn/Kellies-Voice/issues/304). **Not yet verified on that tester setup:** spoken audio ducking, microphone-related features, negative authorization cases, timer/reconnect regressions. Do not label the entire checklist complete until those are tested. Original implementation reference: [PR #61](https://github.com/chadakenn/Kellies-Voice/pull/61).

You can add up to **three optional focusable buttons** to a normal text
announcement, still camera or live camera. A popup without an `actions` list
is unchanged: it remains non-focusable, silent unless `speak` is set, and
never steals remote input. Alerts do not start the microphone.

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

**Current result:** Core button UX passed signed Philips OLED hardware tests on v0.17.4-dev.3. The remaining audio, mic, permission and reconnect acceptance criteria are tracked in [#304](https://github.com/chadakenn/Kellies-Voice/issues/304).
