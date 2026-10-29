# Two-phase transcript-bound AEAD credential handoff (draft)

**Issue:** #213  
**Coordinates with:** #217 (HA pending lease), #218 (TV activation gate), #212 (sealed payload), #203 (AES-GCM)  
**Status:** Conceptual protocol with HA-only in-memory state machine staged in draft #221. No live token exchange, public endpoints, or credentials on mDNS/offer/status.

## Goal

Deliver a one-use OAuth credential set from HA to a physically verified TV, then exchange two directional authenticated messages so the TV may commit only after an independently authenticated HA authorization, and HA may drop its lease only after the TV has persisted.

## Preconditions (all required)

1. Owner has completed the HA OAuth external step and the single-use PKCE exchange has produced a refresh token held in a `PendingOAuthTransaction` (LEASED state).
2. The TV session, ECDH transcript, and matching-code/TV-OK proof are bound to the same flow.
3. The TV has a non-expired provisional HA config entry so the scoped alert subscription can succeed.
4. Transport is HTTPS (or loopback). Bare private-LAN HTTP is a blocker; do not send tokens over it.

## Exact in-memory draft #221 frame (not a live network protocol)

The current `PairingSealedChannel` already authenticates a 22-byte outer
header: version byte `0x01`, direction byte (0=HA→TV, 1=TV→HA),
8-byte big-endian sequence, and 12-byte random AES-GCM nonce. The 16-byte
authentication tag follows ciphertext. AEAD associated data is the existing
`KelliesVoice/v1/channel-aad` label, the public ECDH transcript SHA-256
and the full outer header. Sequence and direction enforcement are mandatory.

Within that encrypted authenticated payload, #221 uses exact JSON fields:

| Field | Type | Current #221 name |
| --- | --- | --- |
| Protocol version | integer 1 | `v` |
| Authenticated message type | string | `type` |
| Canonical TV application UUID | string | `tv_uuid` |
| 16-byte base64url session | string | `session` |
| Public ECDH transcript SHA-256 | 64-digit lowercase hex | `transcript` |
| Fixed session expiry | integer Unix seconds | `expires_at` |
| Encrypted candidate (offer only) | base64url without padding | `candidate_b64` |
| Required receipts | exact typed keys and true booleans | `ack_for`, `assist_ok`, `alerts_ok`, `authorize_commit`, `verified_store_committed` |

Unknown fields and mismatches abort. The **message type is authenticated
inside AES-GCM ciphertext**, not yet in a separate per-type AAD label.
The type-specific AAD labels discussed below remain proposed future
hardening and must not be described as implemented. No actual OAuth
credentials, TV endpoint, or token transfer have been connected.

The following table is the *design-level* packet description. It is not a
second, compatible wire encoding. Any TV implementation must match the
precise #221 frame and its existing P-256/AES-GCM v1 outer header; changes
to fields, labels or key derivation require coordinated revisions, fresh
interoperability vectors and security review.

## Packet fields (authenticated)

All AEAD packets use AES-256-GCM with a key derived from the ECDH shared secret + transcript binding (per #203/#212). Additional authenticated data (AAD) binds:

| Field | Type | Notes |
|-------|------|-------|
| `version` | uint8 | Protocol version = 1 |
| `type` | string | `credential_offer`, `tv_proof_receipt`, `ha_commit_authorization`, or `tv_persisted_receipt` |
| `tv_uuid` | string | Exact paired device UUID |
| `session` | string | Pairing session id from ECDH |
| `transcript_hash` | bytes32 | SHA-256 of the full ECDH transcript |
| `nonce` | bytes12 | Unique per packet, never reused |
| `expiry` | uint64 | Unix timestamp, ≤ 120 s from issue |
| `payload` | ciphertext | See below |

### `credential_offer` (HA → TV, encrypted)

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "expires_in": 1800,
  "client_id": "https://ha.example/"
}
```

### `tv_proof_receipt` (TV → HA, encrypted)

```json
{
  "ack_for": "credential_offer",
  "tv_uuid": "...",
  "session": "...",
  "assist_ok": true,
  "alerts_ok": true
}
```

### `ha_commit_authorization` (HA → TV, encrypted)

```json
{
  "authorize_commit": true,
  "tv_uuid": "...",
  "session": "..."
}
```

### `tv_persisted_receipt` (TV → HA, encrypted)

```json
{
  "ack_for": "ha_commit_authorization",
  "tv_uuid": "...",
  "session": "...",
  "verified_store_committed": true
}
```

## Sequence (reconciled with #218)

1. HA encrypts `credential_offer` for the verified TV and sends it on the sealed channel. Lease remains revocable.
2. TV decrypts, stages the credential in the isolated Keystore slot, runs Assist + scoped-alert proof.
3. TV sends authenticated `tv_proof_receipt` (fresh nonce).
4. HA verifies the receipt against the exact session/TV/transcript, then sends authenticated `ha_commit_authorization`.
5. TV receives the HA authorization, passes the #218 gate, and persists the credential.
6. TV sends mandatory authenticated `tv_persisted_receipt` only after synchronous, verified Keystore storage succeeds. HA must validate this exact TV/session/transcript before `commit_after_proof()` may drop its revocable token lease.
7. If confirmation is missing, HA must treat the lease as uncommitted, invoke `abort_and_revoke()`, and require the TV to discard its unactivated candidate when the session times out. This is not a globally atomic transaction: crash/restart recovery and compensating rollback are still required before enabling live exchange.
8. On any wrong-device proof, failed commit, timeout, or mismatch, fail closed and preserve the previous manual TV connection.

## Authentication labels

- `kellies_voice/pair/credential_offer/v1`
- `kellies_voice/pair/tv_proof_receipt/v1`
- `kellies_voice/pair/ha_commit_authorization/v1`
- `kellies_voice/pair/tv_persisted_receipt/v1`

These per-type labels are **proposed**, not presently wired into the v1 sealed-channel AAD. The in-memory #221 implementation instead authenticates and checks the exact message type inside the encrypted JSON payload and enforces strict message order. A future per-type AAD revision must change both peers consistently and carry new cross-language vectors.

## Rejected cases (fail closed)

- Wrong TV UUID or session
- Transcript hash mismatch
- Replay of any packet
- HA revoking/leaving pending before authenticated persisted receipt
- TV treating a bare boolean or public status as HA commit authorization
- Expired nonce or TTL
- Missing or forged consent
- HA commit authorization arriving before the TV proof receipt
- Any packet on mDNS, offer, status, or unauthenticated HTTP

## Crash/restart

Until a durable encrypted pending store exists, a HA process crash after exchange but before commit leaves an orphaned refresh token. Live exchange must not be enabled without a recovery design (short-lived tokens or external revocation queue).

## Open blocker

Typical local HA installs use HTTP on the LAN. OAuth access and refresh tokens must not traverse bare private-LAN HTTP. A protected transport (HTTPS, mutual TLS, or an equivalent sealed channel) is required before any hardware beta.

---

*This document is a draft protocol. No implementation of the wire path is enabled by its presence.*
