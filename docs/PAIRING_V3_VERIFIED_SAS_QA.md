# KVPair v3 SAS and TV remote-OK MAC: shared test contract (#214)

**OFFLINE ONLY**. This branch contains the Python derivation that Android must
match before any v3 LAN endpoint, OAuth issuance or TV credential transfer is
enabled. The unreviewed **v2** SAS/proof and working manual login are unchanged.

## Exact inputs and canonical order

- Session: strict URL-safe unpadded Base64 representation of 16 random bytes.
- Each side has an independent P-256 ephemeral private key, canonical X.509
  SubjectPublicKeyInfo DER public key and 32-byte random secret nonce.
- HA sends only `ha_commit`; TV returns only `tv_commit`. Neither reveals
  the public key or nonce until BOTH commitments have been exchanged.
- HA reveals its DER public key and nonce. TV verifies these bytes against
  the original HA commitment **before** sending its own key/nonce.
- TV reveals its DER public key and nonce. HA verifies against the original
  TV commitment. A wrong/repeated/late reveal permanently closes the session.
- Both must validate P-256 curve, nonidentical canonical DER keys, exact
  32-byte nonces, roles, session and a short session expiry. No v2 downgrade.
- The code and MAC must be derived **only** from a fully verified
  `CommitmentSession`, not a user-provided nonce or another key object.

`part(x)` means 2-byte unsigned big-endian byte length followed by x.

`tv_commit = SHA256("KelliesVoice/v3/key-commit\\0" || part("tv") ||
part(session_16_bytes) || part(tv_public_DER) || part(tv_nonce_32))`

`ha_commit` is identical with role `"ha"`, HA key and HA nonce.

**Canonical SAS transcript**, regardless of which device calculates it:

```
SHA256(
  ASCII("KelliesVoice/v3/sas-transcript\\0")
  || part(session_16)
  || part(tv_commit_32)
  || part(ha_commit_32)
  || part(tv_public_DER)
  || part(ha_public_DER)
  || part(tv_nonce_32)
  || part(ha_nonce_32)
)
```

Compute P-256 ECDH shared secret using the verified peer public key.
`PRK = HMAC_SHA256(transcript_SHA256, ECDH_shared_secret)`.

`code_hmac = HMAC_SHA256(PRK, ASCII("KelliesVoice/v3/eight-digit-code\\0") || 0x01)`

The eight-digit SAS is the **first four code_hmac bytes as big-endian
unsigned**, modulo 100,000,000, formatted with leading zeros.

**Physical TV OK proof**, emitted only after the human has checked the code
and pressed the physical TV remote OK button:

`mac = HMAC_SHA256(PRK, ASCII("KelliesVoice/v3/remote-ok-uuid\\0")
|| part(ASCII(canonical_RFC4122_v4_tv_UUID)) || 0x01)`

Truncate to the first 16 bytes and encode as unpadded Base64URL. Home
Assistant must independently compute and constant-time compare this result
against the exact TV UUID returned by its verified peer. It still needs
actual signed-in owner approval. A valid MAC is **not** authorization
to release a credential by itself.

## Deterministic Python ↔ Android fixture

Both sides MUST use precisely:

- Session string `AQIDBAUGBwgJCgsMDQ4PEA` (bytes 0x01 through 0x10).
- TV P-256 private scalar 1, HA scalar 2 (test-only known keys).
- TV nonce 0x00..0x1f, HA nonce 0x20..0x3f.
- TV UUID `123e4567-e89b-42d3-a456-426614174000`.

Expected TV commitment:
`df28eb33b2fb5c6d5e45e8732a5893e3c938892dc2ff642361235f7b5f1a9f2d`

Expected HA commitment:
`f6fbfcfefc4a3aa3c9ccb39a9bd97888a2c02a564e85c1a869681fe02dc9bd9c`

**Expected visual code: `83428994`**

**Expected TV remote proof: `TuUIMiT-zkUz3XaKygd5Gw`**

These fixture private keys and nonces are never suitable for production.

## Proposed message framing, not yet implemented

Before Android or HA implements either side's v3 wire, agree on strict
bounded JSON messages and immutable, version-separated endpoints. Suggested
test-only sequence (all replies must echo `protocol:3` and exact session):

1. HA → TV `POST /v3/pair/commit` with session + base64url(ha_commit).
   TV returns base64url(tv_commit), **no keys, nonces or tokens**.
2. HA → TV `POST /v3/pair/reveal-ha` with session + HA public DER + HA
   nonce. TV checks its stored original HA commitment then returns only
   `accepted: true` (never the TV key yet).
3. HA → TV `GET /v3/pair/reveal-tv?session=...` once. TV returns its
   public DER + nonce; HA checks against original TV commitment.
4. HA/TV display the v3 code. Human compares all eight digits. TV remote
   OK gates a v3-specific status response with canonical TV UUID + MAC.
5. HA confirms MAC and user comparison; then a separate signed-in owner
   approval (see #241) can proceed. **Still no token issuance in this QA**.

Each request must be one-shot, size-limited, time-limited, with no redirects;
no unauthenticated LAN caller can reset or steal a verified active session.
Refuse mixed v2/v3 fields, unknown versions, missing/invalid identity, key
substitution, replay and out-of-order messages. This proposal needs **joint
Android + HA review** before implementation; it is NOT a deployed API.

## Must pass before any public release

- Grok's Android implementation matches both frozen fixture outputs and
  strict key commitment order, including active MITM substitution tests.
- A real onn 4K Pro and actual HA 2026.10 instance show the same fresh
  random v3 code, physical remote OK yields UUID-bound proof, and cancel,
  expiry, standby/reboot, multi-TV and downgrade refusal work.
- Separately authenticate an HA owner, provide a distinct limited TV user,
  retain/revoke a durable token lease (#239/#240), secure credential
  delivery into Android Keystore and verify actual Assist/alerts. No admin
  owner refresh token may ever be sent to Android.
- Review and merge only after security audit and honest documentation.

This branch intentionally does NOT import the new verifier into production
`config_flow.py` or `PairingDiscoveryWindow.java`.
