# HA-first secure TV pairing: production integration gate

**Status: draft on PR #292. Do not publish this branch to ordinary HACS users yet.**

## Expected owner experience

1. Install the ordinary **Kellie's Voice** Android TV APK and HACS integration, then restart Home Assistant once.
2. Open the TV's First-time setup wizard / Secure Home Assistant pairing, using the TV remote. No Android keyboard, URL, token or raw device ID.
3. Home Assistant discovers the temporary TV pairing session. Select the TV, compare the same eight digits, confirm on the TV remote.
4. In Home Assistant, an authenticated **owner** approves that exact device and explicitly opts in to a dedicated, non-admin TV account (not the owner's token). The consent warns that Home Assistant's ordinary **Users** group can control all entities.
5. Home Assistant provisions a renewable login over the authenticated pairing channel and waits for the signed TV credential activation ACK, **then** creates or preserves the normal device entry.
6. On reboot, the TV reuses its Keystore-protected refresh grant, reconnecting Assist and an enabled alerts subscription without typing again.

Legacy manually configured TVs must continue working, and upgrading must not overwrite their credentials. No microphone or alerts are enabled simply by pairing.

## Proof already collected

- **Isolated Beta #260, real Onn 4K Pro:** matching codes, TV physical OK, independently authenticated HA owner approval, dedicated TV account activation and actual authenticated Assist light control all passed on Oct 30, 2026.
- **Stable v0.17 RC #290, signed QA #183:** four-camera screensaver, fullscreen sound, themes and Quick Actions passed Oct 31.
- **This PR #292:** moves the owner-approved login engine into the normal HACS namespace and enables the production TV setup path. Synthetic tests and CI are not a substitute for the missing production first-install and security tests.

## Required non-owner hardware / automated release verification

- First installation with no existing `kellies_voice` config entries: owner panel must register even if HA HTTP router is already frozen; the onboarding must not need an extra `kellies_voice_v3qa:` YAML line, ZIP, command line or additional integration.
- Normal HACS TV entity and alerts registration on first pairing, including close/re-open and multiple TVs.
- Owner denial, wrong/expired eight-digit code, replays, token provisioning failure, connection loss, ACK failure, reconnect and enrollment rollback.
- 24+ minute access token refresh; full HA/TV reboot; revoked login; owner disconnect/re-pair; two-TV identity isolation.
- Local-only HA **without** Nabu Casa or HTTPS: current beta transport refuses persistent login over plaintext HTTP (#262). A reviewed, usable, secure no-paid-cloud option is required to claim universal zero-typing.
- Independent #214 security review and fixes: broad non-admin user permissions, user/revocation lifecycle, logs, replay/MITM, HA origin and token journal crash recovery.
- Full Android and HACS tests plus a new signed preflight built from the *exact merged source SHA*.

**Owner has explicitly finished manual QA.** Do not repeatedly ask this owner to test. Use CI and independent beta/security reviewers. Keep public v0.16.14 untouched until the production gate passes; do not silently downgrade to a manual-token installation while advertising automatic pairing.
