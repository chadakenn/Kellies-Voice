# Issue #261: HTTP HA dashboard stuck on "Please wait"

## Diagnosis

Home Assistant's frontend may show "Please wait" when its authentication flow does not complete. The specific cause for issue #261 is **not yet reproduced or established**. These are hypotheses to test on the reporter's HTTP-only setup:

1. **Authentication flow / cookies not established**  
   Home Assistant trusted-networks is an authentication provider that can let an IP-allowlisted client select a user without a password. It is **not equivalent to being logged in without an HA token**. Android WebView **accepts first-party cookies by default**, per Android documentation; explicitly calling `setAcceptCookie(true)` makes policy obvious, but is not a proven fix. Inspect the on-device authentication redirect flow, including provider selections and client IP/proxy routing.

2. **external_auth=1 without a responding bridge**  
   If an HTTP URL is loaded with `external_auth=1` (or HA's frontend decides to wait for an external auth callback), and no `externalAppV2` listener answers, the frontend waits indefinitely. The existing HTTPS external-auth path deliberately does **not** append `external_auth=1` to HTTP URLs.

3. **User-Agent / storage differences**  
   Desktop user-agent spoofing and DOM storage are already enabled. Some HA versions still behave differently in WebView than in Chrome. This is not fully solvable without HA frontend changes.

4. **Redirects to a different host/port**  
   `shouldOverrideUrlLoading` blocks navigation that fails `sameSite` (scheme + host + port). A redirect to HTTPS, a different port, or another hostname is correctly rejected and can leave the page on the waiting screen.

## What this change does

- Explicitly enables cookie acceptance for the screensaver WebView (first-party only).
- Adds privacy-safe diagnostics (`ScreensaverWebDiagnostics`) that log only scheme, host class (ip/hostname), port, and error codes. Full URLs, tokens, cookies, and Authorization headers are never logged.
- Adds tests confirming HTTP dashboard URLs are accepted, same-site redirects stay on the same host/port/scheme, and `authenticatedUrl` never produces an HTTP `external_auth=1` URL.

## What this change does **not** do

- Does **not** automatically authenticate an HTTP dashboard with the TV's renewable token.
- Does **not** downgrade the HTTPS external-auth path.
- Does **not** seed tokens into localStorage or use an unrestricted JavaScript interface.
- Does **not** disable certificate verification.

Automatic LAN-HTTP token login remains tracked in issue #262 and requires a separate security review.

## Suggested hardware test

1. On the TV, set Screensaver dashboard URL to `http://192.168.x.x:8123/lovelace/...?kiosk` and leave Secure HA dashboard sign-in **OFF**.
2. Confirm HA has the TV's IP in `trusted_networks` (or use the login form).
3. Start the screensaver / preview.
4. Check logcat for `KellieScreensaverWeb` lines (scheme, host class, errors only).
5. Expected: dashboard loads or diagnostics show page lifecycle and any main-frame WebView error codes. A login spinner **may still appear with no WebView network error**. Never interpret lack of errors as successful authentication.
6. Repeat with Secure HA dashboard sign-in **ON** and an HTTPS same-origin URL to confirm the existing external-auth path is unchanged.

## References and security note

- [Android WebView CookieManager](https://developer.android.com/reference/android/webkit/CookieManager): `setAcceptCookie` defaults to `true`.
- [Home Assistant trusted-networks authentication](https://www.home-assistant.io/docs/authentication/providers/): trusted client networks are an authentication *provider*, not unrestricted API access; including the normal `homeassistant` provider preserves password login.
- [Home Assistant external authentication](https://contributors.home-assistant.io/frontend/reference/external-auth/): the native V2 bridge is only activated with `external_auth=1`; HTTPS-only owner-approved pairing remains unchanged.
- Diagnostics deliberately omit the free-form WebView error description because error text from providers may contain URLs or secrets despite attempted regex redaction. Logs contain only scheme, host class, port and numeric error code. Do not post complete logcat or token-bearing traces publicly.
