# Kellie's Voice v3 QA (isolated test integration)

**STAGING ONLY. Not an auto-login. No credentials or config entries are created.**

This folder is a separate, minimal Home Assistant custom integration. Its domain is `kellies_voice_v3qa`, distinct from the normal `kellies_voice` integration. It does NOT register services, WebSocket commands, alert handlers, timers, sensors, entities, or a device. It imports the reviewed v3 commitment, ECDH code and LAN wire adapter from draft PR #247; those modules must remain byte-for-byte identical while testing.

## Required HA startup registration for owner-test version

The owner panel adds a static HTTP route, so it **must register during Home
Assistant startup**, not while the TV is actively pairing. This isolated
QA package has no permanent HA config entry. After installing the ZIP,
add this exact single line at the top level of
`/config/configuration.yaml` (only once):

```yaml
kellies_voice_v3qa:
```

Then use **Developer Tools → YAML → Check configuration** and restart
Home Assistant. The installed QA domain is independent of the normal
HACS `kellies_voice` domain. Do not overwrite the normal integration
and do not add OAuth tokens. The owner-only panel should now appear at
`/kellies-voice-v3qa-approval` in an owner-authenticated HA browser.

If HA does not accept the YAML entry or the approval panel does not
appear after restarting, **stop the test**. Check the Home Assistant logs
for "Kellie's Voice v3 QA owner-panel startup failed" and report the
exception; don't attempt a manual credential workaround. If the flow
returns `owner_setup_missing`, the startup registration did not finish.

## Test installation (after HA backup)

1. Download the **v3 QA HA integration ZIP** artifact from the GitHub Actions workflow associated with this branch, or generate the package using `scripts/package_ha_v3qa.py` from a checkout.
2. Inspect the archive. It must contain ONLY the `custom_components/kellies_voice_v3qa` folder, no `custom_components/kellies_voice`.
3. Extract it into your Home Assistant `/config/` directory. You should end up with both folders:
   - `/config/custom_components/kellies_voice/` (your existing working integration, unchanged)
   - `/config/custom_components/kellies_voice_v3qa/` (new verification-only test)
4. Add the YAML startup line above, validate the configuration, then restart Home Assistant. Confirm your existing Kellie's Voice entities, services and live TV are still working.
5. On the **separately installed DEBUG TV APK**, go to Settings → Home Assistant → Secure pairing v3 (DEBUG TEST ONLY) → Start 2-minute v3 test.
6. In HA Settings → Devices & services, select the new **Kellie's Voice v3 SAFE TEST** discovery (not your production Kellie's Voice), then select Start.
7. Compare all eight digits, press OK on the physical TV remote, then proceed to the **owner_approval** step. In a second HA owner browser tab open `/kellies-voice-v3qa-approval` and click **Approve this TV** for the identical code. Return to the original HA dialog and submit. Expect **`owner_verified_no_login`**, never a new TV or working OAuth login.

Stop if you see a request for an HA token, login, or if the original `kellies_voice` folder is going to be replaced. You do not need to disable or uninstall the production integration.

The owner test must not modify anything outside the QA folder except
the explicit YAML startup entry. **To roll back**, remove that single
`kellies_voice_v3qa:` line, delete only the QA folder, validate the
HA configuration, then restart Home Assistant.

## Removal / rollback

Delete ONLY `/config/custom_components/kellies_voice_v3qa` and restart HA. Nothing is supposed to be stored in HA config entries or Android Keystore by this test.

Drafts #247 and this isolated package stay off main and public releases until the relevant security review and real-device QA pass.
## v3 authenticated owner proof (draft, optional QA step)

After all eight digits match and you approve using the TV remote, this QA
version **does not yet finish**. The new `owner_approval` form must receive
a separate confirmation from an authenticated, active **Home Assistant owner**.
Keep the pairing flow OPEN in the first browser tab. In a second tab, open
`/kellies-voice-v3qa-approval` on the SAME Home Assistant instance.
Verify the exact code displayed there, click **Approve this TV**, and return
to the original config flow. Only after the backend confirms both TV and
owner proofs will HA report `owner_verified_no_login`.

A signed-in ordinary admin who is not the HA owner must be rejected.
Do not type a Home Assistant user ID anywhere. Close the QA flow or allow
the 120-second session to expire to discard it. Moving between Home
Assistant panel and config-flow modal in one tab may dismiss the modal,
so two tabs are necessary for this **developer test**. This is NOT the
consumer one-click onboarding UI and does not issue credentials.
