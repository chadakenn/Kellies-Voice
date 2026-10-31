# KVPair v3: encrypted QA ping before real TV authorization

**Draft security prototype, NOT live authentication.** The previously tested
onn 4K Pro + HA v3 flow still ends at `owner_verified_no_login`. No HA login,
OAuth grant, token creation, credentials, service execution or Android Keystore
write is permitted by this PR. This code does not run in the TV app.

## What is implemented and tested

A complete verified v3 P-256 commit/reveal transcript already produces an
ECDH extract shared by the TV and HA. Python and JVM derive two independent
256-bit AES-GCM keys from that extract, using HMAC-SHA256 HKDF-Expand:

- TV → HA direction 0x01; HA → TV direction 0x02
- key-info: ASCII `KelliesVoice/v3/qa-aead/key\0`, 2-byte length-framed
  16-byte v3 session, 2-byte length-framed canonical TV UUID ASCII,
  2-byte length-framed one-byte direction, plus 0x01
- nonce: four zero bytes plus 64-bit big-endian monotonically increasing
  sequence; independent per-direction keys eliminate direction collisions
- AAD: ASCII `KelliesVoice/v3/qa-aead/aad\0`, same framed session and
  TV UUID, raw direction byte, and the big-endian sequence
- GCM tag length 128 bits; strictly sequential receive; any tamper/replay,
  wrong direction/session/TV UUID, malformed or expired message fails closed
- Only **literal** ASCII `qa-ping` and `qa-pong` allowed, never an arbitrary
  byte buffer, credential, command, service call, OAuth code or token
- Session memory lifetime ≤120s, explicit close, no persistence or key logs

Frozen Python/JVM fixture: session `AQIDBAUGBwgJCgsMDQ4PEA`,
TV UUID `123e4567-e89b-42d3-a456-426614174000`,
P-256 scalar 1 (TV) and scalar 2 (HA), nonces bytes 0..31 and 32..63.

| direction | message | GCM ciphertext + tag (hex) |
| --- | --- | --- |
| TV → HA, sequence 1 | qa-ping | `32cced09c0407f709d358666bff467c580c102e4fea474` |
| HA → TV, sequence 1 | qa-pong | `c96732d43dab9211e1d9a068bdd766428e25a33a9ae4d7` |

## Critical missing security binding

**A successful encrypted QA ping is not an authorized TV command.** It proves
the sender possessed the specific v3 ephemeral key *at that moment*. The
existing TV/HA pairing code currently closes and wipes its ECDH material
immediately after TV remote approval; this PR intentionally does not change
that lifetime. The authenticated HA owner approval may happen afterward.

Before any on-wire test, design and independently review a handshake handoff
that:
1. Retains the verified transcript and keys only in a locked, bounded session
   until authenticated owner consent is actually consumed; abort/expiry/reboot
   must destroy it.
2. Sends an explicitly bound **owner-approval acknowledgement** over the new
   AEAD channel, not a browser-supplied owner boolean. Fail closed when no
   owner has approved the exact TV UUID/session.
3. Binds every authenticated message to one server-side channel, a specific
   TV, exact operation scope, request number and the permission gate #254.
4. Does not create an HA token until a separately reviewed least-privilege
   account / command-enforcement design exists. An ordinary non-admin
   HA user still has too many entity permissions.
5. Tests two TVs, network disconnect, injection, races, time rollback, HA
   restart, concurrent channel close, credential loss and revoke, and Android
   Keystore activation before beta.

An AEAD transport is *not* automatically a scoped Assist session.
No release or main merge until #214 independent audit and all gates pass.

Tests: `python -m unittest discover -s tests -p 'test_v3qa_aead.py' -v`;
JVM `:app:testDebugUnitTest` includes `PairingV3QaPingChannelTest`.

**No new ZIP or APK installation is requested from the owner for this PR.**
