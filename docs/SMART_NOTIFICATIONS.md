# Smart Notification Center: v0.18.0 first slice

**Development only. Not included in v0.17.4 stable.** This first PR implements notification priority, opt-in quiet hours and session-only history. Frigate recorded-event playback and the event thumbnail strip are tracked in [#307](https://github.com/chadakenn/Kellies-Voice/issues/307); they are **not implemented** by this PR.

## Priorities

The **Home Assistant actions** `kellies_voice.announce`, `show_camera`, and `show_live_camera` accept optional `priority: low | normal | high`.

- **low**: small popup, silent even if `speak` is supplied, and does not take remote focus unless it has an explicit user-action button. Suitable for ordinary movement detection.
- **normal** (default): same TV alert size, focus and speech behavior as existing v0.17.4 automations. No YAML migration.
- **high**: large popup; optional spoken announcement still requires TV spoken-alerts enabled; bypasses *opt-in* quiet hours. Suitable for intentional doorbell alerts.

Unknown priority values are rejected by the HA service schema and the scoped event sanitizer. Do not send strings from arbitrary automation data without validation.

### Low motion alert

```yaml
action: kellies_voice.announce
data:
  device_id: YOUR_TV_PAIRING_ID
  title: Driveway motion
  text: Motion detected outside.
  duration_seconds: 8
  priority: low
```

### High-priority live doorbell, with a one-use acknowledgement button

```yaml
action: kellies_voice.show_live_camera
data:
  device_id: YOUR_TV_PAIRING_ID
  entity_id: camera.doorbell
  title: Front door
  duration_seconds: 30
  priority: high
  actions:
    - id: dismiss
      label: Dismiss
```

This button sends the existing authenticated `kellies_voice_announcement_action` event; it **does not** invoke a HA service or unlock anything.

## Quiet hours on one TV

Open **TV Settings → Alerts & cameras → Smart notification quiet hours**. It is **OFF by default**. Enable it and choose one local-TV-clock window: 10 PM–7 AM, 11 PM–6 AM, or midnight–7 AM. Start is inclusive, end exclusive; the logic handles overnight hours.

During this window, low and normal alerts are suppressed on that TV. High-priority alerts still display (subject to TV alert enablement/permissions). Quiet hours do **not** alter Home Assistant/Frigate recordings or network requests. Other TVs use their own preferences.

## Session history

From **TV Settings → Alerts & cameras → Recent notifications (this app session)**, view up to the last 30 notifications (24-hour maximum) by time, short title, and priority, including messages suppressed by quiet hours. Tap **Clear** to forget them. This is **RAM only**, so the list clears when Android kills the app process. No event IDs, URLs, credentials, camera frames, text bodies or clips are recorded.

## TV QA gates

- [ ] Install matching dev HACS + signed dev TV build and test low/normal/high.
- [ ] Preview existing v0.17.4 default alert unchanged.
- [ ] Low alert silent and small, passive alert does not steal remote focus.
- [ ] High live camera uses large popup, video muted, D-pad dismissal single-use and Back works.
- [ ] Quiet hours OFF default; enable/preset; test start/end, midnight crossing and high bypass.
- [ ] History shows displayed and suppressed alerts, Clear, limit and process restart.
- [ ] HA restart, wake word, timers, 4-camera screensaver and YouTube ducking unchanged.

No Frigate replay is claimed until [#307](https://github.com/chadakenn/Kellies-Voice/issues/307) is implemented and hardware-tested.
