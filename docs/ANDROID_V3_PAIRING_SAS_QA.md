# Android v3 pairing cryptography handoff after Grok quota limit

**This is an offline Android + Home Assistant cross-language verification
draft, stacked on PR #242. It must NOT be installed as a working login,
merged into main, or released as v0.17.0.**

## Why this exists

The Home Assistant-side PR #242 already pins a v3 commit-before-reveal
transcript, but Android's PR #236 has only a key commitment primitive.
Without an actual independent Android v3 code and remote-OK MAC, real
pairing endpoints cannot safely be wired or tested.

## Completed Java implementation

- `PairingKeyCommitment.Session.verifiedTranscript()` returns one copied
  immutable-session snapshot under the same lock, after BOTH key commitments
  and BOTH peer reveals were validated, with no mutable live-state aliases.
- `PairingV3Verified.comparisonCode(privateKey, session)` computes the
  canonical role-independent SHA-256 transcript and P-256 ECDH derived
  eight-digit SAS. No v2 fallback exists in this module.
- `PairingV3Verified.physicalTvOkProof(privateKey, session, tvUuid)` derives
  the v3 16-byte MAC bound to a canonical RFC4122 v4 TV UUID. It is only a
  helper; the physical TV remote event must gate the actual invocation.
- The Java verifier checks canonical DER and precise secp256r1 parameters,
  validates that the private key actually matches the committed public key
  using a local ECDSA signature test, and rejects an unverified/closed session.
- The PR has NO changes to Android listener, microphone, settings, credential
  store, OAuth, persisted tokens, Home Assistant config flow, or existing v2.

## Exact Python↔Java fixture

P-256 TV private scalar 1, HA private scalar 2, session
`AQIDBAUGBwgJCgsMDQ4PEA`, TV nonce bytes 0..31, HA nonce bytes
32..63, TV UUID `123e4567-e89b-42d3-a456-426614174000`.

Expected v3 visual code: **`83428994`**.

Expected TV UUID-bound remote OK proof: **`TuUIMiT-zkUz3XaKygd5Gw`**.

Both Android TV and HA must independently calculate exactly those values
and reject bad commitments, key substitution, wrong nonce/session, wrong
private/public key, early code, cross-TV UUID, cancellations and replays.

## The next milestone is real wire integration, not yet implemented

Agree with the Python side on bounded, versioned, strictly one-shot LAN
messages before adding new TV endpoints. Both peers must commit before
revealing keys/nonces. Bind session, role, TV UUID, timeout and immutable
transcript, then display code on TV and HA. Physical TV remote OK and
explicit HA owner approval follow. v2 is *never* acceptable for credential
delivery.

This does **not** authorize a credential: the restricted TV-user identity,
durable revocation journal, native HA owner approval flow, actual Assist and
scoped alerts and atomic Keystore handoff remain release blockers.

The existing manually configured Kellie's Voice connection stays untouched.
