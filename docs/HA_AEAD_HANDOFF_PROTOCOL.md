# Two-phase transcript-bound AEAD credential handoff (draft)

**Issue:** #213  
**Coordinates with:** #217 (HA pending lease), #218 (TV activation gate), #212 (sealed payload), #203 (AES-GCM)  
**Status:** Protocol design only. No live token exchange, no public endpoints, no credentials on mDNS/offer/status.

## Goal

Deliver a one-use OAuth credential set from HA to a physically verified TV, then receive an authenticated commit ACK, without exposing tokens on any public or unauthenticated path.

## Preconditions (all required)

1. Owner has completed the HA OAuth external step and the single-use PKCE exchange has produced a refresh token held in a `PendingOAuthTransaction` (LEASED state).
2. The TV session, ECDH transcript, and matching-code/TV-OK proof are bound to the same flow.
3. The TV has a provisional HA config entry (see below) so the scoped alert subscription can succeed.
4. Transport is HTTPS (or loopback). Bare private-LAN HTTP is a blocker; do not send tokens over it.

## Packet fields (authenticated)

All AEAD packets use AES-256-GCM with a key derived from the ECDH shared secret + transcript binding (per #203/#212). Additional authenticated data (AAD) binds:

| Field | Type | Notes |
|-------|------|-------|
| `version` | uint8 | Protocol version = 1 |
| `type` | string | `credential_offer` or `commit_ack` |
| `tv_uuid` | string | Exact paired device UUID |
| `session` | string | Pairing session id from ECDH |
| `transcript_hash` | bytes32 | SHA-256 of the full ECDH transcript |
| `nonce` | bytes12 | Unique per packet, never reused |
| `expiry` | uint64 | Unix timestamp, ≤ 120 s from issue |
| `payload` | ciphertext | See below |

### `credential_offer` payload (encrypted)

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "expires_in": 1800,
  "client_id": "https://ha.example/"
}
```

### `commit_ack` payload (encrypted)

```json
{
  "ack_for": "credential_offer",
  "tv_uuid": "...",
  "session": "...",
  "assist_ok": true,
  "alerts_ok": true
}
```

## Sequence

1. HA encrypts `credential_offer` for the verified TV and sends it on the sealed channel.
2. TV decrypts, stages the credential in the isolated Keystore slot (#218), runs Assist + scoped-alert proof.
3. TV sends authenticated `commit_ack` (separate packet, fresh nonce).
4. HA verifies the ACK against the exact session/TV/transcript, then calls `commit_after_proof()` on the pending transaction and drops the HA-side refresh token.
5. On any failure, timeout, or mismatch HA calls `abort_and_revoke()`.

## Authentication labels

- `kellies_voice/pair/credential_offer/v1`
- `kellies_voice/pair/commit_ack/v1`

These labels are included in the AAD so a packet of one type cannot be replayed as the other.

## Rejected cases (fail closed)

- Wrong TV UUID or session
- Transcript hash mismatch
- Replay of either packet
- Expired nonce or TTL
- Missing or forged consent
- Commit ACK arriving before the offer was leased
- Any packet on mDNS, offer, status, or unauthenticated HTTP

## Crash/restart

Until a durable encrypted pending store exists, a HA process crash after exchange but before commit leaves an orphaned refresh token. Live exchange must not be enabled without a recovery design (short-lived tokens or external revocation queue).

## Open blocker

Typical local HA installs use HTTP on the LAN. OAuth access and refresh tokens must not traverse bare private-LAN HTTP. A protected transport (HTTPS, mutual TLS, or an equivalent sealed channel) is required before any hardware beta.

---

*This document is a draft protocol. No implementation of the wire path is enabled by its presence.*
