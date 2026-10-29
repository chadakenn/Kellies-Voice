# #193 Stage B1: user-started LAN discovery preview

Status: **development only, read-only prototype; not automatic pairing.**
Depends on [PR #197](https://github.com/chadakenn/Kellies-Voice/pull/197).
This slice intentionally does **not** change `custom_components/kellies_voice`, `CredentialStore`, `HaAssistClient`, microphone permissions or production startup paths.

## What the current code does

1. On an Android TV test APK, open **SETTINGS → Home Assistant → Preview no-typing pairing discovery (development only)**.
2. Press **START DISCOVERY PREVIEW**. The TV creates an unpredictable 128-bit *public* session identifier and a TCP listener on an ephemeral real port.
3. The TV advertises `_kellies-pair._tcp.` over Android NSD with the **actual port**, `mode=discovery-only`, `protocol=1`, and the session identifier. It intentionally does not use or modify the legacy `_kellies-voice._tcp.` layout-control announcement.
4. For at most 120 seconds or 32 connections, `GET /v1/pair/capabilities` returns only `{"protocol":1,"mode":"discovery-only","authorized":false,"session":"..."}`. Every other request is rejected and closes its socket. No POST support or credential upload exists.
5. **Cancel, Back, navigating away, Android activity stop, timer expiry and registration failure** close the listener and withdraw the advertisement. No boot/startup receiver opens it.
6. The existing saved HA login, paired ID, application data and scope of allowed microphone remain unchanged.

### LAN smoke test for maintainer (not completed)
- On a TV with the updated debug APK, open preview and deliberately press Start. On a computer on the same LAN, use an mDNS browser (`avahi-browse -rt _kellies-pair._tcp`, `dns-sd -B _kellies-pair._tcp`, or similar) to see the ephemeral port. Never publicly expose the port.
- Request `http://TV_LOCAL_IP:ADVERTISED_PORT/v1/pair/capabilities`. It must say `authorized:false` and contain no HA URL, pairing UUID, token, credentials, OAuth code or device fingerprint.
- POST or another path must yield no credential transfer, a non-success response; no request may cause a login.
- Leave the screen or press Cancel and verify that it disappears from mDNS and the endpoint closes. Repeat at expiry, reboot and standby; it must not restart itself.
- Keep the existing Home Assistant layout/manual login functional. Existing `_kellies-voice._tcp.` discovery is separate.

## Important limitation
**Home Assistant will NOT automatically pair with this preview.** The installed HACS config flow doesn't yet implement the new `_kellies-pair._tcp.` exchange. We intentionally don't advertise a fictitious secure/ready capability or create an HA entry from this public advertisement. There is no actual user consent/OAuth token exchange in this PR. Do **not** describe the preview as a connected device; it doesn't transfer even the HA base URL.

## Remaining #193 stages

- B2: Add cryptographic ephemeral key exchange with transcript-bound numeric comparison and physical TV OK; rate limit/protect inbound requests; security review before any secret moves.
- C: Add HA-side flow that reliably binds an *authorized* owner and the intended TV, using Home Assistant's officially documented OAuth authorization code + PKCE; ensure the OAuth callback/client ID are supported for a browser on phone/PC, and that refresh tokens remain separately revocable. A config flow on its own cannot mint a user's scoped token. A refresh token represents the authorized HA user, not automatically a permission-restricted per-device identity.
- D: With a verified encrypted channel and consent, provision authenticated HA origin and revocable credential to the TV's device keystore; only commit the config entry after the intended TV establishes actual authenticated Assist and optionally TV alert WebSocket. Roll back on failure, preserving current login.
- E: Test on onn TV and multiple installations, same/other VLAN, forged mDNS, wrong pairing code, replay, expiration, re-pair and revocation, Android restart, and no microphone startup.

References: [Home Assistant Authentication API](https://contributors.home-assistant.io/core/internals/auth-api/) and [#193](https://github.com/chadakenn/Kellies-Voice/issues/193).
