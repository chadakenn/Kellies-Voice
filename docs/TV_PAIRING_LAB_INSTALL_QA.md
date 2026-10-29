# Installable Kellie's Voice Pairing Lab QA build (#193)

**Debug APK only.** This is **not** v0.17.0, a stable release,
or a working zero-typing OAuth login. It intentionally cannot take over
the Home Assistant credentials used by your working TV app.

## Where to get the APK

In the GitHub Actions **Unit tests** run for this draft branch, open
the successfully completed run and select the
**Kellies-Voice-Pairing-Lab-debug-APK** artifact.
GitHub supplies a ZIP. Unzip it and install **app-debug.apk**.
The ZIP is retained for seven days. Do not install an APK from a failed
run, and do not use a third-party rehosting site.

## Install on onn 4K Pro

The debug package ID is `com.example.tvvoiceprobe.debug`.
It is a separate package from stable `com.example.tvvoiceprobe`.
It will show additional launcher icons, including
**Kellie's Voice Pairing Lab**. It should not overwrite the stable
app or its data. Do not remove the working stable app.

Either transfer the APK to the TV and install it through your normal file
manager, or use Windows platform-tools while the TV has Android wireless
debugging enabled:

```powershell
.\adb.exe devices
.\adb.exe install -r "C:\path\to\app-debug.apk"
.\adb.exe shell am start -n com.example.tvvoiceprobe.debug/com.example.tvvoiceprobe.PairingLabActivity
```

Keep the onscreen "Install unknown apps" permission limited to your
preferred file manager. Grant no microphone or overlay permissions for
the Pairing Lab tests.

## What to try on TV

1. Launch **Kellie's Voice Pairing Lab** (not the regular app).
2. Select **RUN LOCAL SECURE PAIRING SELF-TEST** using the TV remote.
   It runs fresh P-256 peers, eight-digit code agreement, v2 UUID-bound
   physical-OK proof, HA→TV AES-GCM synthetic offer, TV→HA synthetic Assist
   and scoped-alert receipt, HA→TV commit authorization, TV→HA synthetic
   persistence receipt, replay rejection, and different-credential hash
   rejection. Expect a screen showing seven **PASS** results.
3. Select **OPEN LAN TWO-SCREEN CODE PREVIEW** to test the existing temporary
   120-second mDNS and short-lived public-key pairing stage. In Home
   Assistant, a matching *draft* discovery flow must be installed for
   this to appear. Never approve a code unless all eight digits actually
   match both displays.
4. Use **CANCEL / BACK** to stop the temporary discovery listener. It
   must never continue on a restart or silently accept a token.
5. Reopen your existing stable Kellie's Voice separately and confirm its
   saved login, cameras, voice, mic settings, alerts and other behavior
   remain unchanged.

## Important QA boundaries

- The button's Assist/alert verification is a **simulated local gate**.
  It is NOT a live Home Assistant access-control or OAuth permission test.
- A real token is never used, transferred or stored, and the fake "disk
  sink" never writes a credential.
- The code is not yet a runnable zero-typing OAuth pairing experience.
  Actual owner consent, exact credential handoff, authenticated read-only
  Assist+scoped-alert proof, durable revocation and rollback, and device
  integration remain blocked pending security review #214.
- The debug APK includes the normal app classes; install it only on
  a test device or as an independent package alongside stable. Do not
  configure its normal assistant features unless you're deliberately
  testing that isolated debug install.

Report: whether the Pairing Lab launcher shows up; whether all seven
local checks pass; whether LAN discovery shows the eight-digit comparison;
and whether the stable app remains unaffected. Screenshots of the
self-test report can be attached to this PR, but do **not** post any
Home Assistant URLs, tokens, private keys, owner identifiers, or private
camera views.

## Status

This build is **for on-TV manual QA**. It is not production-ready.
Do not merge its draft or publish a public beta until end-to-end
authentication, credential recovery and independent security review pass.
