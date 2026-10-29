# #193: Read-only Assist + TV-alert authorization proof

**Draft only.** This code is not connected to the network pairing listener or a live user account, and it cannot activate or replace the current login.

## What the TV must prove before a future credential commit

1. Pending credentials are bound to the expected OAuth client, actual HA origin, exact TV UUID and active two-minute pairing session, previously authorized by the HA owner and confirmed with TV OK.
2. A one-shot HTTPS WebSocket (platform TLS, no redirects) receives `auth_required`, then successfully authenticates the candidate token with `auth_ok`.
3. The read-only `assist_pipeline/pipeline/list` command returns a matching successful result with at least one pipeline.
4. Only then may the TV ask `kellies_voice/subscribe_alerts` for the **exact same TV UUID**. A matching `success: true` result proves HA has accepted that user's read access to the correct paired TV.
5. The temporary subscription is removed and the short-lived socket closed. **Neither check activates credentials, turns on the microphone, or marks pairing Connected.**

Auth rejection, empty pipeline list, unknown command, wrong TV access, wrong result ID, out-of-order event, retry beyond 15 seconds, TLS/certificate failure and cancellation fail closed. HA may temporarily return `tv_not_ready` while the TV select entity is loading; this helper fails, rather than weakening permission checks. An eventual real onboarding coordinator needs a bounded, secure retry with the original session lifetime.

## Limitations

- A successful read-only query is not proof an actual STT/TTS run or audio output works.
- A successful scoped alert **subscription** is not proof future alerts will arrive or that the token has broad device control rights.
- The OAuth client ID/callback must still be validated on real HA; the owner consent + TV verified encryption channel is not connected.
- Plaintext local HTTP for OAuth credentials remains blocked; support requires a reviewed end-to-end authenticated encrypted channel or trusted HTTPS.
- No Android Keystore activation, background token refresh, token revocation, owner-side unpair or failure rollback is implemented by this helper.
- Requires the matching HA integration and registered paired TV config entry before the alert authorization check can pass.

## QA before beta

- [ ] JVM state tests pass (auth only, Assist only, no pipeline, alerts denied, wrong ID, replay, cancellation).
- [ ] Android debug/native JNI APK compiles on the exact commit; HA/HACS CI passes.
- [ ] Real onn 4K Pro with the *matching* test HA integration: authorized user with TV select read permission passes; user without read permission fails.
- [ ] Two TVs with separate UUIDs: a token for one TV cannot prove scoped alerts for the other.
- [ ] Test reconnect and restart, expired/revoked tokens, stale HA select and no-mDNS/VLAN fallback.
- [ ] Independent security review and actual first-install OAuth->AEAD->Keystore->Assist+alerts->atomic-activation path, with rollback preserving any existing working account.

Source: Home Assistant WebSocket API (`auth_required`/`auth_ok`, subscriptions and `unsubscribe_events`) https://contributors.home-assistant.io/core/internals/websocket-api/ .
Related: #193, #211, #212, public beta candidate #206.
