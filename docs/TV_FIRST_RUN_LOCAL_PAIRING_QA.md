# TV first-run local pairing: no typing (QA draft for #193)

## What changed

This draft is based directly on `main`, not on the unreviewed OAuth or Android Keystore handoff branches. It imports only the already-tested P-256 ephemeral pairing-code and short-lived LAN discovery modules from the separate Pairing Lab. No credentials are accepted or installed.

- A TV with **no saved connection** opens a large **Waiting for Home Assistant** screen on ordinary app launch. Existing connected TVs, launcher overlay shortcuts, assistant invocations, and undecryptable legacy logins are deliberately not intercepted.
- **START 2-MINUTE PAIRING** starts the visible, temporary `_kellies-pair._tcp.local.` advertisement with an actual HTTP listener on an ephemeral local port. It exchanges public keys only. No Nabu Casa, HTTPS, or account is required for THIS verification step.
- In Home Assistant, an owner can use the **separate approved QA integration from PR #230** to discover the TV, compare the same eight-digit code, press OK once on the physical remote, and get independent UUID-bound proof verification.
- **USE EXISTING MANUAL LOGIN** closes the listener and directly opens the pre-existing Home Assistant address & token form. **CLOSE SETUP** and Android Back safely exit without showing a keyboard. No existing login, mic, TV event or wake word preferences are changed.
- The existing TV Settings → Home Assistant menu also has **Pair with Home Assistant (two-screen code preview)** for later retests without wiping an existing connection.

## Explicitly not finished

**The public setup path is NOT complete yet:** the preview does not open OAuth, send an HA URL to the TV, exchange or store an HA token, authenticate Assist/alerts, or switch the TV to Connected. Even after a correct code, the existing login still must be used until the independently reviewed secure credential handoff and recovery flow is ready. Do not market or merge this as working auto-login.

## On-device TV acceptance tests

1. Install the unsigned debug QA APK on a test TV with no saved credentials. Open the launcher icon: it should show Waiting for Home Assistant with a clearly focused START option and no TV keyboard.
2. Press START. The discovery listener is active for at most two minutes. Home Assistant's PR #230 test component discovers and displays an 8-digit code, and the TV shows the same code. TV remote OK returns authenticated approval proof, but no login is saved.
3. Cancel, press Back, put the app in background or expire the 120-second timer. The listener and mDNS advertisement must close. There must be no listening or microphone on boot/standby.
4. Test existing saved login: ordinary launcher, assistant button, and configured overlay shortcut must all retain their original behavior, not forcibly open onboarding.
5. Tap USE EXISTING MANUAL LOGIN. The original address-and-token screen must appear immediately. CLOSE SETUP / remote Back returns to the app with no credential changes. No Nabu Casa needed.
6. Verify no unsolicited LAN listener is active before pressing START or after leaving the activity. Verify multi-TV identity and same-code matching. For now no real OAuth or credential data is permitted.

## Remaining design work

Complete supported and owner-approved Home Assistant authentication for **both local-only and Cloud** users; prove actual HTTPS/local security properties; build scoped TV permissions; bind verified encrypted handoff to TV UUID/session/code; test real Assist + scoped alerts before atomic Keystore activation; restore/revoke after crash and restart; finalize translations; independent #214 security audit; then integrate one stable no-typing UI with automatic URL discovery, no manual ZIP/ADB for ordinary users.

Keep PR DRAFT, preserve `main`, `dev`, the current release candidate and the user's working TV installation.
