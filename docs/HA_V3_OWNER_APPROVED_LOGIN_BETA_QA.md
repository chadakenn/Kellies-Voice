# Kellie's Voice beta: owner-approved ongoing login (PR #260)

**This is a draft hardware test, not a public release. Do not deploy it until
the PR's Android, Home Assistant, pairing and combined-kit builds all pass.**

## What the owner will do

1. Install the **Kellie's Voice Beta** APK alongside the existing stable
   app. Its package ID and credentials are separate. Do not uninstall stable.
2. Extract the inner `kellies_voice_v3qa_ha.zip` into Home Assistant's
   `/config/`, preserving the entire
   `custom_components/kellies_voice_v3qa/frontend/` folder.
   Never overwrite the stable `custom_components/kellies_voice/`.
   Keep the existing one-line `kellies_voice_v3qa:` in
   `configuration.yaml`; check HA configuration and restart HA.
3. **Prerequisite:** HA must have an HTTPS internal or external URL
   configured at **Settings → System → Network**. This beta will NOT send
   a reusable refresh token to a plaintext HTTP endpoint.
   The TV needs to reach that HTTPS address, with a trusted certificate.
4. On the TV, open Kellie's Voice Beta → Settings → Home Assistant →
   **Secure pairing v3** and press **START 2-MINUTE V3 TEST**.
5. In HA Settings → Devices & services, select the discovered v3 SAFE TEST.
   Compare all eight digits, press the physical TV remote's OK button,
   confirm the code in the original HA flow. Keep the TV screen open.
6. In the separately authenticated HA owner panel
   `/kellies-voice-v3qa-approval`, compare the code again.
   To request an **ongoing login**, check the dedicated TV account option,
   then press **Approve this TV**.
7. Return to the original HA dialog and Submit, before the original
   two-minute TV window expires. Expected result:
   `owner_login_connected`. TV should say **TV LOGIN ACTIVATED**.
8. Reopen the *beta* app. It should renew its HA bearer token through HTTPS
   and connect Assist without you typing a URL or copying a token.
   Reboot the TV and HA, then check it reconnects automatically.
9. To receive camera popups and announcements in this *separate beta*,
   enable the opt-in alerts setting and display-over-apps permission.
   Pair the beta's distinct TV identity with the regular stable
   `kellies_voice` HACS integration in HA; the isolated v3 QA component
   does not create that alerts device.

## What this actually authorizes

The owner opts into a **dedicated Home Assistant non-admin Users account**
for this TV, with a normal renewable refresh token encrypted by Android
Keystore. The HA owner token is never copied or exposed.

**Important permission disclosure:** Home Assistant's normal Users group is
NOT entity-limited. This TV account can read and control **all HA entities**,
including sensitive devices, via supported APIs. This is not a harmless
voice-only or read-only account. Only select the option for your own trusted
TV, and do not expose its login secret. To remove access, remove its refresh
token or delete its dedicated user in HA's Users settings.

The custom ECDH handshake encrypts credential delivery to the exact TV;
the separate Android Keystore vault persists refresh credentials over reboots.
HA records the exact token ID in a durable lease journal and revokes
incomplete enrollments on startup. The app renews short-lived access tokens
instead of storing a ten-year owner bearer token.

## Failure / rollback

- `https_required`: configure a reachable HTTPS HA address and retry.
  Ordinary local `http://192.168.x.x:8123` is not accepted for renewable
  enrollment.
- `owner_login_failed`: physical remote, owner approval, token staging or
  activation did not finish. Restart the whole two-minute TV window. On
  the next HA startup unfinished token leases are revoked.
- `owner_receipt_verified_no_login`: successful older identity test.
  The owner did **not** opt into recurring login.
- If the beta fails to connect after success, inspect HA's URL and HTTPS
  certificate, local-only user policy, user/token status and TV network.
  The stable app should remain unaffected.

Never share tokens, renewal grants, HA passwords or private keys in GitHub
issues, screenshots or chat. Do not install on public HACS or merge to main
until hardware pairing, Assist, alerts, restart, offline revoke and
two-TV isolation are verified. PR #260 stays DRAFT.
