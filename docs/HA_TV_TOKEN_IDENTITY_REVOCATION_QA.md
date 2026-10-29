# Claude #214 handoff: non-admin device credentials and offline revocation (QA only)

This module is **not connected to an OAuth exchange, Home Assistant setup flow,
a TV network endpoint or the currently installed HACS integration**. It is a
reviewable no-credential-changing prototype for two specific blockers:

- **H1:** The existing OAuth prototypes would export the approving HA owner's
  admin/owner refresh token to the TV. That is unacceptable.
- **H4:** Pending transactions currently lose their only refresh-token handle
  on HA restart, making orphan credential revocation impossible.

## Checked against actual Home Assistant AuthManager source

Home Assistant's AuthManager implements:

- `async_validate_access_token(access_token)` returning the associated
  `RefreshToken` or `None`, which includes its user and token ID.
- `async_get_refresh_token_by_token(refresh_secret)` for the secret's
  refresh-token object.
- `async_get_refresh_token(refresh_token_id)` for ID-only lookup.
- `async_remove_refresh_token(refresh_token_object)`, which invalidates
  the matching refresh token and its issued access tokens.

These are **core internals** and must be version-tested on supported HA Core
releases before integration. The experimental code never calls the
create/exchange methods or writes anything to the owner's live HA.

## Implemented security checks

`verify_nonadmin_tv_candidate(...)` requires that the presented access token
and refresh-token secret resolve to the **same HA refresh-token ID**, exactly
one active, non-admin, non-owner, non-system user, a specific separately
assigned TV user ID **different from the approving owner**, a normal
(not long-lived/system) token type and an exact per-TV `client_id`.

Any wrong or missing HA token, changed username, unrelated client, rotated
token identity, expired access token, admin/owner privilege or system user
**fails closed**. It returns a frozen `DeviceTokenHandle` containing only:
canonical TV UUID, user ID, refresh-token ID and approved client ID. No
refresh-token secret, access token or owner credential appears in this handle.

`revoke_verified_device_handle(...)` looks up the exact token by ID inside
the HA AuthManager and checks user and client ID before calling the local
synchronous revocation API. It can revoke while the TV is offline and does not
need the refresh-token secret. It returns false when already gone or when
the identifier does not belong to the expected TV user/client. Failed
revocations raise an opaque exception, so the owning durable journal must
retain and retry the record instead of deleting it.

## Critical remaining blockers before any live connection

1. **How to issue a TV-only credential:** do not simply send the
   approving owner's OAuth credential. HA OAuth authorization belongs to the
   browser user, which is frequently admin/owner. The production design
   must identify a supported way to provision a **separate constrained user**
   without silently creating or elevating accounts. Ordinary non-admin
   users may still have broader entity read/write access than the desired
   per-TV permissions. Demonstrate actual Assist execution and scoped
   alerts using that user on real HA.
2. **Durable, atomic restart recovery:** this draft does not persist any
   lease. The production integration must save an HA-owned pending
   transaction before issuance and record the token ID **before any TV
   provisioning**, then load its durable journal on HA restart and revoke
   pending/orphaned entries not marked committed. A crash after issuing a
   token but before storing its ID must be recoverable. The per-TV client
   ID is a possible recovery index only if the actual HA IndieAuth rules
   permit it and token enumeration is reviewed. A process-local dict is
   NOT sufficient.
3. **Live URL authorization and authentication:** `proposed_tv_client_id`
   is a *candidate* URI for each TV, not a proven HA IndieAuth registration.
   Validate it on HA 2026.10 using a non-admin account with a wrong-PKCE
   verifier test. Do not send OAuth codes over local plaintext HTTP.
4. **Identity binding to real owner:** connect #234's authenticated HA
   WebSocket `connection.user` claim and the one-click approval UI in #235
   with the exact TV UUID/session. Do not accept arbitrary user IDs from
   mDNS, the TV, forms or a public OAuth callback.
5. **H2 wire upgrade:** #236's v3 key commitments are offline and not yet
   integrated into both peers. No credential can be encrypted to the v2
   code-only handshake. Reject downgrade to v2 for any credential delivery.
6. **Revocation failure + user downgrade/upgrades:** do not depend on
   the TV responding or a current non-admin permission claim to revoke.
   Keep a durable revoke-needed record until AuthManager confirms absence.
7. **No credentials on success screen:** code comparison and TV OK are NOT
   login or permission grants. Claim “Connected” only after TV authenticated
   Assist and the authorized alerts, with atomic Keystore promotion and
   crash/rollback testing.

## QA

`tests/test_pairing_device_token_guard.py` uses only synthetic in-memory
tokens and fake AuthManager methods. It covers owner/admin/system rejection,
invalid/expired/mismatched token pairs, per-TV isolation, malformed objects,
offline token-ID revocation, idempotency, failure retry and no secret in
the returned handle/exception strings. CI explicitly verifies this branch
adds no token-exchange network endpoint or credential issue method.

**Keep draft and main unchanged. Do not install this QA package into live
Home Assistant. The current manually configured Kellie's Voice TV stays
connected normally.**
