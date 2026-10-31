# v0.17.0 QA: local HTTP trusted-network login vs TV WebView

Status: **diagnostic only**. No username/password, URL secrets, cookies, auth codes, full paths or redirect targets are recorded.

## Controlled observations (onn 4K Pro, October 31, 2026)
- HA trusted-network user selection appears for the TV's LAN IP and its dedicated non-admin user.
- Auth succeeds in TV Bro **on the same Android TV and network**, opening Mobile Command.
- Clicking Log in inside Kellie's Voice WebView fails, including with a physical mouse.
- HA logs report invalid authentication on an `/auth/login_flow/<id>` endpoint for the TV's IP.
- Disabling Kellie's Voice's opt-in LAN HTTP WebView restriction did **not** fix login; restore it before further tests.
- Separately, the native D-pad navigation was trapped by HTML focus; see pointer QA PR #277.

## QA test on freshly signed diagnostic APK
1. Do not erase Android app data, uninstall, or touch HA auth YAML. Leave HTTPS-only HA external auth OFF for the local HTTP experiment.
2. Confirm the local dashboard URL, then capture **only** the app's safe Logcat tag:
   `adb logcat -c` (optional, clears only log buffer); `adb logcat -s KellieScreensaverWeb:I *:S`
   Alternative after the test: `adb logcat -d -s KellieScreensaverWeb:I *:S`
3. Open screensaver HA page, select the dedicated trusted user, click Log in **once**, and stop.
4. Review timestamped log for `HTTP error status=...` and `kind=auth-login-flow`, and for `blocked navigation` with only scheme/host class/port. Also see HA's system log for invalid authentication at the same instant.
5. If a `4xx` login-flow HTTP status is logged, investigate HA request/flow compatibility. If an off-origin navigation is blocked, inspect the safe scheme and origin class without allowing arbitrary external auth callbacks. If no HTTP error is reported but the UI hangs, inspect WebView frontend storage/PKCE/navigation instrumentation in a separate PR.
6. **No automatic sign-in via HTTP is claimed.** Avoid repeated attempts triggering an IP ban. Preserve Frigate, camera grid, doorbell, remote Back, and pairing.

Diagnostic changes have no auth-policy changes, no new permissions, and are not ready for public release.
