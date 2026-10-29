# HA TV token crash recovery (offline security QA, #214)

This branch **does not issue any refresh tokens** or change the user's
Home Assistant installation. It is stacked on #239, the offline
non-admin identity/revocation guard. Do not install or release it.

## Failure we are preventing

In the earlier OAuth draft the server may issue a bearer refresh token, but
only save its token ID in an in-memory holder **after** the exchange. An HA
crash in that gap leaves an active token with no durable record. Saving the ID
later cannot fix a crash that already happened.

## Durable pre-issuance intent, and how it recovers the gap

1. Before any future HA token issuance, the owner-approved flow MUST call
   `TokenLeaseJournal.begin_intent` and **await successful disk persistence**.
   A random 128-bit nonce makes a unique per-attempt OAuth `client_id` such
   as `<HA-origin>/kellies-voice/tv/<uuid>/attempt/<nonce>`.
   User ID, owner ID, canonical TV UUID and client ID are stored without
   refresh or access token secrets.
2. Only after that fsynced write returns can a future reviewed component
   attempt to issue an **explicitly non-admin device token** with the exact
   client ID. **HA Core IndieAuth acceptance of that candidate path remains
   unverified** and is a hard blocker before using it.
3. Once issued and validated, `record_id` stores the HA refresh-token ID.
   After full TV Assist/alerts proof and atomic TV credential promotion,
   `commit` marks the record committed (the reaper never revokes it).
4. On restart, **before enabling pairing**, `recover_unfinished` checks
   saved pre-issuance intents. It asks HA AuthManager for exactly the
   recorded dedicated TV user and enumerates that user's refresh-token
   records. It only selects tokens whose `client_id` matches the unique,
   persisted nonce/TV ID and whose user ID matches. This detects a token
   issued before the ID was recorded. It persists `revoking`, calls HA
   AuthManager's `async_remove_refresh_token` synchronously, then persists
   `revoked`. A missing, ambiguous or inconsistent identity, storage
   failure or failed revocation retains the record for retry or intervention.
5. A record marked `committed` is preserved for a later Disconnect flow
   that will use the exact token ID from #239 to revoke the selected TV
   even while offline. A future implementation must add that controlled
   action separately.

## Durability and scope

The isolated prototype uses a **private JSON journal** written via
`hass.async_add_executor_job`. It writes a temporary 0600-permission
file, fsyncs file contents, atomically replaces the journal and fsyncs
the containing directory on POSIX. It refuses corrupt/unsupported files
without overwriting them.

A normal Home Assistant `Store.async_save` helper is **not assumed** to
provide a synchronous durable write on all failures. In current HA Core
the helper logs and can swallow write errors, which is insufficient for a
mandatory pre-issuance safety barrier. A future integration may adopt a
reviewed supported durable-store wrapper with equivalent guarantees.

The journal contains only IDs and an HA origin. No access/refresh secrets,
passwords, API keys or device private keys are recorded.

## Missing release gates

- This class is **not yet registered in Home Assistant**, nor hooked to any
  token issuer. No actual OAuth code exchange or TV credential transfer.
- The initiating HA user MUST be authenticated (#234, #235). A TV's mDNS
  data or UI form cannot choose an owner or TV credential user.
- The assigned TV user must be provably non-admin, with truly limited
  Assist and per-device alert permissions. A generic non-admin HA account
  may still have more capabilities than intended.
- HA Core 2026.10 must be tested to accept distinct per-attempt client IDs
  and to enumerate only the intended user's tokens; a wrong PKCE verifier
  must fail. The use of internal HA auth APIs requires maintainer review.
- Persisted pairing intent and HA AuthManager's own asynchronous token store
  are not a single database transaction; the final implementation must
  address lifecycle ordering, storage failures and token-list consistency.
- A storage write failure following server-side revocation may leave an
  `issued/revoking` journal record. The recovery function is designed to
  repeat safely, but must be tested against actual HA Core.
- Backend #234 authenticated owner claim has no normal frontend CTA yet.
- Security PR #236 commit/reveal is an offline v3 primitive; both network
  peers must use v3 with downgrade refusal BEFORE real credentials.
- TV Keystore atomic promotion, authentication proof and controlled offline
  Disconnect are not part of this branch.
- Real tests on a local-only Home Assistant and Nabu Casa backed instance
  with multiple TVs, failed writes, abrupt restart and revocation required.

## Test scenarios

`tests/test_pairing_token_lease_journal.py` exercises disk persistence,
0600 file permissions, crash **after token issuance but before ID save**,
crash **after ID save but before TV acknowledgment**, exact-ID isolation,
committed-token retention, ambiguous identity denial, disk failure,
revoke retry after restart, stale/invalid journal refusal, and duplicate
TV issuance blocking. All tokens and users in tests are synthetic.

**Keep PR DRAFT. Never enable credential issuance until the ownership,
permission and version-dependent API gates are independently reviewed.**
