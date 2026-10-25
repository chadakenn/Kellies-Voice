# Kellie's Voice: signed APK updates

**Canonical GitHub repository:** [chadakenn/Kellies-Voice](https://github.com/chadakenn/Kellies-Voice), renamed on 2026-10-21. GitHub release asset URLs preserve that exact capitalization.

The Android package ID remains `com.example.tvvoiceprobe`, and the original private release-signing key remains in use. **Never uninstall to rebrand.** Install a same-signed update over the existing app to preserve Home Assistant credentials, settings and the ⭐ shortcut.

## First update after the repository rename

The installed v0.10 and earlier updater may reject a release asset under the renamed path, because it validates the exact filename and case-sensitive repository prefix. GitHub redirects alone do not cure that limitation.

1. Visit [GitHub Actions for Kellie's Voice](https://github.com/chadakenn/Kellies-Voice/actions/workflows/build-apk.yml).
2. Open the latest successful **Build Kellie's Voice Signed APK** run and download the `kellies-voice-signed` artifact.
3. Extract `app-release.apk`. Install it **over** the existing app on the onn 4K Pro, without uninstalling. If Android reports a signature mismatch, stop and report it; do not clear app data.
4. Confirm the saved Home Assistant connection, ⭐ overlay shortcut, microphone permission, voice responses and warm copper branding.
5. Test **SETTINGS → Check for app updates**. A successful check with only the older public v0.6 published should report that v0.11 is up to date.

## Release URL compatibility

The updater requests `https://api.github.com/repos/chadakenn/Kellies-Voice/releases/latest` and allows release APK assets only at `https://github.com/chadakenn/Kellies-Voice/releases/download/`.

A TV left on the default **Stable** channel never asks for prereleases. **Settings → Check for app updates → Development prereleases** is opt-in and only then reads the public release list. It still refuses drafts, stable tags, other hosts, and a download until the owner taps Download and Android's installer. It cannot see a private Actions artifact. See [DEV_CHANNEL.md](DEV_CHANNEL.md).

The old **Julia-Voice** URL is no longer a fallback. It did not help TVs still running v0.10 or earlier — those builds have the old prefix compiled in and still need the one-time sideload above. A fallback in the new app only mattered when `Kellies-Voice` itself returned 404, which would also let a recreated `Julia-Voice` repository offer a download. After the SHA-256 check, the app now rejects an APK whose package name is not `com.example.tvvoiceprobe` or whose signing certificate does not match the installed app, before Android's installer opens.

The GitHub rename has **already occurred**. Do not create a new repository at the old `Julia-Voice` path. For local clones, use `git remote set-url origin https://github.com/chadakenn/Kellies-Voice.git`.

## Public release, only with owner approval

Normal pushes create a **signed development APK artifact**, not a public GitHub Release. After physical TV testing, the owner can run **Build Kellie's Voice Signed APK** manually with `publish_release` checked. That workflow builds the signed APK, verifies the signature, and publishes `app-release.apk` and checksum metadata `update.json`.

**v0.15.0 release:** Publication was owner-authorized. A one-time `[publish-v0.15.0]` gated main-branch workflow path builds/tests the latest app, signs and verifies its APK, and uploads `app-release.apk` with SHA-256 `update.json`. Later releases still require a new owner-authorized version bump/manual workflow.

**v0.15.1 patch release:** Adds a manual pairing fallback and the local Home Assistant brand icon. It retains the same signed Android package identity and publishes after Android and HA tests. HACS may still show a generic icon due to an upstream HACS frontend issue, while Home Assistant's native integration page uses the bundled brand asset.

## v0.16.0: update directly from Kellie's Voice (October 2026)

With version 0.15.1 already installed, open the **Kellie's Voice** TV app → **SETTINGS → Check for app updates**. When v0.16.0 appears, choose **Download update** and then **Install downloaded update**, accepting Android's normal package-installer prompt. You may have to grant Kellie's Voice **Install unknown apps** once. Do not uninstall or clear app data.

The app's built-in updater reads the public stable GitHub Release, requires `app-release.apk` and `update.json`, compares Android `versionCode` values, validates the SHA-256 checksum, package ID and installed signing certificate, then delegates installation to Android. A GitHub Actions artifact **without** a published Release is **not** visible to in-app updates. Developer/prerelease builds are deliberately not offered by the stable update endpoint.

After updating the TV, in **HACS** update the Home Assistant Kellie's Voice integration to **v0.16.0**, then restart HA. The new live camera action is experimental until hardware-tested; announcement and Frigate snapshot popups remain the tested default. The OTA update does not replace or upgrade the HA integration automatically.

The release workflow publishes only when explicitly approved and the signed APK passes tests/signature verification. Normal GitHub pushes continue producing signed artifacts but do not publish user-facing app updates.

## v0.16.1 live streaming TV compatibility update

Use `Kellie's Voice → Settings → Check for app updates` after the v0.16.1 GitHub Release is published. This patch uses `TextureView` for Android TV overlays and shows a real, **clearly labeled snapshot fallback** when live streaming fails. The live camera is still experimental and needs moving-video proof on an onn TV. No changes to the Frigate person popups, HA pairing or TV announcement services. The HACS integration does not require a new version for this Android-only update.

## v0.16.2: dismiss camera by voice, timed camera requests

The new signed Android TV update can receive a `kellies_voice_hide_camera` event and immediately dismiss only the active live or snapshot camera popup. A matching v0.16.2 HACS integration registers `kellies_voice.hide_camera`, with optional `device_id` to target a single paired TV. Regular announcements and voice overlay remain untouched. The player stops and releases its resources. The existing `show_live_camera` action accepts durations from 15 to 180 seconds, including 30 seconds. Scripts exposed to Assist can optionally accept a `duration_seconds` field, although named 30-second scripts may be more reliable for voice agents that cannot pass script parameters.

**Upgrade sequence:** TV app Settings → Check for app updates → install v0.16.2 over v0.16.1; **HACS** → Kellie's Voice integration → update to v0.16.2 → restart Home Assistant; open the TV app so the event receiver reconnects. Do not uninstall or clear credentials.

**Test:** call `kellies_voice.show_live_camera` with `duration_seconds: 30` and confirm it automatically disappears; during a longer 90-second camera view call `kellies_voice.hide_camera` and confirm immediate dismissal without changing the TV playback. Confirm the command does not dismiss an on-screen text-only announcement. Use voice-assistant scripts for natural phrases.

## v0.16.3: per-TV alert location, size and safer restarts

**Android:** open Kellie's Voice → **Settings → Check for app updates** → install the **signed v0.16.3 APK over v0.16.2**. Keep credentials and app data. **Home Assistant:** open HACS → Kellie's Voice → update to **v0.16.3** and restart Home Assistant. These are two independent updates.

In HA **Settings → Devices & services → Kellie's Voice → your TV** adjust the **TV alert position** and **TV alert size** entities. Five locations and Small/Medium/Large sizes control on-screen announcements, Frigate camera snapshots and muted live video. They don't move the voice assistant overlay. Defaults are **Top right** and **Medium**. Changes affect the next popup, not one already visible. No scripts need to be installed just to use these layout settings.

With **TV announcements & cameras** already enabled, Android attempts to restore the event listener after an app update or reboot without activating the microphone. This is best effort, *not* guaranteed always-on behavior. If Android prevents background starts, open the main app or go to **Settings → TV alert status → Resume TV alerts**. Force-stopping or some standby/OS conditions will still require opening the app. Disabling alerts stays disabled. The receiver never replays old events after a gap.

### Real onn 4K Pro acceptance checklist (not yet verified)

1. Adjust both HA controls and verify **next** live doorbell, live driveway, still snapshot and announcement popup size/position, while normal TV playback continues.
2. Verify the **voice assistant overlay stays put** when only the TV alert controls change.
3. During a live 90-second camera view say **“Julia, close the camera”** and confirm prompt dismissal. For a fixed 30-second Assist script, verify the timeout without stopping TV playback.
4. Install the APK in place with alerts enabled; confirm alerts recover automatically or TV alert status explains that Android blocked it.
5. Reboot the TV and test delivery. Repeat with the TV alerts setting OFF (must remain OFF).
6. Revoke **Display over other apps**, unplug networking, resume standby, and observe the safe status/reconnect behavior. Events from outages must not replay.

See [voice scripts and Assist setup](VOICE_CAMERA_SCRIPTS.md) for copy/paste actions and the tested camera names only as examples, not universal device IDs.
