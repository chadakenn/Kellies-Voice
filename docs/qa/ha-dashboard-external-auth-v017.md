# v0.17.0 QA: opt-in Home Assistant dashboard screensaver (#261)

This is a beta **screensaver web-mode option**, not a replacement for the
existing four native live cameras, Frigate URL viewer or voice assistant.
It requires signed QA v0.17 and an owner-approved **dedicated TV login**.

## Configure

1. Pair the QA app to the intended Home Assistant server using the
   owner-approved renewable TV login. The pairing grant requires HTTPS.
   Do not copy an administrator's long-lived access token into the WebView.
2. In **Settings → Screensaver**, turn on Screensaver dashboard / cameras.
3. Set **Dashboard URL (Frigate or Home Assistant)** to a dashboard URL
   on the **same exact HTTPS scheme/hostname/port** as the paired HA origin,
   for example `https://ha.example.test:8123/tv-wall/wall?kiosk`.
4. Turn on **Sign in to Home Assistant dashboard**. It is OFF by default.
5. Open **Preview screensaver** and test the actual Android TV dream.
   Turn off the sign-in switch to use the original unauthenticated Frigate
   page viewer, or clear the URL to use the existing native camera grid.

The app adds `external_auth=1` to the dashboard URL (preserving any kiosk
query and fragment), but **never adds a token**. It registers only
`externalAppV2` for the exact paired HA origin using
`WebViewCompat.addWebMessageListener` and checks both the sending frame
and current top-level page. No wildcard, cross-host or
`addJavascriptInterface` fallback is used.

HA's frontend requests an **access token only**, using
`getExternalAuth`; native Android Keystore retains the refresh token.
The token and a conservative TTL are supplied to Home Assistant's fixed
`externalAuthSetToken` callback only after a secure background renewal.
Home Assistant's in-page Logout button calls `revokeExternalAuth` and
revokes the TV refresh token at `/auth/revoke`, **disconnecting the
TV's Assist, alerts and cameras as well** until re-paired. This is expected.

## Limitations and hardware/security QA

- Secure pairing is HTTPS-only. **HTTP trusted_networks bypass**, the original
  tester's HTTP dashboard in #261, **is not supported** by this first secure
  opt-in mode. Do not close #261 until supported testers verify their scenario
  or the remaining HTTP-only case is explicitly tracked.
- A WebView lacking `WEB_MESSAGE_LISTENER` fails closed with a notice.
  The unsafe V1 bridge is intentionally not offered; the Android companion
  apps have had a cross-origin token exposure vulnerability.
- No remote debugging of the authenticated WebView, no console token logging,
  no extra OAuth or new HA config, no automatic sign-in to Frigate or arbitrary
  domains. A dashboard must be on the paired HA **exact origin**.
- Dashboard cards and custom components are arbitrary first-party HTML/JS
  inside HA's trusted origin. A compromised custom HA component could act
  with the dedicated TV user's token; use a minimally permitted TV user and
  avoid including untrusted JavaScript.
- The board rejects attempted off-origin top-level navigation; owner close
  and Android Back are unchanged. Do not assume user-agent desktop spoofing
  fixes all custom HA frontends.
- [ ] User turns the switch OFF: native four-camera live grid and existing
  Frigate URL mode behave as before, including after reboot.
- [ ] Same-origin HTTPS dashboard loads with `?kiosk&external_auth=1` and
  does not show “Please wait” at login, including after a WebView reload.
- [ ] Next access token expires/refreshes after 30+ minutes without logout.
- [ ] Wrong host, changed port, http://, redirects and mixed-content
  origins never receive the access token; no token in logs, URL or storage.
- [ ] Cross-origin iframe cannot request a token; malformed callbacks do not
  execute arbitrary script. QA must verify `isMainFrame` and source/current
  origin enforcement.
- [ ] A revoked dedicated-TV token causes sign-in failure and **never**
  falls back to saved legacy owner's credentials.
- [ ] In-page logout only reports success after HA server revocation;
  network outage does not silently wipe/re-authorize an old credential.
- [ ] Signed APK passes Android unit tests, JNI APK build, HA tests and
  real onn 4K Pro / Philips Android TV hardware checks.

Reference: [Home Assistant's external-authentication API](https://developers.home-assistant.io/docs/frontend/external-authentication/).
