# KVPair v3: offline key commitment before reveal (security draft)

## Why this exists

Claude's review in #214 identified a **last-mover active-MITM weakness** in the current two-screen P-256 pairing prototype. A malicious LAN intermediary can pick its replacement key *after* seeing the other party's public key, then grind candidate keys until its derived short verification number matches the number on the physical TV. The existing eight-digit code + TV OK is useful for human verification, but not a proof that this deliberate attack has been cryptographically ruled out. **No live tokens should be enabled using the current v2 pairing as-is.**

## New offline barrier

This branch adds matching pure functions and local state machines, not active networking:
- HA: `custom_components/kellies_voice/pairing_key_commitment.py`.
- Android: `app/src/main/java/com/example/tvvoiceprobe/PairingKeyCommitment.java`.
- Tests: `tests/test_pairing_key_commitment_v3.py` and `PairingKeyCommitmentTest.java`.

The commitment digest is **SHA-256** of `"KelliesVoice/v3/key-commit\\0"`, followed by the SHA-256 input fields (each a **2-byte big-endian length + exact bytes**): local role `tv` or `ha`, canonical 16-byte decoded session, ephemeral P-256 public-key DER bytes, and an independently random **32-byte secret nonce**. Domain separation and role-specific commitment prevent reflected or cross-role messages. All inputs have strict lengths, and digest comparison is constant time.

The **required next-generation wire sequence**:
1. Both sides generate fresh P-256 keys and independent 32-byte secret nonces locally.
2. HA transmits **only its 32-byte commitment**, not HA public key or nonce.
3. TV replies with **only its 32-byte commitment**, not TV public key or nonce.
4. HA reveals its public key and nonce; TV verifies against the *already received* commitment.
5. TV reveals its public key and nonce; HA verifies against the *already received* commitment.
6. Both validate P-256 curve/key, compute ECDH, derive the **version-separated v3 verification code using the fully verified transcript**, show the same eight digits, and require physical TV OK plus explicit HA confirmation. Then the existing UUID-bound proof must be updated and tested to bind the v3 transcript.

**Never allow v3 to fall back to v2** on unrecognized/malformed packets. An attacker cannot silently choose a new public key after signing up to its commitment. An active attacker can still deny service, and short-code matching is still vulnerable if users don't compare all digits. The code is not a password or credential.

## Test fixture shared across Python and Android

Session: `AQIDBAUGBwgJCgsMDQ4PEA`. TV P-256 public key fixture derives from scalar 1; HA fixture from scalar 2 (existing project Python↔Java interop vectors). TV nonce = bytes 0..31, HA nonce = bytes 32..63.

- TV commitment: `df28eb33b2fb5c6d5e45e8732a5893e3c938892dc2ff642361235f7b5f1a9f2d`.
- HA commitment: `f6fbfcfefc4a3aa3c9ccb39a9bd97888a2c02a564e85c1a869681fe02dc9bd9c`.

The tests require both commits BEFORE revealing either key, reject changed key/nonce, replay, reflection, wrong session/role, malformed peers and out-of-order actions. **This demonstrates only the primitive, not the whole protocol.**

## Independent review gates

This PR deliberately leaves `PairingDiscoveryWindow`, `config_flow.py`, `VoiceActivity`, `HaAssistClient`, and `CredentialStore` untouched. CI guards that isolation. The v3 protocol is **not wired** and cannot be used to pair a device yet.

Before wiring:
- Review exact network message order and ability to avoid deadlocks (two-phase commit exchange, two-phase reveal).
- Commit to all session/TV identity metadata needed for the final proof.
- Derive a new `v3` human verification code and remote-OK MAC that include both commitments and both nonces, without introducing new last-mover opportunities.
- Test real HA + onn TV under forged/cross-TV, wrong digest, replay, down-negotiation, timeout and DoS.
- Address Claude's other high severity issues **H1 admin token overprivilege, H3 browser owner linkage and H4 durable revocation**. Complete #235 user approval UI and local-only HA authorization. No token transfer until all are reviewed.

Keep **DRAFT**. No public release, no main merge, no user credentials, and no live pairing behavior change.
