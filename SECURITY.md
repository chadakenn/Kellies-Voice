# Security and privacy

This repo is public, but **your Home Assistant deployment, access tokens, signing material, camera credentials and local recordings are private**.

## Home Assistant connection

- The Android app authenticates to `/api/websocket` using either an explicitly configured Home Assistant token (legacy/manual mode) or an opt-in, owner-approved renewable login for a dedicated TV user (v0.17.0 public preview). The APK contains no owner token. The renewable login requires a trusted HTTPS Home Assistant origin for ongoing authentication; local HTTP-only auto-login is not supported yet ([#262](https://github.com/chadakenn/Kellies-Voice/issues/262)).
- Local credentials are encrypted at rest using an **Android Keystore-backed key**; Android backup of app data is disabled.
- Pressing **TALK** intentionally streams microphone samples to the configured HA server for processing. The diagnostic MIC/VOICE_RECOGNITION tests only compute signal levels and don't upload or save recordings.
- Use an HTTPS URL with a valid trusted certificate when feasible. **The currently allowed HTTP/WS connection sends your access token and microphone audio unencrypted on the network.** Only consider it on a trusted, isolated LAN and plan a trusted TLS endpoint. We must not add a trust-all-certificate bypass.
- In the opt-in owner-approved pairing flow, verify the same eight-digit code on the physical TV and in the authenticated Home Assistant owner panel before approving. The TV should receive a dedicated non-admin login, not the owner's administrator credentials. This protocol is still awaiting independent security review ([#214](https://github.com/chadakenn/Kellies-Voice/issues/214)); a non-admin HA user may still control all entities made available to that account. **Never put a bearer token in a QR code or public URL.**
- TTS sources are constrained to the configured HA server; only explicit HA TTS proxy paths may be rewritten to it. External playback and Frigate cameras need separate authorization and allowlists.

## Android signing key

- The current GitHub Actions release-signing key is recovered from encrypted repository secrets. **No keystore, password or base64 private key belongs in commits, issues, logs, artifacts, or screenshots.**
- Keep a private backup of the original keystore and its password. Without it, future installs cannot safely update over the already signed app.
- The GitHub workflow validates the keystore alias and verifies the release APK signature before publishing.
- **Never upload the private signing-key backup ZIP to this public repository.**
- A public signed release APK is safe to distribute because it contains only the **public signing certificate**, not the private signing key.

## Reporting a possible vulnerability

Please do not publish exploit details, tokens, private HA URLs or credentials in a public GitHub issue. Instead contact the repository owner through an appropriate private channel, or use GitHub's private vulnerability reporting if enabled. Include a short description, affected app version and reproduction steps without secrets.

## Scope and known limitations

This is an **experimental community integration**, not a hardened smart-home security boundary. Owner-approved renewable pairing is available as an opt-in public preview but has not completed independent security review. Device revocation UX remains limited; clearing a TV login does not automatically invalidate an issued Home Assistant credential. Google remote microphone-button takeover and broader device support remain platform-dependent or unfinished.

If a device or APK is compromised, revoke the affected Home Assistant token from the Home Assistant user profile, then create a new one. Removing the saved TV connection does **not** revoke a token at Home Assistant.
