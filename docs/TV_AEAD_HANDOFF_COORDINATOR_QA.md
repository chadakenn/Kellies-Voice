# #193: TV-side AEAD handoff coordinator (draft)

**Not live pairing.** No listener, OAuth token, URL, saved login, microphone, or Connected state is touched.

This branch adds the isolated TvSealedHandoff Java peer of HA draft PR #221. It uses the existing PairingSealedChannel P-256/AES-256-GCM v1 protocol, checks the stable canonical TV UUID, ECDH transcript, exact session, message type, expiry, and directional message order.

## Four-message exchange

1. Receive encrypted credential_offer, accepting only bytes with the literal public development prefix SYNTHETIC NON-SECRET. Stage them in memory, not preferences.
2. After PairingAssistProofState reaches VERIFIED (both Assist and scoped TV alerts), encrypt tv_proof_receipt. This pure gate cannot by itself attest that the live WebSocket proof was genuine. Do not wire it to live OAuth.
3. Decrypt HA commit_authorization, checking the same TV UUID, session, transcript hash and expiry.
4. Invoke a synthetic test disk sink. Only after a successful synchronous return, encrypt tv_persisted_receipt. Failed writes and wrong order never generate this receipt.

All four messages use the existing version, direction, sequence, 12-byte nonce, GCM tag, P-256-derived keys and authenticated transcript from PairingSealedChannel. No token is sent over an unencrypted listener. Abort on tampering, replay, expiry (both wall clock and local monotonic two-minute cap), wrong identity, missing proof, unexpected JSON fields, bad ciphertext and rejected test storage.

## Testing

TvSealedHandoffTest is a JVM test with independent Java P-256/AES-GCM peer channels, synthetic test bytes and a fake disk sink. The Android app already uses org.json, and Gradle testImplementation provides a real JVM parser. Run Gradle unit tests with -PskipWakeNative=true and verify the normal JNI debug APK independently.

## Remaining blockers

Actual owner-approved HA OAuth and Core config-flow lifecycle; two-sided encrypted network endpoints; TV proof from a real authenticated Assist + scoped alerts WebSocket, not pure-test state; verified Keystore activation and refresh/revoke; persisted crash recovery and previous manual login rollback; trusted transport especially for local HTTP Home Assistant installs; full onn 4K Pro QA and issue #214 independent audit.

Keep PR draft and do not merge into main/dev/#206 or publish until end-to-end pairing is proven.
