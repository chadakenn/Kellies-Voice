# V3 signed-in owner approval (token-free QA)

Draft only, stacked on PRs #250, #249 and #247. The v3 device proof passed on actual onn 4K Pro and HA Core 2026.10.2 on October 30, 2026. This owner-approval change has NOT yet been hardware-tested.

The existing commit-before-reveal ECDH and physical remote proof run first. Only then is a separate 120-second owner claim made available. Owner identity must come from Home Assistant's authenticated WebSocket connection.user, never the TV, mDNS or a config-flow context.

## Developer QA steps

1. Install this draft's isolated kellies_voice_v3qa ZIP alongside the existing production kellies_voice folder, not over it. Restart HA. Use the separately installed Android debug TV APK.
2. Start the v3 test on the TV, select the SAFE TEST discovery in HA, verify eight digits and physically press OK on the TV.
3. HA should now display an owner_approval form, not the old verified_no_login success yet.
4. Keep that form open. In a second signed-in HA owner browser tab open /kellies-voice-v3qa-approval on the SAME HA instance.
5. Check the exact code, click Approve this TV, return to the first tab and submit owner_approved.
6. Expect owner_verified_no_login. No token, persistent config entry, OAuth exchange or TV login is created.

Failure cases: inactive/nonowner HA users, wrong code, wrong TV/session, replay, no TV remote OK, expiry or HA restart must fail closed. Do not enter a Home Assistant user ID manually.

Known limitations: Two browser tabs are a developer workaround and not the promised single-modal consumer UX. Runtime HA frontend/panel registration and browser behavior still need actual testing. Independent security audit, least-privilege TV identity (#239), crash-safe revoke (#240), encrypted credential delivery, Android Keystore, Assist and scoped alert permissions remain blockers to real auto-login.

Main, HACS and existing TV credentials are unchanged.