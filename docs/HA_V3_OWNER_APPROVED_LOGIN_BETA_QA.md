# Kellie's Voice beta: owner-approved ongoing login (PR #260)

**This is a draft hardware test, not a public release. Do not deploy it until
the PR's Android, Home Assistant, pairing and combined-kit builds all pass.**

## Owner approval panel shortcut (QA)

When the two-screen physical TV proof succeeds, Home Assistant's final
**Verified TV: confirm the signed-in HA owner** form now includes a
clickable **[OPEN OWNER APPROVAL PANEL](/kellies-voice-v3qa-approval)**
link in its description. Open the link **in another browser tab** (for
example, right-click → Open link in new tab or Ctrl+click), because
navigating away from the active config flow can discard the current
120-second pairing attempt. The link is relative to the HA instance
currently displaying the flow, so it also works with HA Cloud or a custom
local address. No TV ID, code, token or owner information is embedded in
the URL. The approval page is restricted to authenticated admins and its
WebSocket backend independently requires the actual HA owner.

The old redundant editable `qa_instructions` text field has been removed.
The *actual* checks are unchanged: matching eight-digit code, physical
TV proof, same session/TV identifier, explicit action in the signed-in HA
owner panel and cryptographic receipt acknowledgement. The owner may
opt into ongoing login only after reviewing the broad standard HA Users
permissions. Do not select Submit before approval; the form continues
to display `owner_pending` until the backend confirms it.

## Local discovery versus Home Assistant login transport

**Local HTTP discovery and short-lived pairing already work.** They are
separate from the TV's ongoing authenticated Home Assistant connection.

| Stage | How it works today | Local HTTP-only HA |
| --- | --- | --- |
| HA discovers the TV | TV advertises `_kellies-pair._tcp.local.` via mDNS; HA connects to the verified private TV IP and short-lived local HTTP pairing port | **YES**, no Cloud needed for the TV discovery and eight-digit physical-remote/owner-proof test |
| HA discovers its own URL | HA's supported `homeassistant.helpers.network.get_url(hass)` can return the configured or automatically detected local `http://...:8123` URL | **YES**, the URL can be determined; this is *not* currently used as a TV renewable-login endpoint |
| TV discovers HA from mDNS | HA advertises `_home-assistant._tcp.local.` for clients on the same LAN | **NOT YET IMPLEMENTED** in the Beta TV app. The current flow is HA-initiated TV discovery |
| TV receives its renewable grant | An owner-approved, eight-digit-bound P-256/AES-GCM handoff encrypts the credential as HA sends it to the exact TV over LAN HTTP | The encrypted one-time delivery can use local HTTP; **but issuance still requires a valid HTTPS HA URL** for future renewal |
| TV performs Assist/alerts and refreshes login | Uses the saved HA **HTTPS** origin for WebSocket/API bearer access and `POST /auth/token` refresh grants | **NO** in PR #260; no silent HTTP fallback or auto-issued renewable grants over plaintext HTTP |

**HTTP-only Home Assistant behavior today:** Without usable Cloud HTTPS,
externally configured HTTPS, or internally configured HTTPS, the owner opt-in
for *ongoing login* aborts with `https_required`; no renewable TV credential
should be provisioned. The v3 identity proof without ongoing login may still
be tested. Users may browse their local Home Assistant via HTTP separately.

Home Assistant's network API can find a local HTTP origin, but **URL discovery
is not authentication**. Calling `/auth/token` or opening an authenticated
WebSocket over HTTP sends sensitive authorization data without TLS protection.
An ordinary dedicated Home Assistant Users account can control *all* entities,
so leaking that credential may expose locks, doors, alarms, cameras and more.

**Future option to research (not part of this beta):** Local-first users should
not need a Cloud subscription. Consider an explicit opt-in LAN-only mode with
a clearly displayed trust warning and server-confirmed local-only account,
or local HTTPS with appropriately validated certificates. Both options need
real threat modelling, cross-network/redirect protection, tests for stolen
tokens and revocation, and separate security review. Do not silently replace
the HTTPS policy with HTTP, infer LAN trust solely from RFC1918 address, or
send refresh/access tokens over mDNS advertisements. mDNS is for discovering
the address, not proving ownership.

### Quick manual checks

1. With only `http://<local-ha-host>:8123`, start a v3 TV discovery and
   complete physical-remote pairing **without** requesting ongoing login:
   owner proof should succeed; no credentials should be created.
2. In an isolated QA installation that has no working HTTPS URL, request
   ongoing login: expect `https_required`, not a silently stored HTTP token.
3. With HA Cloud HTTPS enabled and local HTTP still automatic, pairing should
   auto-select Cloud HTTPS and Assist should authenticate after the app reopens.
4. Do not disable TLS validation or open local ports to the public internet.

## What the owner will do

1. Install the **Kellie's Voice Beta** APK alongside the existing stable
   app. Its package ID and credentials are separate. Do not uninstall stable.
2. Extract the inner `kellies_voice_v3qa_ha.zip` into Home Assistant's
   `/config/`, preserving the entire
   `custom_components/kellies_voice_v3qa/frontend/` folder.
   Never overwrite the stable `custom_components/kellies_voice/`.
   Keep the existing one-line `kellies_voice_v3qa:` in
   `configuration.yaml`; check HA configuration and restart HA.
3. **Prerequisite:** Use the existing **Home Assistant Cloud** HTTPS URL
   under **Settings → System → Network** (Internet: Use Home Assistant Cloud).
   This beta now automatically resolves it through HA's native network API;
   no manual external URL is required. Local network can stay automatic HTTP.
   The TV must be able to reach the Cloud HTTPS address with trusted TLS.
   For Cloud access the dedicated TV account is remote-capable.
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
including sensitive devices, via supported APIs. Through Cloud HTTPS the
dedicated TV account is also remotely accessible (not local-only). This is
not a harmless voice-only or read-only account. Only select the option for your own trusted
TV, and do not expose its login secret. To remove access, remove its refresh
token or delete its dedicated user in HA's Users settings.

The custom ECDH handshake encrypts credential delivery to the exact TV;
the separate Android Keystore vault persists refresh credentials over reboots.
HA records the exact token ID in a durable lease journal and revokes
incomplete enrollments on startup. The app renews short-lived access tokens
instead of storing a ten-year owner bearer token.

## Failure / rollback

- `https_required`: HA couldn't obtain a valid HTTPS address from Cloud or
  its configured network URLs. Check Cloud remote connection status. Do not
  paste a Cloud address into `configuration.yaml` as a workaround.
  Ordinary local `http://192.168.x.x:8123` is still permitted for browser
  access, but not for renewal tokens.
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
