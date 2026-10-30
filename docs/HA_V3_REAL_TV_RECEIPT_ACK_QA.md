# Hardware QA: v3 TV receipt + authenticated acknowledgement

**Developer-only no-credential trial. This does not automatically log the TV into Home Assistant.**

Test the isolated Home Assistant integration `kellies_voice_v3qa` and the
**debug-build** Kellie's Voice v3 QA screen on an onn 4K Pro. DO NOT
overwrite `custom_components/kellies_voice`, restore a backup, install any
tokens on the TV, remove stable login, change home automations, merge `main`,
or publish this protocol in a production release.

## Preconditions

- Home Assistant configuration is backed up. The existing top-level
  `kellies_voice_v3qa:` YAML line is present and validates.
- Download the **isolated v3 QA HA ZIP** from this PR's passing
  `Isolated v3 QA HA ZIP (no credentials)` GitHub Actions run. It must
  contain the nested file
  `custom_components/kellies_voice_v3qa/frontend/kellies-voice-v3qa-owner-panel.js`.
  Extract to `/config/` preserving the full directory tree.
- Download the **debug APK** from this PR's passing `Unit tests`
  Actions artifact only after the build is green. This APK is for test
  devices; do not update the stable Kellie's Voice production release.
- Restart Home Assistant after installing the isolated ZIP and verify the
  **Kellie's Voice v3 Owner Approval** owner-authenticated panel loads at
  `/kellies-voice-v3qa-approval`. Existing normal Kellie's Voice must still
  function.
- Open two Home Assistant browser tabs. The first stays on HA
  `Settings > Devices & services`, the second stays on the owner panel.

## The two-minute test (do not leave the TV screen)

1. On the debug Android TV QA screen press **START 2-MINUTE V3 TEST**. Keep
   the activity foreground; Back, Home, sleep and leaving it close the
   one-use listener.
2. In the first HA tab choose newly discovered
   **Kellie's Voice v3 SAFE TEST**, start the secure code comparison.
3. Compare **all eight digits** shown on the TV and HA. If they match,
   press the actual physical TV remote's confirmation. Never approve
   if the codes differ.
4. Submit the number-match step in HA. It should display
   `owner_approval`. Do not close this tab. The ephemeral TV listener is
   still running.
5. Switch to the owner-authenticated second tab, click
   **Refresh available TVs**, verify the same eight-digit code, and
   click **Approve this TV**.
6. Switch back to the first tab and submit the owner approval check
   **before the original two-minute window expires**.
7. Expected HA result:
   `owner_receipt_verified_no_login`. Expected TV status:
   **SECURE OWNER APPROVAL VERIFIED** and
   **Encrypted TV acknowledgement sent**.
8. Confirm the old manually signed-in Kellie's Voice still works and no
   new TV HA device, user, credential or access token appeared.

If an error occurs, save the screen and report only the error message;
**never share secrets or credentials**. On any mismatch, denial, timeout,
failed ack, restart or lost TV foreground state, the test must fail
closed. Retry only with an entirely fresh TV two-minute window.

## Threat boundaries and limitations

- The owner identity comes only from an authenticated HA owner WebSocket
  message; the TV cannot claim ownership. The already-verified physical-TV
  proof and P-256 commit/reveal transcript must match the exact discovered
  TV UUID and 16-byte session.
- HA retains the ECDH key only for the original ephemeral window, then
  encrypts the fixed `owner-approved` receipt after consuming the single-use
  claim. The TV only accepts it while in the already-physically-approved
  state and returns AES-GCM `qa-pong`. HA must decrypt that authenticated
  response before showing success. Both peers close ephemeral keys afterward.
- There is **no account login, OAuth, refresh/access token, Android Keystore
  activation, persistent authorization, Assist/camera capability, automatic
  Home Assistant config entry, new service, or change to stable integration**.
  This tests a sealed, one-use identity receipt only.
- The LAN HTTP endpoint itself remains unauthenticated; only the fixed
  encrypted owner proof is accepted. No generic command or credential
  endpoint exists. A wrong or replayed receipt tears down that attempt.
- This is intentionally a **developer draft** for limited owner hardware QA
  after CI. Production release still needs independent security audit #214,
  dedicated least-privilege TV identity, durable cancellation/revocation,
  fallback, detailed cross-TV concurrency and time-of-check/time-of-use
  review, and authentication bound to actual Assist and alert APIs.
- Do not extend expiry to make testing easier. If two minutes proves
  impractical, design a newly authenticated, separately confirmed extension
  rather than reusing key material past its original bound.

## Remove only the QA component

Leave existing stable integration intact. To revert the test integration:
remove its `kellies_voice_v3qa:` YAML entry; delete only the
`/config/custom_components/kellies_voice_v3qa` folder; check HA config
and restart. No HA credentials need cleanup because this test issues none.
