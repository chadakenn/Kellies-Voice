# Frigate Recorded Event Replay | v0.18.0 design / first implementation slice

**Status:** Developer groundwork in [#307](https://github.com/chadakenn/Kellies-Voice/issues/307). **No public TV clip player, Home Assistant service, or automatic history fetching is shipped in this slice.**

This phase adds a pure selection policy (`frigate_replay.py`) and a narrowly scoped async adapter (`frigate_replay_client.py`). The adapter calls an **already authenticated Frigate integration API client supplied by Home Assistant** and gets a bounded set of recent events or one exact event ID, then applies the selector. They return at most ten metadata-only `ReplayEvent` instances for **one explicitly mapped Frigate camera** and never construct or distribute playback URLs. The caller must still verify the TV and HA camera permissions, find the correct Frigate integration config entry/client, and supply its configured camera inventory.

## Authorization gate (new)

`frigate_replay_access.py` now contains `authorize_replay_request`, a fail-closed **TV plus HA camera permission check** that returns only a `ReplayScope` for one explicitly permitted Frigate instance. Its caller must supply the actual Home Assistant authenticated WebSocket `connection.user` and the Home Assistant entity registry, not user-supplied identities. The gate requires:

- The TV is paired and the authenticated account may read its TV control entity, using the already deployed `_tv_can_read` permission rule.
- The same user has **read permission for the requested HA camera entity**, not merely access to the TV.
- The TV config entry owner has explicitly configured a `replay_camera_map` entry linking that HA camera entity to a specific Frigate config entry and camera name. The HA Options form now offers a single explicit Frigate camera mapping for each paired TV; the owner selects a registered Frigate HA camera entity and enters its actual Frigate camera name. The Frigate entry ID comes from HA's registry, not from the TV.
- The HA entity registry confirms that the camera entity **belongs to the expected Frigate config entry**, and that exact Frigate entry exists. A generic camera or a camera from a different Frigate installation fails closed.

No camera mapping is invented, and no Frigate URL or credentials are generated. The registered read-only `kellies_voice/replay_events` WebSocket command can now query scoped event metadata, but the TV app doesn't call it yet. All denial messages are generic to avoid disclosing other cameras or credentials.

## Owner setup and authorized event metadata

In **Home Assistant → Settings → Devices & services → Kellie's Voice TV → Configure**, choose **Replay: Home Assistant Frigate camera** from a dropdown restricted to camera entities owned by a loaded Frigate integration, then enter **Replay: exact camera name inside Frigate** (such as `front_door`). Both fields blank disables replay. Mappings are stored only in that TV's own HA options.

The scoped, read-only command `kellies_voice/replay_events` accepts `device_id`, `entity_id`, optional `event_id`, and `limit` (1–10). It verifies the authenticated user can read both that TV and that camera, that the mapped camera belongs to the declared Frigate config entry, and that the real Frigate instance config includes that camera. It queries Frigate using the **already authenticated HA Frigate client**, never a TV-supplied URL. Permissions are checked again after the async query.

It returns a camera entity ID and up to 10 event summaries containing only `event_id`, short `label`, `start_time`, and `end_time`. No thumbnail, clip URL, credential or raw Frigate JSON travels over this command. **This is development only; the Android TV player and event strip are not yet wired.**

## Why do this first?

A doorbell alert may arrive at the same moment as a person/car event on a nearby camera. **Watch Last Event must not quietly choose a different camera or an unrelated newer clip.** The selector requires an exact camera match, completed recording (`has_clip is True`, valid `end_time`), a non-false-positive event, and a bounded timestamp.

- If the triggering Frigate event ID is supplied, find **that exact event** or return an empty result. **Never fall back** to another event.
- Otherwise, only consider events for an explicitly configured camera and return the most recent completed recordings (newest first), at most eight by default.
- Allow only narrow Frigate event IDs, camera identifiers, and labels. Reject URL/path data, malformed timestamps, in-progress/missing clips, stale entries, oversized pages, and unknown cameras.
- Keep metadata transient. Do not include clip URLs, Frigate credentials, authentication headers, image thumbnails, arbitrary event JSON, or private camera access tokens.

## Confirmed Frigate API

Frigate 0.18 exposes `GET /api/events` with `camera`, `has_clip`, `limit`, `after`, and other filters, plus `GET /api/events/{event_id}` to validate a specific event and `GET /api/events/{event_id}/clip.mp4` to stream the recorded clip. The HA Frigate integration exposes notification clip routes, including `/api/frigate/notifications/{event_id}/clip.mp4`, and supports a `<client_id>` route namespace for multiple instances.

References:
- https://docs.frigate.video/integrations/api/events-events-get/
- https://docs.frigate.video/integrations/api/event-events-event-id-get/
- https://docs.frigate.video/integrations/api/event-clip-events-event-id-clip-mp-4-get
- https://docs.frigate.video/integrations/home-assistant/

**Security:** HA's Frigate notification media routes are documented as publicly available for notification apps. We **must not** put event IDs, sensitive media paths, or plaintext Frigate credentials into broadly exposed TV alert payloads. The final playback phase needs a scoped, authorized route or a clearly explained and owner-approved strategy to access Frigate media. Existing authenticated HA TV WebSocket messages are the control plane, **not** an automatic authorization grant to Frigate's entire archive. Do not bypass TLS or proxy arbitrary URLs.

## Required work to complete #307

1. **Home Assistant authorization integration:** The owner-controlled camera mapping, per-TV/camera/Frigate-instance gate, and read-only metadata WebSocket request are implemented. Still needed: live testing with the real Frigate integration/TV and Android UI request wiring. Multiple-camera configuration can follow the first verified mapping.
2. **Scoped request / action:** Bind `Watch Last Event` to the existing one-use, per-TV alert-action ledger. The server must authorize the paired TV and the mapped camera, and the action must refer to the correct notification/event. **Do not add an untrusted arbitrary video URL field** to existing `kellies_voice.announce` or camera services.
3. **Private playback:** Play only the verified clip in a TV video player with a same-origin/approved-host URL policy, muted by default. Use remote Back to return to the previous TV app, not an injected overlay that steals focus in passive mode. Handle 403/404, expired retention, missing clips, timeouts, cancellation and codec failures gracefully.
4. **Recent-event strip:** Show up to 8 recent results for the same authorized camera, with time, short label and bounded thumbnails only after explicit user selection. Keep access limited to that paired TV. The strip should not accidentally show recordings from other cameras.
5. **QA:** Home Assistant integration tests including multiple TVs and Frigate instances, then signed Onn 4K Pro testing. Verify correct doorbell event, wrong-camera rejection, missing clip, Frigate offline, Back, mute/audio preference, screensaver camera and regular notification regression.

## Calling the selector (internal only)

```python
from custom_components.kellies_voice.frigate_replay import recent_recorded_events

# events must come from an authenticated, restricted Frigate client.
result = recent_recorded_events(
    events,
    frigate_camera="doorbell",   # explicitly mapped, never guessed from entity_id
    now=clock.time(),
    requested_event_id="1730571482.487155-abc123",
)
# No match => [] (not "try the latest other event").
```

The authenticated HA WebSocket metadata lookup exists, but the TV app does not yet issue that request. There is still **no clip playback, video player, or media URL** in this first slice.
