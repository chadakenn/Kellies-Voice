# Home Assistant TV diagnostics (development candidate)

Kellie's Voice already provides a per-TV **TV alert receiver connected**
binary sensor. It means Home Assistant has an active authenticated,
read-scoped alert WebSocket subscription. It does **not** mean an alert
was displayed, the Android app is foregrounded, the HDMI output is visible,
or a live camera rendered frames.

This candidate adds two **Diagnostic** sensor entities to each paired TV
under **Settings → Devices & services → Kellie's Voice → [TV]**:

| Diagnostic | Meaning |
| --- | --- |
| **TV alert receiver connected** (existing) | An authenticated receiver socket is subscribed now, or disconnected; offline TVs may resume after Android/HA restarts |
| **TV app version (reported)** | Last versionCode/versionName reported over that scoped socket since this HA startup; **Unknown** until a compatible TV connects |
| **TV last popup receipt** | **Never confirmed**, **Received by TV**, or **Popup created on TV**, with timestamps since this HA startup |

The new Android client sends a version report **only after HA authorizes its
per-TV alert subscription**. Home Assistant attaches a random short-lived,
single-use receipt to each scoped announcement/snapshot/live-camera/forecast
notification. The TV reports (1) when the event reached its handler and
(2) when Android accepted popup/window creation (`card != null`). This is
honest progress telemetry, **not** proof the viewer saw a pixel or a video
decoder played a frame. Speech playback and action-button presses are not
recorded as delivered.

Receipts may be submitted **only on the active, authenticated socket** for
the paired TV, with the original random receipt still pending and an intact
read permission. They expire in two minutes, limit to 16 outstanding per
subscription, and are discarded when the socket closes. Neither the HA
sensor attributes nor its logs contain pairing IDs, announcement body,
camera names/URLs, microphone audio, tokens, or the receipt values. Reports
are retained in HA **memory only**, since the last Home Assistant restart.
No permanent tracking database and no remote microphone controls.

**Troubleshooting example:** if "receiver connected" is ON but "TV last
popup receipt" remains "Never confirmed", first use HA's
`kellies_voice.announce` to show a test notice. If it changes to
**Received by TV** but not **Popup created on TV**, check the TV's
**Display over other apps** permission and **TV alert status**. If it says
**Popup created on TV** but nothing appears, inspect Android overlay,
screen/sleep state, and HDMI app behavior; the system cannot certify that
a human saw the popup. A legacy Android/HACS pairing will show the
existing receiver state but will **not** report new receipts.

### QA before any stable release

- [ ] Install a signed development TV APK and matching HACS component; restart HA.
- [ ] With two paired TVs, confirm each only reports its own app version and
      received/popup-created timestamps; manual typo-paired TV stays unverified.
- [ ] Disable the receiver or revoke HA read permission: diagnostic reports
      must stop, not silently downgrade to a broad admin-only socket.
- [ ] Send a test announcement while the TV is watching video. Verify a
      distinct **Received by TV** then **Popup created on TV**.
- [ ] Deny overlay permission or force the popup creation to fail: it may
      report **Received**, but must not falsely report popup creation.
- [ ] Restart HA, then reconnect the TV: old report timestamps must be gone
      and no popup receipt is accepted from the previous socket.
- [ ] Test snapshots, live camera card (no claim of video frame), forecast,
      partial network loss, timer, and hide-camera. Only genuine popup cards
      should produce a popup-created receipt.
- [ ] Inspect Home Assistant entity attributes and logs to ensure no
      private household data appears.
- [ ] Confirm older stable TV builds keep working without new telemetry.
- [ ] Keep **stable `main` unchanged** until all real-TV checks are passed.

The new diagnostic component is intentionally separate from #144's
network/layout synchronization changes, #145's signature/provenance hardening,
and #146's reconnection changes. Rebase and rerun CI after those merges.
