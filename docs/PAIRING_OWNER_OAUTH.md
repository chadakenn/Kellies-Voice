# #193 owner OAuth bridge: development security design

**Status: standalone, offline PKCE/URL/callback guard only. Not yet wired to a running Home Assistant auth route, TV or credential store.** This is a mandatory engineering step toward [issue #193](https://github.com/chadakenn/Kellies-Voice/issues/193), now required before the v0.17.0-dev.1 public prerelease.

## Why a real login is required

Home Assistant's config flow can create a TV device entry without authenticating the TV to Assist. Existing `CredentialStore.java` encrypts an owner-entered URL and bearer token, but does **not** contain OAuth refresh-token logic. Discovery and ECDH verification do not authorize an HA account by themselves.

Home Assistant supports **OAuth2 IndieAuth** authorization-code login, optional PKCE S256 and refresh-token grant. The owner must explicitly approve the client using Home Assistant's login UI; neither this integration nor Android can silently mint a user token just because it knows a TV UUID. A refresh token represents the consenting HA user and is **not intrinsically a per-TV permission-limited token**. A dedicated restricted HA account is an option to evaluate, not an unverified default.

Primary source: https://contributors.home-assistant.io/core/internals/auth-api/

## Browser callback feasibility (must be proved on a real HA instance)

The first testable design uses the actual HA instance origin as the IndieAuth client ID, and a callback path **on that same HA instance**, so the user can perform login in an ordinary phone or PC browser rather than a special companion app:

- Client ID: `https://ha.example.org/` (or a local-network `http://192.168.x.x:8123/` URL)
- Redirect URI: `https://ha.example.org/api/kellies_voice/pair/oauth_callback`
- Authorization endpoint: `<ha-origin>/auth/authorize`, with `response_type=code`, random 256-bit state, `code_challenge_method=S256` and independent random PKCE verifier
- Future token endpoint: `<ha-origin>/auth/token`, code/verifier/client ID exchanged once **only if** owner-approved and TV-OK-verified, with secure server-side HTTP behavior

The callback endpoint is **NOT registered or reachable yet**. Explicit review and live HA test are required to establish that using the HA origin for an integration client ID is supported for each locally installed URL, including proxy/HTTPS and HA MFA flows. If HA rejects this client identity, use a real Kellie's Voice HTTPS client metadata document and a vetted redirect, never impersonate the official Home Assistant companion client ID.

The new `pairing_owner_oauth.py` is an **offline helper** with a canonical HA-origin check, local-network-only HTTP, exact same-origin callback, one-time two-minute PKCE state, owner+physical TV consent flags, TV-session binding and state/code/replay/expiry checks. It contains no listener, browser action, HA config flow registration, HTTP token exchange, persisted token or credential transport.

## Real implementation still required

1. **HA UI and callback:** authenticated and consented HA-owner step in `config_flow.py` after verified two-screen code, with a short-lived, one-use callback registered through supported Home Assistant APIs. Bind HA owner/session and TV device ID to the same ECDH transcript, and reject CSRF, wrong origin, stale login, proxy/redirect changes and unknown user. No automatic HA admin token.
2. **Token exchange:** explicit browser approval and exact PKCE S256 validation, using the supported HA token endpoint. Revoke any partially created refresh token if provisioning fails.
3. **Private provisioning:** ephemeral, transcript-bound `PairingSealedChannel` AEAD messages only, never the public `/v1/pair/offer` or `/v1/pair/status` routes. The user-confirmed TV and HA owner must jointly authorize the same session; encrypt URL and OAuth tokens, never emit them in mDNS, logs or unencrypted HTTP.
4. **TV credentials:** extend Android Keystore storage to hold client ID, refresh token and access expiry without losing the existing manual connection, then perform secure refresh and revocation via `/auth/token`. Do not replace an existing working login until the newly provisioned token proves it can open an authenticated Assist WebSocket and scoped alert receiver.
5. **HA integration:** create or update exactly one TV config entry after Assist proof; supply Disconnect/Re-pair with user-readable expiry/revocation behavior; never confuse a discovered identifier or verification-only preview with Connected.
6. **Physical QA:** onn 4K Pro, blank install, two TVs, different networks/VLANs, wrong code, cancel, expired/replayed/forged nonce, credential rollback, HA reboot, app reboot, revoked permissions, fallback manual login, no unsolicited mic/wake.

## Release contract

- No automatic pairing claims in documentation or release notes until full stages 1–6 pass.
- Do **not** deploy a credential endpoint just because both sides know the short visible code; the full ECDH transcript, exact owner consent and AEAD are mandatory.
- Remain **draft and off main** until independent security review and owner hardware QA. Existing v0.16.14 users and v0.17 beta candidate remain unaffected until a reviewed stack is explicitly integrated.

