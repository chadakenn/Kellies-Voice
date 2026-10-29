# #193: Exact OAuth candidate binding, draft B15

**Status: development-only gate. No new live login, OAuth token exchange,
Android credential activation, public API route or network endpoint.**

Before this draft, the one-use Assist+alerts WebSocket receipt was only
bound to the initiating TV UUID and pairing session. A future integration
could accidentally use a successful proof for credential A to authorize
different credential B, despite the same TV and session.

## Binding rule

`PairingCredentialCandidateCodec` now defines a proposed strict binary
format for the exact OAuth candidate that will eventually be encrypted
inside the `credential_offer` AEAD message. In field order:

1. ASCII magic: `KVPair/credential-candidate/v1\0`
2. Six UTF-8 strings, each prefixed by an unsigned 16-bit big-endian length:
   **Home Assistant origin**, **OAuth client ID**, **access token**,
   **refresh token**, **TV pairing session**, **canonical TV v4 UUID**
3. Signed 64-bit big-endian **access expiry epoch milliseconds**
4. Signed 64-bit big-endian **staged-at epoch milliseconds**

The payload is limited to **1024 bytes**, matching the current HA draft
handoff offer limit. It refuses oversized or malformed UTF-8, invalid
credentials, unknown versions, truncated fields and trailing data. OAuth
tokens typically vary in size; an oversized valid token will **not** be
accepted until a coordinated higher-capacity protocol version is designed
and tested. No silent truncation or fallback.

The locally computed commitment is
`SHA256("KVPair/v1/exact-candidate-binding\\0" || uint16_be(len) || exact_candidate_bytes)`.
It is a binding identifier only, never an authentication secret or
replacement for authenticated physical TV OK, owner consent and AEAD.

`PairingAssistConnectionProbe.checkBound` computes the commitment from
the *same* `PairedOAuthCredentials` object it tests through trusted
HTTPS/WSS. Only after that socket authenticates and both Assist + exact
TV-scoped alert permissions succeed does the private, one-use
`VerifiedCapabilityReceipt` contain that candidate commitment.
The `TvSealedHandoff.tvProofFromVerifiedSocket` method compares it
against the commitment derived from the **exact sealed offer bytes held
in memory**, and aborts if anything differs. Two different tokens, HA
origins, refresh credentials, timestamps or TV bindings can no longer
share a receipt merely because the pairing session matches.

This is still *synthetic-only* in the TV handoff. Production must parse
the already authenticated offer with the codec and feed the **same**
decoded credential object to the WSS checker, without accepting a
separate credential supplied by a caller. The current Android
`onSyntheticOffer` deliberately rejects the proposed OAuth binary
format, so nothing here starts live provisioning.

## Tests

- Candidate encode/decode exact round trip.
- Different Home Assistant origin, access token, refresh token, TV UUID,
  access expiry and creation time produce different commitments.
- Short/truncated/extra/oversized candidates fail closed.
- A one-use WSS proof cannot be claimed with a different offer digest,
  different TV/session, wrong time or repeated claim.
- Existing Python↔Java synthetic encrypted four-message test must
  continue to pass on the stacked branch.

## Further blockers

Home Assistant must implement and cross-test the same canonical
candidate encoding before any real OAuth handoff. The true HA Core
owner browser flow, exact TV OK/v2 transcript, TLS transport,
direct credential parsing/Keystore activation, refresh-token revocation
and crash/restart recovery still need full integration, hardware QA
and independent security audit #214.

**Keep draft. Do not merge to main/dev/#206 or publish a beta.**
