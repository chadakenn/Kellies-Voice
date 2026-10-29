# Authenticated Home Assistant owner claim (QA-only, #193)

## Why

Home Assistant's Zeroconf source creates a config flow on the backend, not an authenticated owner login. On HA Core 2026.10.2, config-flow contexts do **not reliably include a `user_id`**. The previous draft #231 incorrectly assumed `self.context['user_id']` represented a verified owner, so after TV success it would generally abort and never reach OAuth. The proper security boundary must read the user from a separate Home Assistant-authenticated request.

## What changed in this stacked draft

After the verified eight-digit code and physical remote P-256 proof, the flow registers an expiring (120 s), process-local record keyed by exact HA flow ID and exact canonical TV UUID + ephemeral session. The public TXT advertisement, user form and TV never specify or choose an owner ID.

New WebSocket command `kellies_voice/pair/claim_verified_owner` accepts only the verified `flow_id` and `tv_session`. HA already authenticated the request, so the handler reads `connection.user` and verifies `is_owner is True` and `is_active is True`. It records the actual owner user ID and prevents a second claim, replay, expired flow, wrong session, wrong TV, or ordinary/admin-but-not-owner user.

The next browser-consent step consumes this exact claim **once**, re-reads the owner using `hass.auth.async_get_user` to verify they are still active owner, then launches only the existing token-free PKCE browser probe. The callback never gets any owner's ID from mDNS, discovery, or a textbox. No HA config entry or TV credential is created.

## Remaining UI dependency, still BLOCKED

**The stock Home Assistant discovered-config-flow frontend does NOT automatically call a custom pairing WebSocket command.** Therefore this branch's owner authorization will still stop at `owner_probe_owner_required` until a reviewed authenticated owner approval control is implemented in the actual HA UI (or another supported HA-authenticated user-consent mechanism is approved). Never tell the owner to paste a user ID into the flow; do not trust `self.context` as a shortcut. A manual developer WebSocket invocation is a security/contract QA tool only, not consumer setup.

## Test-only safeguards

CI runs P-256 discovery tests, browser callback replay and expiration, non-owner/missing user/invalid UUID, per-instance isolation, owner claim one-use, HA WebSocket handler reading `connection.user` not client `user_id`, and no token exchange/credential files. Do not merge or install on a working HA instance yet. The 120-second owner claim timeout is intentional and may need a new TV proof if the owner approval UI is delayed.

## Production blockers

Claude (#214): independent auth/token privilege review. Grok (#232): Android first-run UX. Next engineering: build user-facing HA authenticated claim control; verify it on actual HA Core; only then implement owner-approved credential with least privilege, revocation, HA restart recovery, protected TV transport and Android atomic Keystore + Assist/alerts proof. Local-only users without Nabu Casa must remain supported.
