# Home Assistant TV announcements and camera popups

This is an opt-in Android TV feature. It does **not** require installing the
custom Home Assistant integration to show a **local announcement preview**.
Live Home Assistant events require HA configuration in a later step.

## TV configuration

**If TV alerts/timers stopped after a TV wake, HA restart or update:** On the
Kellie's Voice **main screen**, select **SETTINGS → TV alert status → Resume
TV alerts**. Scroll down to **TV alert status** within the main SETTINGS
menu. **Resume TV alerts** is the button in the status dialog; it is *not*
inside **TV announcements & cameras**. If missing, enable the latter first,
configure Home Assistant, and allow Android's **Display over other apps**
permission. The TV alert receiver is separate from Assist's Connect /
reconnect button. Check the HA **TV alert receiver connected** diagnostic
and then send a new test event. See
[troubleshooting](TROUBLESHOOTING.md#tv-camera-or-announcement-popups-missing-find-resume-tv-alerts).


1. Install the current signed Android APK (v0.16.9 at this documentation update) over the existing app (keep saved settings). Install the matching HACS Kellie's Voice integration and restart Home Assistant.
2. In Kellie's Voice, open **Settings → TV announcements & cameras**.
3. Turn it on. Android may require **Display over other apps** permission.
4. Choose **Preview announcement** for a local test popup. It does
   **not** enable the microphone, pause/mute YouTube, or steal D-pad focus.
5. To disable, turn the same switch off or use **Disable alerts** from the
   persistent notification.

The opt-in switch defaults to **OFF**. When enabled, the alert receiver uses
its own authenticated Home Assistant WebSocket connection and, with matching
v0.16.5+ Android/HACS, a **TV-scoped alert subscription** for announcements,
camera snapshots/live/hide, forecasts, and timers. This connection is separate
from voice/Assist. The app attempts to recover it after normal network losses,
updates, and restarts. Starting with v0.16.9, fix #86 distinguishes
Home Assistant's TV controls still loading from actual access denial and
retries the first condition using bounded backoff; genuine read denials still
block event delivery. Android TV may block background starts, so it is **not**
a guaranteed 24/7 listener. Open the app or use **TV alert status → Resume TV
alerts** if it remains disconnected. Events missed during outages are not
replayed.

**Public v0.16.4:** the receiver uses HA custom-event subscriptions. Home
Assistant restricts these to administrators. If using v0.16.4, the safe
workaround is to use other notification methods or restrict its TV token
carefully; do not deploy a full HA administrator token on an untrusted TV
simply to work around missing permissions.

**Released and field-verified in v0.16.5 (#68):** with matching Android APK **and** HACS integration,
the TV requests the new `kellies_voice/subscribe_alerts` command on its existing
authenticated WebSocket. The HACS integration checks that the requesting account
can read the **paired TV's "TV alert position" select entity**; a valid pairing ID
alone is insufficient. It sends sanitized, per-device notifications only, and
re-checks authorization before every event. No event payload is stored as a
Home Assistant sensor state/history. A rejected request appears as a permissions
error in TV alert status, not an endless "Connecting" message. Existing event
bus automations and the administrator-only legacy subscription remain supported
for older combinations of app and HACS. Always use the least-privileged token
that works, keep it private and never upload it to GitHub.

The Philips 65PUS8507/12 community tester verified non-admin alert delivery
and targeted-TV isolation on HA 2026.10.2 with v0.16.5. Issue #68 is closed.
Fine-grained permission-revocation behavior was covered by automated tests
but **not** demonstrated on a live non-admin HA account; please report any
security or revocation issue separately.

## Did Home Assistant actually reach the TV? (issue #69)

The current HACS integration exposes a diagnostic binary sensor **TV alert
receiver connected** under each paired TV. It only turns ON when the
authenticated, read-authorized TV alert WebSocket subscription is active.
`last_seen_since_ha_start` shows the latest successful connection during this
Home Assistant run; `never` indicates that HA has not seen one since startup.
This is *not* a popup-delivery acknowledgment, and it is not a status for the
voice/Assist WebSocket. Older Android releases using the legacy event channel
will correctly show OFF or `never`.

An incorrect but valid-format pairing ID is accepted in manual setup to allow
offline/VLAN-separated pairing; the new sensor exposes that it has never
connected. Home Assistant's 'Action ran OK' means the event was **published**,
not that a TV displayed it. We do not promise delivery while an alert receiver
is disconnected or replay missed events. Avoid rejecting all offline TVs: this
would break intentional offline automations and legacy recipients.

This diagnostic is available in v0.16.5+ with matching Android and HACS
versions. A manually entered TV pairing ID is **not** the HA device-registry ID.
A connected receiver confirms a subscription, not that a particular popup was
rendered; issue #69 remains open for wrong-ID scenario testing.

## Event contract

The companion Home Assistant integration can implement the
`kellies_voice.announce` and `kellies_voice.show_camera` services. A seven-day forecast is a separate on-demand action, documented in [WEATHER_FORECAST.md](WEATHER_FORECAST.md). The
Android side listens for the following raw **Home Assistant event bus** events:

### Visual announcement (text only in phase 1)

- Event type: `kellies_voice_announcement`
- Data:
  - `title`: optional title, up to 65 characters
  - `text`: required non-empty message, up to 500 characters
  - `duration_seconds`: optional integer, clamped to 4–45 seconds (default 10)
  - `device_id`: optional persisted Kellie's Voice device ID; omit for all TVs

Sample Developer Tools → Events action:

```yaml
event_type: kellies_voice_announcement
event_data:
  title: Laundry
  text: The washer has finished.
  duration_seconds: 9
```

### Camera snapshot popup

- Event type: `kellies_voice_camera_popup`
- Data:
  - `entity_id`: required Home Assistant entity such as `camera.driveway`
  - `title`: optional, up to 65 characters
  - `duration_seconds`: optional 4–45 seconds (default 10)
  - `device_id`: optional, omit for all TVs

Sample:

```yaml
event_type: kellies_voice_camera_popup
event_data:
  entity_id: camera.driveway
  title: Person at the driveway
  duration_seconds: 12
```

The TV fetches a **single still image** through the authenticated Home
Assistant `/api/camera_proxy/camera.driveway` endpoint. The separate experimental `show_live_camera` action can request a temporary HLS video stream; this snapshot event itself never streams video. Spoken TTS announcements are not implemented as part of these visual alerts. Images only load from the configured Home Assistant host; event data
cannot provide a URL. The card is a touch- and focus-transparent Android
overlay, so video apps and remote controls remain usable.

**Note:** Some Frigate entities may not expose a usable Home Assistant
camera snapshot. Use an HA `camera.*` entity that supports `camera_proxy`,
or the popup will show its text without an image.

### Seven-day forecast

- Event type: `kellies_voice_forecast`
- Fired only by `kellies_voice.show_forecast` after a successful `weather.get_forecasts` call
- Data is a cleaned location label, `°C` or `°F`, 1 to 7 days, and a duration from 8 to 45 seconds
- The TV ignores a forecast older than two minutes and will not cover a camera that is already showing

Do not put a weather URL or a token in this event. Setup steps and the Assist script are in [WEATHER_FORECAST.md](WEATHER_FORECAST.md).

## Developer test checklist

- Enabling the receiver never requests recording permission or starts audio.
- With YouTube playing, use Preview: popup appears and auto-dismisses.
- Disabling receiver removes foreground notification and stops its socket.
- HA fires announcement event: intended TV shows a card; other target TVs ignore it.
- HA fires camera event: valid camera snapshot appears without a second hostname.
- Disable Wi-Fi / restart HA: the event receiver reconnects when available.
- Unsupported entity names and oversized text are rejected or safely bounded.
- Verify Android 14+ special-use foreground-service behavior on the onn 4K Pro.

This feature is intentionally separate from Home Assistant layout control.
The latter is still optional and keeps its own setting.

## On-demand experimental live camera

The matching v0.16.0 Android TV app and HACS integration also support a third, opt-in event `kellies_voice_live_camera` via action `kellies_voice.show_live_camera` with `entity_id`, optional `title`, `duration_seconds` (15–180, default 90), and optional `device_id`. Only Home Assistant camera entities supporting streaming are accepted. The TV requests a short-lived HLS URL from Home Assistant over its own authenticated WebSocket and plays the video muted. This is **not yet verified on the actual onn 4K Pro**, and live feed performance can be device/camera dependent. The event never contains untrusted stream URLs or secrets.

## Live camera black screen follow-up (v0.16.1)

This patch switches video compositing to an Android `TextureView` inside the overlay instead of the default Media3 `SurfaceView`, which can be invisible on some TV overlay windows. It also loads a clearly labeled snapshot as fallback while HLS is preparing or when playback fails. The small status label shows the **Home Assistant stream error code** or **Media3 playback error code** without printing signed streaming URLs or access tokens. No new permissions, ports or stream-host allowlists are added.

When testing, use `kellies_voice.show_live_camera`. A fallback still image is **not proof** that the live stream works. Confirm moving frames and the status `LIVE · muted` before declaring success. If it fails, report the exact visible code (e.g. `Live playback error 2004`) and whether the fallback snapshot loaded.
