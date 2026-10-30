# V3 credential permission preflight: default-deny TV identity

**Security review / offline staging only.** This does not enroll a user, mint or validate a token, launch a login, write to HA, contact the TV, or change an existing manual connection. It must not be treated as a successful account authorization merely because the eight-digit TV verification and HA owner consent passed.

## Finding from Home Assistant Core 2026.10

HA's standard system-users group has a broad entities=true policy. A non-admin standard user can access and control **all** HA entities, even though they cannot administrate HA settings. Thus creating a generic non-admin account is insufficient for a TV-specific least-privilege identity. An admin/owner token is worse. Under the current HA API, no ordinary fixed user role proves scoped Assist and TV-alert privileges.

Official reference: https://contributors.home-assistant.io/core/internals/permissions/ . Source: homeassistant/auth/const.py, auth/models.py, auth/permissions/__init__.py (2026.10.2).

## Implemented exact-scope preflight

`credential_scope.check_restricted_tv_scope` is **not wired to the active v3 QA config-flow**. It only verifies a future, explicitly owner-approved, server-selected account object. It requires:

- Canonical v4 TV UUID and exact distinct owner and TV user IDs.
- Active, nonowner, nonadmin, nonsystem, local-only TV user.
- Explicit custom HA group(s), not any system-admin/system-users/system-read-only group.
- Every group policy is only an explicit `entities.entity_ids` map, never broad area/domain/device/all wildcards, `entities: true`, future-entity policies, or extra policy categories.
- Actual computed `user.permissions` must confirm no `access_all_entities` for read/control/edit.
- Exact read access to the TV's own alert-position select and read/control access to a limited set of explicitly selected Assist entities. Neither edit nor access to any unrelated currently enumerated HA entity is allowed.
- All requested entities must exist in a complete inventory. Missing/ambiguous/invalid information fails closed.

Return value is a frozen, non-secret, NON-authorizing record. It is not a token or permission grant, and its successful return is **still not enough to mint a token**.

## Remaining blockers to real issuance

1. Confirm a supported way on HA 2026.10.2 to create/maintain a dedicated custom restricted permission group with the required policy, or implement a separate backend-enforced scoped satellite proxy. Do not hand the TV a normal HA User account as a workaround.
2. Review whether Assist intents and every alert action are scoped by that selected user, not just entity permissions. HA docs explicitly warn that permissions are effective only when the target API checks them.
3. Independently audit the existing PR #239 token-identity guard and PR #240 durable intent/rollback journal. Confirm HA Core's IndieAuth client-ID rules, lifetime, revocation, non-admin user creation and restart behavior before any actual issuance.
4. Implement session-bound encrypted credential delivery with replay resistance and Android Keystore transaction/rollback. Do not send tokens in mDNS TXT, query strings, plaintext LAN endpoints, logs or configuration entry records.
5. Real two-TV isolation, full Assist + scoped alert verification, TV reconnect, network failure, revocation, expiration and crash recovery; security review #214 before beta.

Do not import or call this preflight from the live `kellies_voice_v3qa` owner flow yet. The owner flow intentionally completes as `owner_verified_no_login`. Keep `main`, stable HACS and installed TV untouched.

## Tests

`python -m unittest discover -s tests -p 'test_v3qa_credential_scope.py' -v` rejects default HA User group, owner/admin/system user, broad group grants and merges, unrelated entity permissions, missing required entity permissions, invalid values/IDs and lookup errors. It asserts no token field exists in a successful proof.