## Philips TV line-in and echo-reference are not microphones (issue #71)

Some Philips Android TVs report input devices such as **Echo Reference In**
and **line_in** even when there is no app-accessible speech microphone.
The initial v0.16.5 preflight blocked REMOTE_SUBMIX but mistakenly
trusted other input types. The #71 follow-up fixes that by accepting only
recognized microphone-capable Android device types (built-in, USB,
wired-headset and Bluetooth headset). Unsupported or unknown devices,
including line-in and internal echo-reference, no longer count as voice mics.

If a vendor firmware exposes a genuine microphone only through a nonstandard
type, use **TV Settings → Mic input detection → Try unverified microphone**
as a *temporary diagnostic override*, then verify spoken STT works.
This cannot unlock a Google Assistant-only remote microphone and doesn't
capture anything until you explicitly start listening. Quiet speech or
a silent room is not used to decide whether a microphone is present.

**Original Philips tester acceptance:** On 65PUS8507/12 without USB mic,
TALK should report **No accessible microphone detected** before Assist STT
receives silent audio. Repeat on 55OLED856/12. With a real USB microphone,
the preflight should proceed and route to a microphone. onn 4K Pro must
retain its previously working built-in mic, normal TALK/STOP and overlay.

# Troubleshooting: Kellie's Voice on Google TV

## TV camera or announcement popups missing? Find Resume TV alerts

The **voice/Assist connection and TV alert receiver are separate**. Even if
TALK works and the main page says Connected, alerts and timers may not reach
the TV after standby, an app update, or a Home Assistant reload.

**Exact remote-control path (Kellie's Voice v0.16.6 and newer):**

1. Open **Kellie's Voice** on the TV, not Android/Google TV system Settings.
2. On the main screen choose **SETTINGS** with the remote.
3. Scroll down to **TV alert status** and press **OK**. This is a separate
   SETTINGS item, below **TV announcements & cameras**, not inside that menu.
4. In the **TV alert status** pop-up, choose **Resume TV alerts** at the
   bottom and press **OK**. It tries a fresh TV-alert-only connection.
5. In Home Assistant, open the paired Kellie's Voice TV device and check
   **TV alert receiver connected**. When ON, send a **new** announcement,
   camera popup, or named timer to confirm delivery.

**No Resume button?** It appears only when **TV announcements & cameras**
is ON, a Home Assistant connection is configured, and **Display over other
apps** is allowed in Android's app permissions. Go back to SETTINGS and
enable/check those first. **Connect / reconnect** in SETTINGS controls the
main Assist connection; it isn't the alert-receiver Resume command.

If the receiver remains stuck on Reconnecting, note the app/HACS versions
and file a [redacted report in #78](https://github.com/chadakenn/Kellies-Voice/issues/78).
Manual Resume was confirmed to bring an onn 4K Pro receiver diagnostic back
ON with v0.16.8. This does **not** establish that automatic recovery after
standby or an HA reload is fixed on every device. TV events missed during
a disconnection are not replayed; no microphone recording starts here.


## "Conflicts with another app" / "App not installed"

Android requires the **same package ID and signing certificate** for an update.

- Original artifacts were `onn-tv-ha-assist-alpha` / `app-debug.apk` and changed signing keys across GitHub runners.
- New permanent artifacts are `kellies-voice-signed` / `app-release.apk`, backed by the private repo keystore. Older successful runs were named `julia-voice-signed`; use the latest `kellies-voice-signed` artifact.
- To change from **old debug** to **new release**, download and verify the successful signed build *first*, then **uninstall old debug app once**, and install release. **The local HA URL and encrypted token are deleted by uninstall**, so you'll need to re-enter them.
- To update from **signed release** to a newer **signed release**, do **not** uninstall; Android should retain settings. If installation conflicts, investigate rather than deleting the app.

See [Signing](SIGNING.md) and [Install](INSTALL.md).

## GitHub build is red

- **Restore private release-signing key** failed: confirm both Actions secrets `TV_SIGNING_KEYSTORE_B64` and `TV_SIGNING_PASSWORD` exist with complete values. Don't show their contents. The signing key alias must be `tvvoice`.
- **Build signed release APK** failed: check compile errors in GitHub Actions logs (never share secrets).
- **Verify APK signature** failed: do not install that artifact.
- The build artifact exists **only after success**, under GitHub Actions run → **Artifacts**; it's a ZIP containing `app-release.apk`.
- Docs-only pushes don't start a full Android build. Use **Run workflow** in Actions when needed.

## Can't type the HA URL or huge token using a TV remote

Try the Google TV / Google Home phone remote keyboard on the same network. The paste feature varies; our promised QR pairing flow remains **planned**, not shipped. The current settings are under **SETTINGS → Configure Home Assistant**.

## Doesn't connect or authenticate

- Enter the correct full HA URL, scheme and port. The TV must resolve/reach it on your LAN, or use an available trusted HTTPS HA address.
- Home Assistant must answer at `/api/websocket`, and a valid **long-lived access token** must be entered.
- Self-signed HTTPS certificates may be rejected; **don't disable TLS verification**. Use a trusted certificate instead.
- If using local HTTP, it is **not encrypted**. Restrict to a trusted private LAN or prefer HTTPS.
- If the screen still says disconnected, retry **SETTINGS → Connect / reconnect**.

## First TALK after reboot is still connecting (#44)

The Home Assistant voice WebSocket and TV-alert receiver are independent. A
voice request right after boot or a Home Assistant restart may arrive before
the voice WebSocket has authenticated or the Assist pipeline has loaded.

- **Connecting to Home Assistant** means the authenticated voice session is
  not ready. Full-screen users must press TALK again once the screen says Ready.
- A newly pressed TALK in the user-launched floating overlay can be held for
  **up to 30 seconds**. If the connection is not ready by then, the request
  expires. The microphone must not start later without another user action.
- **Assist pipeline is starting** is different from a missing pipeline, a
  token rejection, an STT failure, an agent failure or a TTS failure. Consult
  the matching Home Assistant configuration and logs.
- If Home Assistant rejects the saved token, check SETTINGS rather than
  repeatedly opening the microphone. Do not share credentials or signed URLs.

These improved startup messages are currently development-only (#44).
Other Android TV hardware and post-reboot behavior still require testing.
The separate TV alert receiver status does not guarantee voice readiness.

## I can see "YOU SAID" but not the answer

The original seven-button alpha pushed the response beneath the visible portion of the TV. Upgrade to the **signed compact UI** with three main buttons, where the answer appears **above** the transcript. Check whether the HA conversation agent returned an answer; an STT transcript does not automatically guarantee a successful intent or TTS response.

## HA transcribes me, but TV speakers are silent

Speech-to-text and TTS are separate stages. The current client **successfully played a Home Assistant TTS answer through the onn 4K Pro TV speakers** in its first real-device test. If it stops working, confirm that:

1. The selected HA Assist pipeline has **TTS configured**.
2. The on-screen answer is present.
3. TV volume isn't muted and no other app has exclusive audio focus.
4. The app shows **Speaking...**, **Finished...**, or a TTS error in its status line.

The client reads the HA `tts_output.url` event field; earlier builds missed this URL. The working on-device example answered **“5:07 AM”** aloud. If a later test shows a transcript but no sound, capture the **exact status/error text**; don't post your HA token.

## TALK says no accessible microphone

The draft #71 microphone check (not in public v0.16.4) queries Android's
**currently available INPUT devices**, not merely audio-policy capabilities.
If the TV exposes only `REMOTE_SUBMIX` (an audio rerouting path), TALK will
stop **before** starting the Home Assistant Assist pipeline and display an
actionable message to connect a compatible USB microphone or use an accessible
TV microphone. Actual `AudioRecord` routing is checked after capture starts.

In **Settings → Hardware microphone diagnostics**, use **List audio inputs**
and **test VOICE_RECOGNITION** to confirm available inputs and the recorder's
actual route. A Google/Alexa voice remote's built-in mic may be reserved for
its own assistant and therefore unavailable to third-party Android apps.

If a TV's audio device report is demonstrably wrong, **Settings → Mic input
detection → Try unverified microphone** temporarily bypasses the checks for
diagnostics. Turn it back off after testing. It will *not* gain access to a
protected Google/Alexa remote mic. Silence in a quiet room is not considered
proof of missing hardware. The public v0.16.4 still reports
`stt-no-text-recognised` on some microphone-less televisions.

## v0.16.5 TV alerts stuck on Reconnecting after TV sleep or Home Assistant reload (issue #78)

On Android/HACS v0.16.5, a connected main Assist screen does **not**
prove that the separate TV alert receiver is subscribed. If the new HA
`TV alert receiver connected` entity stays OFF while TV alert status says
Reconnecting, camera/announcement automations may publish events that the TV
never sees. v0.16.5's `Resume TV alerts` can fail to replace a stale WebSocket.

The v0.16.6 recovery fix in [PR #80](https://github.com/chadakenn/Kellies-Voice/pull/80) makes **Resume TV alerts** explicitly abort
and rebuild that receiver connection. A bounded Home Assistant WebSocket
`ping`/`pong` heartbeat detects silently stalled sessions, and a setup timeout
catches missing authentication or event-subscription acknowledgments.
Reconnecting never starts the microphone or replays missed alerts. Old
WebSocket callbacks must not affect the new connection, and a rejected token
or denied read permission must stay an actionable error rather than looping.

**v0.16.9 follow-up, October 24 (onn 4K Pro):** With matching HACS 0.16.9
and a TV alert receiver that was already ON, reloading only the Kellie's
Voice HA integration briefly made the diagnostic unavailable and then ON.
A full HA restart instead left the receiver OFF / `never` for several
minutes. The Android TV showed **Home Assistant denied TV alert permission**
while the separate Assist connection remained healthy. Pressing
**SETTINGS → TV alert status → Resume TV alerts** restored the receiver to
ON without changing roles or access tokens, and a new announcement reached
the TV. This is a new physical reproduction of a **transient authorization
denial that remains terminal in v0.16.9**, not proof that Home Assistant
actually revoked the TV's permissions.

**Unreleased recovery patch (review branch):** The Android receiver now
rechecks an `unauthorized` scoped-subscription result at most five times,
using fresh authenticated sockets and 2–30-second bounded backoff (about
60 seconds total). Every attempt still requires Home Assistant to approve
read access to the paired TV. A persistent denial, `not_allowed`, bad token,
wrong pairing ID, or unsupported integration must not be treated as a
successful alert subscription. The main TV screen separately displays
voice/Assist connectivity and alert-receiver status. **Not shipped until
merged, built, updated on TV and physically verified.** Missed messages
are never replayed. Test a full HA restart without pressing Resume, then
send a fresh announcement and confirm it appears.

**#78 HA restart reproduction (onn 4K Pro, v0.16.8):** After restarting HA,
the receiver showed OFF/never while the TV alert status claimed permission
was denied. Home Assistant had created the matching alert-position select by
the time it was inspected. Pressing **Resume TV alerts** without changing any
permissions restored the subscription and a new popup was received. Likely
cause: the initial alert subscription arrived before the select was ready,
and Android treated the temporary error as terminal. The #86 source fix separates
temporary `tv_not_ready` responses from genuine authorization denials: retry
the former with capped backoff, never grant access to a TV without the actual
HA entity read check. A matching HACS+APK update and physical HA restart test
are required before this is marked resolved.

**Temporary workaround on v0.16.5:** Android TV Settings → Apps → Kellie's Voice
→ Force stop, then reopen the app. Test both TV sleep/wake and Home Assistant
reload, verify the HA connection binary sensor returns ON, then send a new
announcement; the receiver must not falsely claim that already-missed alerts
were delivered. Do not share HA tokens, camera URLs or private logs.

## Remote's microphone button launches Google Assistant

This is the observed behavior on this onn 4K Pro. It doesn't mean the Home Assistant app has lost its connection. Use **TALK / STOP** and speak toward the box's built-in microphone instead. Remapping ⭐ to launch the app is a planned experiment, **not implemented**. Remapping alone does not give third-party access to the Google voice remote mic.

## D-pad buttons don't work / view is cut off

The original diagnostic layout needed a D-pad navigation fix; subsequent versions accept UP/DOWN and OK. The next UI fix moves advanced settings to a menu and gives the actual answer priority. If the screen is still cut off, note which version and send a photo without credentials. The TV may overscan, so check TV picture settings if *all apps* are cropped.

## Sensitive data, reset, and support

Never share long-lived tokens, private signing keys, authentication headers, camera passwords, or voice recordings. A screenshot of the **status, transcript, and non-sensitive error code** is usually enough. To remove locally stored login data use **SETTINGS → Forget saved connection**. Uninstalling also removes it.
