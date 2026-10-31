# Android KVPair v3-only temporary LAN listener (draft QA)

This is the next isolated piece after #244's ordered wire states. The
Android app is still **not wired** to start this listener from the first-run
screen and Home Assistant's existing config-flow has NOT been switched to
v3. It is NOT a finished pairing APK or a token transport.

## Implemented adapter

`PairingV3LanWindow.start(context, onCode, onExpired)` must be called
only after a user explicitly presses START on a foreground TV pairing
screen. It generates a fresh 16-byte session and ephemeral P-256 key pair,
allocates an actual ephemeral TCP port, and advertises
`_kellies-pair._tcp.local.` with `protocol=3`,
`mode=verified-code-only` and the session.

Only **four** exact request routes are handled (see
`docs/PAIRING_V3_ORDERED_WIRE_QA.md`):

- POST /v3/pair/commit: receive 32-byte HA commitment and return TV commit
- POST /v3/pair/reveal-ha: verify HA key+nonce against saved commitment
- GET /v3/pair/reveal-tv: return TV key+nonce once, then show verified code
- GET /v3/pair/status: false until physical TV remote approves; then the
  v3 MAC plus canonical TV UUID

HTTP is only for this *token-free public-key verification*. The listener
never receives a HA username, URL, token, OAuth code or credential.

The v1/v2 endpoints are **not exposed**; unknown routes return 404.
The JSON parser checks the exact protocol integer 3, session, field
count and ASCII unpadded Base64URL encoding. All requests have bounded
lines/headers/body sizes, read/accept timeouts, a limit of 80 requests,
and a 120-second monotonic lifetime. No redirects, proxies, long-lived
service or automatic boot listener.

`approveFromPhysicalRemote()` is the **only** way this adapter can
emit TV-OK proof. It must be invoked only from the TV remote UI and only
after the user compares the freshly displayed code.

## What remains before actual onn TV testing

1. Wire this class to `PairingSetupActivity` with a visible Start button,
   large code, focused TV remote OK and a separate Back/Cancel/Manual
   Login action. The activity must close the socket onStop/onDestroy.
2. Add HA discovery targeting `protocol=3` and connect
   `HaV3WireAttempt` from #244 to an authenticated owner-initiated
   config flow. Never automatically fall back to v2 for credentials.
3. Real onn + HA integration test of the same live code, remote proof,
   wrong version, replay, timeout, background, HA restart and two TVs.
4. Reconcile the owner's signed-in approval modal (#241), distinct
   genuinely limited TV account (#239), durable token lease (#240) and
   v3 encrypted handoff into Android Keystore. These are release blockers.
5. Get independent security review before public v0.17.0.

**No existing app route, camera, microphone, Home Assistant integration,
HACS install, saved login, release key or main branch is changed.**
