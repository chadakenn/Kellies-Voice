# #193 B7: Staged OAuth credentials in Android Keystore

**Experimental, source-only storage. Not connected to a pairing listener, Home Assistant login, the Assist socket, or the stable app's saved account.**

An authenticated automatic setup will eventually deliver access and refresh credentials to the TV. We must NEVER call the old `CredentialStore.save(url, token)` first and then attempt to validate a login: a failed new login could erase the user's working credentials.

This branch creates a **separate, encrypted *pending only* slot**, intentionally without any public method to promote or activate credentials:

- `PairedOAuthCredentials`: immutable metadata validated against an exact canonical HA origin and client ID, canonical 16-byte random pairing session, RFC 4122 v4 TV UUID, bounded token strings, and explicit access expiry. A pending pairing is usable only for 120 seconds and never after a backward wall-clock jump. There is no token-bearing `toString()`.
- `OAuthCredentialStagingStore`: independent AndroidKeyStore AES-256-GCM key and preferences namespace, per-record random 96-bit nonce, AEAD domain-associated data, synchronous commit, tamper/expiry/wrong-TV/wrong-session rejection and explicit `discard()`. Repeated or invalid pending state cannot be turned into a new active login.
- Both classes are currently **unreferenced by the live Activities/Assist client**. Staging does not open a network connection, install a login, refresh OAuth, create an HA device or enable a microphone. Existing `CredentialStore` content and alias remain unchanged.

## Next steps required for actual automatic login

1. Complete HA OAuth owner login and callback via an authenticated Home Assistant setup flow, using PKCE and a revocable user-owned refresh token ([#207](https://github.com/chadakenn/Kellies-Voice/pull/207), [#208](https://github.com/chadakenn/Kellies-Voice/pull/208)).
2. Complete session-bound credential envelope sent **only** over the approved ECDH/AES-GCM channel after matching eight-digit TV code and physical OK ([#203](https://github.com/chadakenn/Kellies-Voice/pull/203)).
3. Integrate a strictly temporary local TV staging action that decrypts the envelope, verifies expected device and session and stages credentials for at most two minutes. No secret on open discovery/status routes.
4. Authenticate a **fresh Assist WebSocket and scoped TV alerts receiver** using the pending access token. Only after both proofs, atomically promote the new OAuth identity to active storage and retain refresh/revoke metadata, preserve a recoverable prior connection until validation, then close the onboarding listener and clear all ephemeral keys. A failed proof must discard the pending state and revoke the unused token without altering the old connection.
5. When auth expires, refresh via Home Assistant's supported `/auth/token` refresh grant. Never send the refresh token in cleartext over a private LAN HTTP connection; HTTPS or an authenticated encrypted device tunnel is required. Document supported local networking, IPv6 and reverse proxy behavior. Enable Home Assistant owner-side Disconnect/Re-pair.
6. Test with a real **onn 4K Pro**, stable and QA package identities and full HA configuration backups; Android Keystore behavior needs real-device QA, not just mocked JVM tests. Verify no residual pending token after cancel/expiry/reboot, wrong TV session, clock drift, revoked permissions, failed HA auth, or installing over an existing valid login.

## Current tests
- Pure JVM `PairedOAuthCredentialsTest`: strict session, UUID, origin, secret length, expiry and code mismatches.
- Python source contract `test_tv_oauth_staging.py`: pending-only, separate keystore alias, encryption, cleanup, legacy CredentialStore unchanged and no network or microphone code.
- Existing unit/native APK CI must compile the new AndroidKeyStore staging helper. Full Android instrumentation, QA signing and end-to-end token refresh are future gates.

**No release and no user hardware test claim.** Stable v0.16.14 is untouched. `v0.17.0-dev.1` stays blocked by [#193](https://github.com/chadakenn/Kellies-Voice/issues/193).
