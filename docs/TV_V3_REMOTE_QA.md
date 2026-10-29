# TV secure pairing v3: on-device QA screen (DEBUG APK only)

This draft adds an actual on-device TV remote path for the **token-free**
v3 LAN handshake. **It is a TEST, not an automatic Home Assistant login**.
The existing released APK and existing saved login remain untouched.

## Test navigation

On a separately installed **debug** APK, open:
**Settings → Home Assistant → Secure pairing v3 (DEBUG TEST ONLY, no login)**.

The activity itself checks the Android `FLAG_DEBUGGABLE` bit and
immediately exits otherwise. Its Android manifest entry is not exported
and is not a launcher activity. The v3 option is absent from the normal
release Home Assistant settings menu.

The TV screen shows:

1. **START 2-MINUTE V3 TEST** (focused). Starts a freshly generated
   P-256 key+nonce session, local ephemeral port and mDNS protocol=3.
   Neither a microphone nor a listener starts until this explicit press.
2. A large eight-digit code appears **only after HA's v3 commitment and
   reveal have been independently checked and the TV has revealed once**.
   Compare all eight digits on both real screens.
3. **EIGHT DIGITS MATCH · TV REMOTE OK** becomes focusable only after
   the code is shown. This physical click produces the UUID-bound MAC
   that HA can independently verify. It does not sign in.
4. **CLOSE SETUP / KEEP CURRENT LOGIN** closes the listener, destroys
   ephemeral pairing state and returns to the unmodified existing app.

Pressing Android Back, backgrounding or destroying the screen also
closes the socket and mDNS advertisement. A generation counter prevents
old pairing callbacks from overwriting a newer attempt. After a timeout
the code disappears and START becomes available again.

## Troubleshooting immediate START reset (onn 4K Pro)

In the original debug build, **START 2-MINUTE V3 TEST** could briefly
show "Waiting for Home Assistant" and then reset with no actionable error.
Android NSD registration was given the full service name
`_kellies-pair._tcp.local.`, while Android's `NsdServiceInfo` registration
takes `_kellies-pair._tcp.`. The HA discovery manifest must **continue**
using the fully qualified `_kellies-pair._tcp.local.` name.

In the revised debug build:

- **V3 network discovery READY** means Android accepted the LAN advertisement.
  The eight-digit number will *not* appear until the matching HA test flow
  starts and both sides verify the key commitment. Open HA's new **v3 SAFE
  TEST** discovery after seeing READY.
- **Android NSD registration FAILED (code N)** is a definitive Android
  registration failure, not a wrong HA code. Note the actual code.
- **V3 START FAILED: ...** remains visible, including exception type and
  message, so a failed socket/NSD start cannot flash by unnoticed.
- **V3 test stopped because the QA screen left the foreground** means the
  Activity's onStop cleanup ran. Don't launch another app or overlay during
  the two-minute test.
- **2-minute window expired** is the expected timeout; press START to
  generate a new key and session. No code is cached.

This build is a **draft DEBUG APK only**; the normal signed TV release,
saved HA connection, microphone and alerts are unchanged. Do not merge main
or sign in with credentials through the temporary listener.

## Hardware gate still pending

The real onn TV cannot complete v3 verification yet using the user's
currently installed **v2-only HA pairing preview**. The matching
`HaV3WireAttempt` implementation already exists in draft #244,
but still needs an owner-initiated HA Core config-flow screen that:
- recognizes v3 mDNS protocol=3 and the same session;
- sends the strict four v3 messages to the discovered local port;
- displays the eight-digit code and checks actual physical TV-OK MAC;
- requires the signed-in HA owner's explicit approval via #241;
- never silently drops back to v1/v2 to issue credentials.

Only after that does this APK become an end-to-end v3 device test. The
public auto-login also needs the independently reviewed separate
least-privileged TV credential, crash-safe token lifecycle and encrypted
Android Keystore handoff. HACS normal installation must eventually be
one-click for users without Nabu Casa.

**Do not release or merge this QA-only screen as public v0.17.0.**
