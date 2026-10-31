# Draft: Safe ongoing Kellie's Voice TV login, not live yet

**Security-critical staging only. Do not install this branch as an
automatic-login build or claim that the v3 owner receipt now logs a TV in.**

## Confirmed against Home Assistant Core 2026.10.2

1. HA's ordinary `system-users` account, even though not an administrator,
   has `{"entities": true}` and can control every entity. Admin and owner
   access is broader. Merely issuing a normal User token does **NOT** provide
   safe per-TV permissions. Refer to HA Contributors' Permissions reference.
2. HA supports `hass.auth.async_create_refresh_token(user, client_id=...)`,
   `async_create_access_token`, and revocation by refresh token ID. Official
   OAuth `/auth/token` supports `grant_type=refresh_token` with `client_id`,
   returning a 30-minute access token under its normal configuration.
3. For normal refresh tokens, HA expires the grant after 90 days of no use.
   The token is per-user; use a dedicated local-only restricted account,
   **never** the owner account or the default `system-users` group.
4. HA 2026.10 does not offer a normal UI for creating a custom exact
   per-entity policy group or guarantee that every integration or Assist
   executor checks those policies. We **cannot** safely manufacture one
   with the normal Users screen, and we will NOT recommend hand-editing
   `.storage/auth` to bypass that restriction.

Docs: https://contributors.home-assistant.io/core/internals/auth-api/
and https://contributors.home-assistant.io/core/internals/permissions/

## Implementation in this draft

- `credential_scope.py` is the earlier strict preflight. The new
  `scoped_login_issuer.py` uses HA AuthManager only for an actual,
  active, nonowner, nonadmin, nonsystem, **local-only** user belonging to a
  custom exact-entity restricted group. It checks the current complete
  inventory, access to each permitted entity, absence of unrelated
  permissions, and owner identity. It checks again immediately before
  issuance, so a policy change aborts enrollment.
- The new `token_lease_journal.py` records a unique per-TV issuance intent
  on disk using atomic fsync **before** HA creates any refresh token. It
  stores only token IDs and approved account metadata, **never** bearer or
  refresh token secrets. A crash before final channel handoff is recoverable
  by the per-attempt `client_id`. Incomplete enrollment is revoked on
  restart, including a token created just before a crash.
- HA-issued grants are **normal short-lived access/renewable refresh tokens**,
  never 10-year long-lived owner tokens. Each is identified by TV UUID,
  user ID, token ID and unique client ID. Server owner-controlled revocation
  targets exactly that token even when the TV is offline.
- `TvRenewableLoginPolicy.java` verifies canonical TV identity, exact
  per-attempt client ID and HTTPS HA origin. `TvRenewableLoginVault.java`
  stages credentials in *separate* Android Keystore AES-GCM storage, refuses
  to activate them without a future authenticated server commit, and uses
  the official HTTPS `/auth/token` refresh grant from a background worker.
  Revoked/denied grants clear locally, with **no fallback** to the owner's
  existing manually saved token. This vault does not read or write the
  existing `CredentialStore`.
- Neither issuance nor vault is currently wired into HA startup, owner
  config flow, the TV pairing listener, or the stable app. Neither creates
  an actual account or credential on the user's HA device.

## Required gates to provide a genuinely safe, working ongoing login

1. **Real narrowly restricted identity.** Either HA exposes a supported
   per-entity account/group provisioning API, or build and independently
   review a separate restricted execution gateway for voice/alerts.
   The latter must enforce every command and not let Assist, scripts,
   services or camera streams bypass per-TV authorization. Default User
   permissions are NOT an acceptable fallback.
2. **Authenticated owner authorization.** Tie the live, unconsumed,
   one-use HA owner claim, physical TV ECDH transcript and TV UUID to
   one specific restricted account chosen server-side. No owner_id,
   account/user/group or device IDs from TV packets or mDNS are trusted.
3. **Confidential handoff.** Extend the verified v3 transcript with
   separately derived AES-GCM credential-delivery keys. TV stages the
   token encrypted at rest, but cannot use it yet. TV sends a verified
   stage ACK; HA sends authenticated commit-ready; TV activates and
   returns an activation ACK; HA fsyncs journal commit **last**.
   If HA restarts before journal commit, revoke the orphan token even if
   the TV briefly activated it. Every failed/expired transfer closes keys.
   Do not place token strings in URLs, discovery, YAML, logs or screenshots.
4. **Actual lifecycle integration.** Feed only the vetted access token
   from the vault into Assist and the separate alert WebSocket, with
   refresh-before-expiry, reconnect, local-only network rules, and explicit
   Forget/owner Revoke. Never connect over plaintext HTTP with any
   bearer or refresh token.
5. **Hardware and adversarial validation.** Two TVs with different
   accounts/scopes, wrong device/replay, stolen IP, revoke while offline,
   HA+TV reboot, revoked TV account, camera/media restrictions, Assist
   prompt injection, device erase, network TLS failure and 90-day idle
   re-enrollment. Independent security review #214 must approve.

## What users can test now

The existing full-app beta #258 can test Assist, spoken responses, overlays,
wake word, Frigate, cameras and announcements using its **separate existing
manual HA login**. The v3 encrypted owner-approval receipt from #257 is
also testable. This new PR is *offline only*, with unit tests for
preflight, token creation, encrypted storage shape, crash recovery and
exact-ID revocation. Installing this PR does not enable automatic login.

**No Home Assistant tokens were issued to the user's account by writing
this PR. Never merge to public main or ship a credential path while these
gates are incomplete.**
