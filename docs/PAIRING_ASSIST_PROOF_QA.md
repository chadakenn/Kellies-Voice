# #193: Temporary Assist login proof on the TV (research / QA primitive)

**Status: not wired to the pairing listener or user credentials, not public beta-ready.** After HA owner authorizes a candidate token and the encrypted provisioning session has delivered it to the correct TV, the TV must prove that token works **before** replacing any existing working login.

## What this draft supplies

- `PairingAssistProofState`: strict order: HA `auth_required` → TV authentication attempt → HA `auth_ok` → read-only `assist_pipeline/pipeline/list` reply with exact request ID and `success=true` → successful Assist capability proof. Wrong order, duplicate challenge, invalid response, unexpected ID and cancellation fail closed.
- `PairingAssistConnectionProbe`: one-shot read-only WebSocket, at most 15 seconds, using an already-validated staged `PairedOAuthCredentials` for a verified TV pairing session. **Requires trusted HTTPS** with ordinary platform TLS certificate validation and disables redirects; does NOT send secrets over plaintext local LAN HTTP. Never records audio, invokes an Assist pipeline, stores credentials or starts the mic. Callback once on Android main thread.
- Tests: `PairingAssistProofStateTest` and `tests/test_pairing_assist_probe.py`.

## What this does not prove

- A successful response means the token authenticates to this HA server and it can list Assist pipelines. It does **not** prove the token can actually run a voice pipeline, subscribe to scoped Kellie's Voice alerts, access selected device entities or refresh after expiry.
- The probe cannot prove TV identity or HA owner consent. Those must already have been verified by HA OAuth/PKCE + TV remote OK + session-bound ECDH/AES-GCM *before* the probe receives a candidate.
- It does not create a config entry, mark pairing Connected, activate saved credentials, start a public listener, support `http://` local HA servers, or replace the old manual token.
- Do not run this from the real main Activity until encrypted provision, additional scoped alert permissions and atomic promotion/rollback have been reviewed.

## Remaining checks before v0.17.0-dev.1

1. Review secure browser callback and owner context in Grok's [#210 OAuth research](https://github.com/chadakenn/Kellies-Voice/pull/210) (a public callback does **not** automatically inherit HA owner identity).
2. Implement owner-approved HA OAuth, precise TV binding and authorized encrypted provisioning, then stage access/refresh in Android Keystore.
3. Confirm read-only Assist auth **and** scoped `kellies_voice/subscribe_alerts` for the correct paired device. Failure means revoke the provisional refresh token and delete pending credentials without losing the user's old working login.
4. Implement refresh/revocation under approved HTTPS/secure tunnel rules; preserve existing sessions/reconnect behavior.
5. Test fresh onn TV setup, bad code, replay, expired/revoked credentials, two TVs, VLAN/no mDNS, app/HA reboot and TLS/redirect refusal.
6. Only after successful hardware tests and security review, integrate with the matching theme/main/shortcut beta candidate [#206](https://github.com/chadakenn/Kellies-Voice/pull/206).

No production changes; all pairing branches remain draft.
