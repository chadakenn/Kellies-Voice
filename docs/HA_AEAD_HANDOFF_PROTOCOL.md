# Two-phase transcript-bound AEAD credential handoff (draft)

**Issue:** #213  
**Coordinates with:** #217 (HA pending lease), #218 (TV activation gate), #212 (sealed payload), #203 (AES-GCM)  
**Status:** Protocol design only. No live token exchange, no public endpoints, no credentials on mDNS/offer/status.

## Goal

Deliver a one-use OAuth credential set from HA to a physically verified TV, then exchange two directional authenticated messages so the TV may commit only after an independently authenticated HA authorization, and HA may drop its lease only after the TV has persisted.

## Preconditions (all required)

1. Owner has completed the HA OAuth external step and the single-use PKCE exchange has produced a refresh token held in a `PendingOAuthTransaction` (LEASED state).
2. The TV session, ECDH transcript, and matching-code/TV-OK proof are bound to the same flow.
3. The TV has a non-expired provisional HA config entry so the scoped alert subscription can succeed.
4. Transport is HTTPS (or loopback). Bare private-LAN HTTP is a blocker; do not send tokens over it.

## Packet fields (authenticated)

All AEAD packets use AES-256-GCM with a key derived from the ECDH shared secret + transcript binding (per #203/#212). Additional authenticated data (AAD) binds:

| Field | Type | Notes |
|-------|------|-------|
| `version` | uint8 | Protocol version = 1 |
| `type` | string | `credential_offer`, `tv_proof_receipt`, or `ha_commit_authorization` |
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

## Sequence (reconciled with #218)

1. HA encrypts `credential_offer` for the verified TV and sends it on the sealed channel. Lease remains revocable.
2. TV decrypts, stages the credential in the isolated Keystore slot, runs Assist + scoped-alert proof.
3. TV sends authenticated `tv_proof_receipt` (fresh nonce).
4. HA verifies the receipt against the exact session/TV/transcript, then sends authenticated `ha_commit_authorization`.
5. TV receives the HA authorization, passes the #218 gate, and persists the credential.
6. HA receives an optional persistence confirmation (or treats the authorization as the commit point) and calls `commit_after_proof()` only after the TV has acknowledged persistence, then drops the HA-side refresh token.
7. On any failure, timeout, or mismatch HA calls `abort_and_revoke()`.

## Authentication labels

- `kellies_voice/pair/credential_offer/v1`
- `kellies_voice/pair/tv_proof_receipt/v1`
- `kellies_voice/pair/ha_commit_authorization/v1`

These labels are included in the AAD so a packet of one type cannot be replayed as another.

## Rejected cases (fail closed)

- Wrong TV UUID or session
- Transcript hash mismatch
- Replay of any packet
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
