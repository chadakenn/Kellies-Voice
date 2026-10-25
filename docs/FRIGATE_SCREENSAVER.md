# Frigate screensaver (opt-in, draft)

**Status:** Ported to current `dev` (v0.16.11 base). Disabled by default. Not merged.

## What it does

When enabled, Kellie's Voice can show a Frigate page or up to four camera stills while the TV screensaver is running. It does **not** start the microphone.

Android will not reliably draw a normal popup over Google's own screensaver. The visible path is to choose **Kellie's Voice Frigate** as the system screensaver under **Settings → Display → Screensaver**. After that, the idle timeout opens the dashboard. **Close** or **Back** leaves it.

## Setup

1. On the TV, open **Settings → Frigate screensaver** and turn it **ON**.
2. Set a Frigate page such as `http://192.168.1.20:5000/`. Do not put a Home Assistant token or password in that address. Addresses that contain one are rejected.
3. Or leave the page blank and set up to four `camera.*` entities. Those stills use the saved Home Assistant login. The token is only an image-request header.
4. Choose **Open Android screensaver list** and pick **Kellie's Voice Frigate**.
5. **Preview Frigate dashboard** shows it immediately. **Close** or **Back** leaves it.

Default is **off**. Nothing starts until that setting is on. Reboot only restarts the listener if it was already on.

## Security

- Home Assistant tokens and passwords are never accepted in the dashboard URL.
- Camera snapshots are requested with the token only as an HTTP header.
- The screensaver does not open the microphone or change microphone privacy state.

## Real-TV testing checklist (onn 4K Pro)

- [ ] Feature OFF: reboot, confirm no screensaver service and no microphone activity.
- [ ] Turn ON, select **Kellie's Voice Frigate** as the system screensaver, wait for idle, confirm the dashboard appears.
- [ ] Preview works without waiting for idle.
- [ ] Close and Back leave the dashboard and restore normal TV playback.
- [ ] No microphone privacy indicator appears while the dashboard is showing.
- [ ] Unsafe URLs (tokens, passwords, javascript:) are rejected.
- [ ] Four-camera snapshot mode refreshes and stops network requests on dismiss.
- [ ] Existing 19 Settings items still route correctly (connection, follow-up, spoken announcements, etc.).

## Constraints

- Does not modify PR #61 actionable announcement files.
- Draft only. Do not merge to main or publish a release.
