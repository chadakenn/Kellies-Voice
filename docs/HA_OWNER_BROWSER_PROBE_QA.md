# HA owner browser authorization preview: QA only (#193)

**Token-free experimental draft. This is NOT automatic TV login.**

## What this stage does

1. Home Assistant and the TV complete the already-tested eight-digit visual comparison and physical TV remote confirmation.
2. Home Assistant verifies the actual session/TV UUID-bound P-256 ECDH proof.
3. Only then does HA offer a separate owner browser authorization preview, with an explicit TEST ONLY warning and unchecked opt-in.
4. The flow requires the server-provided user ID, and checks that it belongs to an active Home Assistant owner. No identity from mDNS or TV is trusted.
5. The backend asks Home Assistant's supported network helper for a trusted HTTPS address using require_ssl=True, allow_internal=False, allow_cloud=True, prefer_cloud=True. A Home Assistant Cloud/Nabu Casa HTTPS remote URL is eligible. Plaintext HTTP, including private LAN HTTP, is forbidden for authorization codes. This URL is selected by Home Assistant, never supplied by an unauthenticated TV advertisement.
6. The browser goes to Home Assistant's own OAuth authorize screen using a new 256-bit state and an independent S256 PKCE verifier.
7. A same-origin callback resumes only the exact initiating flow and TV session, consumes the state once, validates the callback and DISCARDS the authorization code and verifier.
8. The flow stops with owner_browser_probe_complete; it does not install any TV credentials or create a config entry.

## Explicit limitations

- No /auth/token request, refresh token, credential store, per-TV alert permission, or TV auto-login.
- A zeroconf flow may not contain a user ID. If owner context is absent, the owner-probe step must stop rather than guess the user.
- This dry-run cannot prove that the actual person approving OAuth in the browser is the same user as the initiating owner because the authorization code is deliberately not exchanged.
- The HTML callback URL is registered only if the experimental component is installed. Do NOT install it without review or overwrite a working HA instance automatically.
- The prefilled eight-digit comparison field and TEST ONLY notice stay visible even when HA translations show raw field keys.
- Both the 120-second OAuth state and the earlier physical TV verification have strict expiry and one-use checks.
- A trusted HTTPS HA URL is required. If only HTTP is configured, this will abort without opening authorization.
- A failed callback may leave a browser window needing manual closing, but it cannot create a token in this draft.

## Tests and next work

Dedicated CI covers cryptographic preview, verified owner context, missing owner, HTTPS-only policy, PKCE URL and one-use state, bad/expired/replayed callbacks, owner/session binding, and bypass attempts. It separately checks that token exchange and credential storage code are ABSENT from this branch.

Before any live token exchange, complete user identity verification after authentication, encrypted HA-TV message transport, durable token revocation on crash/restart, provisional TV alert permissions, Android Keystore credential promotion, independent security review #214 and real HA Core/onn hardware QA.

All OAuth drafts remain unmerged. Keep this PR DRAFT, no main/dev/#206 merge or public release.

## Using Home Assistant Cloud during the QA test

If a Home Assistant Cloud remote URL is enabled, the owner may open the Home Assistant UI through that HTTPS URL before starting pairing. Do not manually paste a cloud subdomain into the Home Assistant Network > Internet URL field; Home Assistant automatically provides its cloud URL through the network helper. The code deliberately prefers the cloud URL over an HTTP internal URL. It must never hardcode or log a user's private cloud subdomain, authorization code, or state.

The presence of an HTTPS cloud address does not prove that a zeroconf-created config flow contains an authenticated owner user_id. This remains a fail-closed runtime prerequisite. A missing owner context returns owner_probe_owner_required instead of silently assigning an owner.
