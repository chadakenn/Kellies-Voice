# #193 B11: TV activation gate and isolated verified OAuth storage

**DRAFT. This does NOT connect the live application to OAuth credentials.** It cannot automatically pair, set the app Connected, swap an existing Home Assistant token, send audio, or provide a token-refresh service. It is the separately testable TV-side commit boundary for the future verified end-to-end flow.

## What changed

- `PairingActivationGate`: pure Java, deterministic, short-lived state machine. Must observe *both* the read-only Assist pipeline and same-TV scoped-alert subscription proof from #215, then a **separately authenticated, transcript-bound, encrypted Home Assistant commit acknowledgment** for the same device UUID and session. Only then is it eligible for persistence. Two-minute lifetime is based on monotonic `SystemClock.elapsedRealtime()`; failures, wrong TV, wrong session, duplicates and expiry fail closed. This gate **does not cryptographically validate an HA ack itself**. The future caller must do so before invoking `authenticatedHaAck`.
- `OAuthVerifiedCredentialStore`: completely separate non-exportable Android Keystore AES-256-GCM key and preferences namespace. Can store an approved OAuth URL/client ID, access/refresh token, expiration and TV UUID **only after** `gate.canCommit` and the two proofs. Rejects plaintext HTTP token transport and refuses to overwrite an already verified OAuth record until an independently reviewed re-pair rollback plan is implemented. Read-back uses authenticated encryption and exact TV binding. No logging or plaintext disk persistence.
- `CredentialStore` (existing manual token), `OAuthCredentialStagingStore` (temporary encrypted candidate), `HaAssistClient`, `TvEventsService` and `PairingDiscoveryWindow` are untouched. **Nothing currently reads this new verified slot for production Assist.** CI checks source/JVM isolation; real Android Keystore round-trip requires device tests.

## Still blocking a genuine public release

1. HA-owner OAuth callback (#216) and single-use exchange/pending leases (#217) must work against an actual HA instance. Verify supported IndieAuth client identity/redirect and secure HTTPS or locally tunneled token transport.
2. HA encrypts the bounded token payload (#212) for exactly the matching physical-TV-approved ECDH transcript (#203) and sends it over a **new single-use authenticated AEAD-only delivery protocol**. No tokens on discovery, offer or status routes.
3. TV opens the channel, validates the authenticated plaintext envelope, stages in `OAuthCredentialStagingStore`, and runs `PairingAssistConnectionProbe` **without changing the manual login**. Both Assist and scoped TV-alert permissions for the exact TV must succeed (#215). The HA config entry and its permission-bearing select entity must already exist or this proof fails closed.
4. HA sends a distinct **encrypted per-session commit acknowledgment** only after the expected owner, device, token and TV proof receipt agree. A boolean `true` or an unauthenticated HTTP status is **not** an HA commit acknowledgment.
5. A reviewed coordinator can then call `OAuthVerifiedCredentialStore.saveAfterProof`, migrate the *new* OAuth session into the live HA client with proper expiry/refresh and remove the pending slot. On any failed step, the HA pending transaction retains the refresh token to revoke, the new candidate is discarded, and the old manual connection remains usable. Encrypted preference commit, app crash/reboot and prior OAuth account replacement require explicit rollback testing.
6. Test physical onn 4K Pro QA APK and matching HA integration: HTTPS reverse proxy and local HTTP fallback, two TVs, no mDNS/VLAN, denied alerts, no pipelines, wrong owner/code/session, duplicate/late HA ack, expired/revoked OAuth, HA restart and Android reboot; perform independent security review (#214) before public publication.

### Known limitations

- This branch's storage API is *not wired* to the current UI, home screen, network listener, OAuth refresh or Home Assistant setup wizard. Green unit tests will not prove actual automatic pairing.
- The stricter HTTPS policy means a typical Home Assistant plain HTTP local IP is **not** eligible for live token validation in the current draft. Manual setup remains a fallback pending a fully authenticated encrypted tunnel or trusted HTTPS support.
- Android Keystore and SharedPreferences cannot supply a multi-device atomic transaction with HA. We need an explicit two-phase acknowledgment/compensating revocation protocol, including crash recovery.
- The activation gate manages state, not consent cryptography. Caller is responsible for verifying HA owner consent, TV remote OK, AEAD transcript, and per-TV ack.

**Release candidate #206 stays blocked, and stable v0.16.14 is untouched.**
