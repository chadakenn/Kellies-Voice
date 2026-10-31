# Owner-approved v3 sealed receipt (offline proof, no tokens)

**Development/security review only.** This does not install code on the onn
4K Pro, run an HA WebSocket or HTTP route, mint a credential, or change the
successful `owner_verified_no_login` test flow. Do not install on production.

## Implemented

An HA owner claim must first be recorded by the actual signed-in owner through
the v3 QA authenticated Home Assistant WebSocket. The server-side caller must
also hold the independently verified v3 P-256 commit/reveal transcript and
physical TV OK result. A new offline API
`issue_after_owner_claim(hass, claims, flow_id, verified_tv, private_key, verified)`
verifies the full v3 transcript and exact TV UUID/session, **consumes** that
single-use claim, reloads that exact owner with HA AuthManager and requires
`is_owner=True` and `is_active=True`. Only then may it encrypt the one fixed
literal message `owner-approved` under a HA-to-TV key. This is not a token,
command, account, authorization code or permanent permission.

The TV-side Java and Python verifiers derive the same key from the verified
TV transcript, accept only one 30-byte AES-GCM receipt, reject tampering and
replay, wrong TV, wrong session, noncanonical identity, unverified key, wrong
direction, changed owner, expired approval, and close/zero key material after
the first attempt. The Android verifier is not called by the app yet.

## Exact cross-language transcript-bound fixture

The v3 SAS fixture uses session `AQIDBAUGBwgJCgsMDQ4PEA`, P-256 private
scalars 1 (TV) and 2 (HA), nonce bytes 0..31 (TV) and 32..63 (HA), and TV UUID
`123e4567-e89b-42d3-a456-426614174000`. The fixed code is `83428994`.

This one-time receipt uses a separately domain-separated HMAC-SHA256 expansion
of the v3 transcript-bound ECDH secret:

- Key info `KelliesVoice/v3/owner-receipt-key\0`, length-framed 16-byte
  session, length-framed TV UUID ASCII, 0x02 (HA→TV), 0x01
- AAD `KelliesVoice/v3/owner-receipt-aad\0`, length-framed session and TV UUID,
  0x02, 64-bit big-endian sequence 1
- AES-256-GCM, 128-bit tag, IV 4 zero bytes + 64-bit big-endian sequence 1
- Fixed plaintext `owner-approved`, exactly once
- Ciphertext and tag (hex):
  `e5604d85a1208646dc1e6db915a359d53967bb7822568f04b77b8ba2146c`

Receipt issuance is never reachable by a TV-supplied owner_id, flow_id, UUID,
or an ordinary public URL. The proof is *not* a substitute for independently
verifying the logged-in owner, nor does it grant any actual TV permissions.

## Still blocked before real-network QA

**The existing v3 HA and Android identity tests close their private
transcripts and destroy ephemeral state immediately after physical remote OK.**
This new receipt runs independently from those flows and **cannot yet be
issued in a real pairing**. The next audited change must retain a bounded,
single-use key state across both actual owner approval and the TV ack,
then destroy it on cancel, restart, exception, expiry or replay. The listener
must never accept a forged owner bool or emit the receipt before server-side
approval is consumed.

Implement a separate explicit authenticated handoff (not arbitrary plaintext
LAN request). Review two-TV session mixups, owner race/cancel, backwards
monotonic clocks, off-main-thread Android closes, handshake timeouts,
untrusted discovery, and failure recovery before integrating.

The old per-TV capability gate #254 and permission preflight #253 remain
offline. No Assist, camera, unlock, voice command or HA token becomes
available because the encrypted receipt verifies. Need dedicated scoped
authentication, actual action enforcement, Android Keystore persistence,
revocation/crash-safe journal and independent security audit #214.

### Tests

- `python -m unittest discover -s tests -p test_v3qa_owner_receipt.py -v`
- `./gradlew -PskipWakeNative=true :app:testDebugUnitTest`
  runs `PairingV3OwnerReceiptVerifierTest` on the JVM.

Keep all PRs draft; do not merge `main`, update HACS or replace the user's
TV debug app until a real controlled hardware test is authorized.
