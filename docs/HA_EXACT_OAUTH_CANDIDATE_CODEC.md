# #193 HA exact OAuth candidate encoding (draft)

The Android [PR #225](https://github.com/chadakenn/Kellies-Voice/pull/225)
requires an authenticated Assist/alerts result to refer to the **same exact
encrypted credential bytes**, not merely the same pairing TV UUID/session.

This HA module provides a separate **offline** Python encoder and decoder
for the proposed canonical candidate:

- Magic bytes: ASCII `KVPair/credential-candidate/v1` followed by NUL.
- Six ordered unsigned-16-bit-big-endian-length UTF-8 fields:
  HA origin, HA OAuth client ID, access token, refresh token, 16-byte
  base64url session, canonical RFC4122 v4 TV UUID.
- Two signed 64-bit big-endian Unix milliseconds: access expiry and staged time.
- Maximum 1024 bytes. Invalid/malformed/extra bytes fail closed. A valid
  larger token is rejected until the limits/protocol are deliberately
  revised. Never truncate.

Candidate commitment: SHA-256 over ASCII domain
`KVPair/v1/exact-candidate-binding` + NUL, unsigned 16-bit candidate
length and the **exact** canonical candidate bytes. The commitment is a
**local equality handle**; only AES-GCM and full verified consent/TV OK
authenticate the pairing. Do not publish hashes or credential fields.

Cross-language fixed test vector uses synthetic `a` and `r` token strings,
`https://ha.example`, session `AQIDBAUGBwgJCgsMDQ4PEA`,
TV UUID `123e4567-e89b-42d3-a456-426614174000`, stage
`1700000000000`, expiry `1700001800000`; binary length 218 bytes
and digest `a9baf0e2a82d08aca7ddb387b906016a25cd1ee7b5f138b96d0b972ee9440f23`.

**No live token generation/transport or config-flow hook is added here.**
This Python counterpart is not enough to make the existing HA-first OAuth
flow work. Before any actual exchange:

1. Verify actual HA Core owner PKCE OAuth consent/callback and exact TV
   verification proof.
2. Encode the **leased exact** credential object as the only encrypted
   offer candidate, and decode that same candidate on the TV before
   executing its WSS capability proof. Never allow a separately supplied
   credential object or caller-provided digest to bypass binding.
3. Complete the existing four-message authenticated handoff, verified
   Keystore promotion and durable revocation/crash-restart rollback.
4. Test on HA Core and onn 4K Pro; require #214 adversarial audit.

**KEEP DRAFT. Do not merge to main/dev/#206 or release a beta.**
