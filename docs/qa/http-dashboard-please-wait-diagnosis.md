# Issue #261: HTTP HA dashboard stuck on "Please wait"

## Diagnosis

Home Assistant's frontend shows "Please wait" when its authentication flow does not complete. In the screensaver WebView this commonly happens for local HTTP dashboards for one or more of these reasons:

1. **Trusted-networks / cookie session not established**  
   HA's HTTP trusted_networks path relies on a session cookie set by the backend. Android WebView does not always accept cookies by default unless `CookieManager.setAcceptCookie(true)` is called. Without the cookie, the frontend stays on the waiting screen.

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
5. Expected: either the dashboard loads, or a clear error is logged. "Please wait" should be reduced if the cookie was the blocker.
6. Repeat with Secure HA dashboard sign-in **ON** and an HTTPS same-origin URL to confirm the existing external-auth path is unchanged.
