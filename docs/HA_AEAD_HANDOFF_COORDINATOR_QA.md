# #193: Four-message HA AEAD handoff coordinator, draft QA

**Status:** Source-only security milestone. No new HTTP/HTTPS route, OAuth code exchange,
refresh-token transfer, Android credential activation, config-flow transition, or
production pairing is connected. This is not yet a usable feature.

## Code

`custom_components/kellies_voice/pairing_handoff_state.py` hosts
`HASealedHandoff`, the Home Assistant side of the proposed #219 protocol.
It consumes the already-tested `PairingSealedChannel` P-256/AES-GCM instance,
which **must** be created after authenticated Home Assistant owner consent,
matching-code comparison, physical TV OK and the v2 UUID-bound proof.

The coordinator requires the exact canonical v4 TV app UUID, one-use 16-byte
session, expected public P-256 transcript and authenticated owner ID.

| Step | Sender | Encrypted message | HA state afterward |
| --- | --- | --- | --- |
| 1 | HA | `credential_offer` (opaque candidate) | `WAIT_TV_PROOF` |
| 2 | TV | `tv_proof_receipt` (Assist and TV-scoped alerts both true) | `READY_TO_COMMIT` |
| 3 | HA | `ha_commit_authorization` | `WAIT_PERSISTED` |
| 4 | TV | `tv_persisted_receipt` (verified durable store true) | `PERSISTED` |

All message types, UUID, session, full public ECDH transcript hash and
expiry are inside the AES-GCM authenticated plaintext. The underlying
channel enforces independent directional keys and monotonic packet sequence.
A replay, wrong stage, wrong identity, wrong transcript, tampering, missing
permission, expiry or unexpected JSON field aborts and closes the channel.
Only `PERSISTED` permits `release_pending_lease`, which checks the owner
and TV-session binding before invoking the existing transaction's
`commit_after_proof`. The coordinator never logs tokens.

## Reproduce in CI (no Home Assistant, network or credentials)

Run `python -m unittest discover -s tests -p 'test_pairing_handoff_state.py' -v`
in the repository's Python cryptography test environment.

Tests perform the full four-message loop using independent real P-256 TV/HA
keys and directional AES-GCM in memory. Candidates are synthetic non-secret
bytes. They also exercise wrong TV/session/hash/version, missing Assist/alert
confirmation, forged plaintext, wrong message order, failed persistence,
expiry, wrong owner, wrong channel direction and replay protection.

## Before this can be wired to actual tokens

1. Validate the real Home Assistant config-flow and OAuth callback/PKCE
   lifecycle in installed HA Core, including owner and TV identity.
2. Bind the TV app's actual proved UUID to the same v2 ECDH handshake.
3. Implement the TV-side four-message authenticated coordinator using the
   same canonical wire structure; the Android proof/Keystore gates alone
   do not implement the receipt protocol.
4. Require real read-only Assist and TV-scoped alert subscription outcomes,
   not caller-supplied booleans, before creating `tv_proof_receipt`.
5. Define an encrypted, durable revocation/recovery strategy for the
   crash windows **after** OAuth exchange and **after** TV persistence,
   plus actual token refresh/revoke and previous-manual-login rollback.
6. Verify that credential transport is HTTPS or independently reviewed
   equivalent protected transport; never send credentials in public mDNS,
   pairing offer, status or unencrypted HTTP.
7. Real onn 4K Pro + HA integration QA, two TVs and wrong TV, expired
   session, cancel, HA restart, and independent #214 audit.

**Do not merge, publish, claim connected or call `/auth/token` from this
draft until these issues are resolved.**
