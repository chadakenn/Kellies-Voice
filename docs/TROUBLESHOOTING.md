# Troubleshooting: Kellie's Voice on Google TV

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
