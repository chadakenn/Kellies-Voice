# KVPair/v3 ordered LAN handshake contract (security QA, #214)

**Status:** Standalone, token-free, main unaffected. This branch is stacked
on #243 and does NOT modify either Home Assistant's installed config flow
or the working Android TV listener. No real credentials are available.

## What we have now

- Android `PairingV3TvWireState` enforces one session with **FRESH →
  COMMITTED → HA_VERIFIED → REVEALED → physical TV APPROVED**.
- Home Assistant `HaV3WireAttempt` serializes exactly these messages,
  parses bounded, version-3-only responses and checks cryptographic proof.
- `AiohttpV3LanTransport` can perform real HTTP requests to a literal,
  private LAN IPv4 address, with a 3-second timeout, no redirects, no
  proxy, no cookies and maximum 1 KiB response. It is not invoked by
  the current config flow.
- Tests use the real P-256 Java and Python primitives, not string mocks.
  Shared fixed vector: v3 code **83428994** and remote proof
  **TuUIMiT-zkUz3XaKygd5Gw**.
- No account credential, OAuth code, bearer token, URL saved on Android,
  microphone, wake word, Home Assistant config entry, user ID or role
  is sent over this pairing link.

## Exact proposed transport contract

The TV advertises an ephemeral local v3-only port ONLY after the person
presses Start on the TV. It must use the exact 16-byte session represented
as canonical 22-character unpadded Base64URL. The network adapter must
not advertise `protocol=1`, serve `/v1/pair/offer`, or allow downgrade.

All JSON messages are strict objects with the exact fields shown. Numeric
`protocol` must be the integer **3**, never the string `"3"` or bool.
Every reply echoes the exact session. Encoded binary is canonical unpadded
Base64URL. Limit request bodies, headers, probe count and 120-second
lifetime and close on any malformed sequence. Use HTTP only for this
**token-free, human-verified** local exchange.

1. POST `/v3/pair/commit`

   Request: `{"protocol":3,"session":"...","ha_commit":"<32-byte digest>"}`

   Response: `{"protocol":3,"session":"...","tv_commit":"<32-byte digest>"}`

   Neither party sends a public key or secret nonce at this stage.
   TV records HA commitment exactly once.

2. POST `/v3/pair/reveal-ha`

   Request: `{"protocol":3,"session":"...","ha_key":"<SPKI DER>","ha_nonce":"<32 bytes>"}`

   Response: `{"protocol":3,"session":"...","ha_verified":true}`

   The Android state machine internally marks its local reveal but
   **does not disclose it** before independently checking the HA
   public key and nonce against the original commitment.

3. GET `/v3/pair/reveal-tv?protocol=3&session=...`

   Response: `{"protocol":3,"session":"...","tv_key":"<SPKI DER>","tv_nonce":"<32 bytes>"}`

   A one-time read; no further key reveal is available. HA verifies this
   key and nonce against the original TV commitment. Both sides then
   derive a fresh **eight-digit v3 code** from the immutable full transcript.

4. GET `/v3/pair/status?protocol=3&session=...`

   Before physical TV OK:
   `{"protocol":3,"session":"...","approved":false}`

   After a person physically presses TV remote OK and matches the
   displayed code:
   `{"protocol":3,"session":"...","approved":true,"tv_uuid":"<canonical UUIDv4>","proof":"<16-byte v3 MAC>"}`

   HA derives its own TV UUID-bound MAC and constant-time compares
   the exact code. No other network action can trigger TV approval.
   A valid proof only completes **physical verification**; an
   authenticated Home Assistant owner must still approve through
   the frontend before account provisioning.

## Implementation separation and remaining blockers

`PairingV3TvWireState` contains **no socket**; a separate Android
adapter must implement the routes using bounded HTTP parsing and a
short-lived mDNS service advertised while the TV setup UI is visible.
Likewise, `HaV3WireAttempt` and its transport exist but are not yet
called by an HA config flow. First implement the Android v3 listener and
connect it to a dedicated TV UI, then connect the HA config flow and test
the same pairing attempt on actual onn 4K Pro + Home Assistant 2026.10.

The two independent screen codes, real physical remote OK, HA owner
identification, multiple TVs, malformed packet replay, timeout,
background, reboot and cancellation all need **real-device** testing.

Before any **real token issuance**, enforce separate limited TV user
(#239), complete owner approval UI (#241), durable lease and revoke
(#240), encrypted session-bound transport and Android Keystore atomic
promotion. The old v2 code/MAC may never authorize credential delivery.
No public release until security review and all these checks pass.

**No user should install this preview as a working auto-login.**
