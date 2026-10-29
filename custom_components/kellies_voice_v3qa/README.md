# Kellie's Voice v3 QA (isolated test integration)

**STAGING ONLY. Not an auto-login. No credentials or config entries are created.**

This folder is a separate, minimal Home Assistant custom integration. Its domain is \`kellies_voice_v3qa\`, distinct from the normal \`kellies_voice\` integration. It does NOT register services, WebSocket commands, alert handlers, timers, sensors, entities, or a device. It imports the reviewed v3 commitment, ECDH code and LAN wire adapter from draft PR #247; those modules must remain byte-for-byte identical while testing.

## Test installation (after HA backup)

1. Download the **v3 QA HA integration ZIP** artifact from the GitHub Actions workflow associated with this branch, or generate the package using \`scripts/package_ha_v3qa.py\` from a checkout.
2. Inspect the archive. It must contain ONLY the \`custom_components/kellies_voice_v3qa\` folder, no \`custom_components/kellies_voice\`.
3. Extract it into your Home Assistant \`/config/\` directory. You should end up with both folders:
   - \`/config/custom_components/kellies_voice/\` (your existing working integration, unchanged)
   - \`/config/custom_components/kellies_voice_v3qa/\` (new verification-only test)
4. Restart Home Assistant. Confirm your existing Kellie's Voice entities, services and live TV are still working.
5. On the **separately installed DEBUG TV APK**, go to Settings → Home Assistant → Secure pairing v3 (DEBUG TEST ONLY) → Start 2-minute v3 test.
6. In HA Settings → Devices & services, select the new **Kellie's Voice v3 SAFE TEST** discovery (not your production Kellie's Voice), then select Start.
7. Compare all eight digits, press OK on the actual TV remote, and confirm in HA. The successful result is a message, **not a new TV or working OAuth login**.

Stop if you see a request for an HA token, login, or if the original \`kellies_voice\` folder is going to be replaced. You do not need to disable or uninstall the production integration.

## Removal / rollback

Delete ONLY \`/config/custom_components/kellies_voice_v3qa\` and restart HA. Nothing is supposed to be stored in HA config entries or Android Keystore by this test.

Drafts #247 and this isolated package stay off main and public releases until the relevant security review and real-device QA pass.