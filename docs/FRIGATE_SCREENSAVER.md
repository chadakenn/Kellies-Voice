# Frigate screensaver (opt-in, draft)

**Status:** Development-only feature in signed v0.16.12-dev.2 (build 41); not in public stable v0.16.11. Disabled by default. The first dev.1 package lacked the configuration screen; use dev.2.

## What it does

When enabled, Kellie's Voice can show a Frigate page or up to four camera stills while the TV screensaver is running. It does **not** start the microphone.

Android will not reliably draw a normal popup over Google's own screensaver. The visible path is to choose **Kellie's Voice Frigate** as the system screensaver under **Settings → Display → Screensaver**. After that, the idle timeout opens the dashboard. **Close** or **Back** leaves it.

## Setup

1. Open **Kellie's Voice → Settings → Frigate screensaver**. This opens a dedicated configuration menu in v0.16.12-dev.2 and later.
2. Choose **Turn Frigate screensaver ON**.
3. **Frigate web page option:** choose **Frigate web URL**, enter the URL for *your* local Frigate instance (for example `http://frigate.local:5000/`), and select **Save**. Never paste login credentials, access tokens, or passwords in the URL.
4. **Camera grid option (instead of Frigate page):** clear the **Frigate web URL**, choose **Home Assistant cameras**, and enter one to four *actual Home Assistant* entity IDs separated by commas, such as `camera.driveway, camera.doorbell, camera.back_garage`. Find exact camera IDs under **Home Assistant → Developer Tools → States**. Snapshots refresh about every 5 seconds. It does not use raw Frigate labels/RTSP URLs. These snapshots use the previously saved HA connection; the token is only an HTTP header.
5. Optionally choose **Dashboard size** (Small, Medium or Large). The size changes the dashboard panel, not the actual Google TV screensaver configuration.
6. Choose **Choose Android screensaver** and select **Kellie's Voice Frigate** in system Display / Screensaver settings. Some firmware doesn't expose this choice; if so, system screensaver takeover may be unavailable.
7. Choose **Preview Frigate dashboard** to test immediately. **Close** or **Back** exits. If you enter both modes, the Frigate web URL takes priority.

Default is **off**. Nothing starts until that setting is on. Reboot only restarts the listener if it was already on.

## Security

- Home Assistant tokens and passwords are never accepted in the dashboard URL.
- Camera snapshots are requested with the token only as an HTTP header.
- The screensaver does not open the microphone or change microphone privacy state.

## Real-TV testing checklist (onn 4K Pro)

- [ ] Feature OFF: reboot, confirm no screensaver service and no microphone activity.
- [ ] Turn ON, enter a local Frigate URL, preview, then clear URL; enter valid camera.* IDs and preview snapshots. Reject secret-bearing URLs, invalid entity IDs, and more than four cameras.
- [ ] Select **Kellie's Voice Frigate** as the system screensaver, wait for idle, confirm the dashboard appears.
- [ ] Preview works without waiting for idle.
- [ ] Close and Back leave the dashboard and restore normal TV playback.
- [ ] No microphone privacy indicator appears while the dashboard is showing.
- [ ] Unsafe URLs (tokens, passwords, javascript:) are rejected.
- [ ] Four-camera snapshot mode refreshes and stops network requests on dismiss.
- [ ] Existing 19 Settings items still route correctly (connection, follow-up, spoken announcements, etc.).

## Constraints

- Does not modify PR #61 actionable announcement files.
- Dev-only. Do not promote to main or publish a stable release until the owner's onn 4K Pro hardware QA passes.
