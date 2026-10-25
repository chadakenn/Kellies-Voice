# Frigate screensaver (opt-in, draft)

**Status:** Development-only feature in signed v0.16.12-dev.4 (build 43); not in stable v0.16.11. Disabled by default. Dev.4 adds watcher recovery after boot/APK update plus diagnostics for Google TV Ambient Mode.

## What it does

When enabled, Kellie's Voice can show a Frigate page or up to four camera stills while the TV screensaver is running. It does **not** start the microphone.

Google TV's **Settings → System → Ambient Mode → Source** typically lists Google Photos, Art Gallery, and Custom AI Art only. It is **not a third-party DreamService picker**; Kellie's Voice will not appear there. Our opted-in foreground watcher listens for Android's `DREAMING_STARTED`/`DREAMING_STOPPED` and attempts a display-over-other-apps dashboard over existing Ambient Mode. Some Google TV firmware does not expose dream broadcasts or may block overlays. On compatible Android TV builds, an actual third-party screensaver picker may offer **Kellie's Voice Frigate** as an alternate system DreamService. **Close** or **Back** leaves the overlay.

## Setup

1. Open **Kellie's Voice → Settings → Frigate screensaver**. This opens a dedicated configuration menu in v0.16.12-dev.2 and later.
2. Choose **Turn Frigate screensaver ON**.
3. **Frigate web page option:** choose **Frigate web URL**, enter the URL for *your* local Frigate instance (for example `http://frigate.local:5000/`), and select **Save**. Never paste login credentials, access tokens, or passwords in the URL.
4. **Camera grid option (instead of Frigate page):** clear the **Frigate web URL**, choose **Home Assistant cameras**, and enter one to four *actual Home Assistant* entity IDs separated by commas, such as `camera.driveway, camera.doorbell, camera.back_garage`. Find exact camera IDs under **Home Assistant → Developer Tools → States**. Snapshots refresh about every 5 seconds. It does not use raw Frigate labels/RTSP URLs. These snapshots use the previously saved HA connection; the token is only an HTTP header.
5. For a full browser-page dashboard matching the laptop's multi-camera layout, choose **Dashboard size → Large** and **Frigate page layout → Desktop TV** (now the default). Desktop TV uses a wide Frigate web viewport, overview fit, and the actual installed Chromium version in a desktop browser identifier. Choose **Automatic** to restore the old embedded mobile/tablet layout if the site or camera playback fails. This setting only affects Frigate/website mode; the 1-4 Home Assistant snapshot grid is unchanged.
6. Ensure **Display over other apps** is allowed for Kellie's Voice. You can now choose **Screensaver status & permissions** to see if the watcher is RUNNING, whether Google TV sent a dream-start event, and why the overlay was or wasn't shown.
7. **Test Frigate as TV overlay** opens the same full-screen service overlay over the current app. Close it with **Close**. This proves overlay rendering, but is **not** proof Google Ambient Mode delivers an idle event. The existing **Preview Frigate dashboard** uses a regular foreground Activity instead.
8. If your TV offers a genuine third-party system screensaver picker, you may select **Kellie's Voice Frigate** there. Do **not** expect it in Google TV **Ambient Mode → Source**, which only offers Google's built-in sources.
9. Choose **Preview Frigate dashboard** to test immediately. **Close** or **Back** exits. If you enter both modes, the Frigate web URL takes priority.

Default screensaver is **off**; page layout defaults to **Desktop TV** once enabled. Nothing starts until that setting is on. The already-opted-in watcher is requested again on app update and reboot (independently of TV alerts), and returns `START_STICKY` after process eviction. Individual firmware may deny background starts. If it says NOT RUNNING after boot, open **Screensaver status & permissions → Try to resume watcher**; nothing starts a microphone.

**Important:** Frigate stores camera-group tile layouts and stream choices in each browser's storage. The TV WebView is not your laptop's browser, so even in Desktop TV mode you may need to select the same Frigate group and arrange its tiles once on the TV. TV desktop mode requests a multi-column web layout but does not copy browser-local dashboard settings from the laptop.

## Security

- Home Assistant tokens and passwords are never accepted in the dashboard URL.
- Camera snapshots are requested with the token only as an HTTP header.
- The screensaver does not open the microphone or change microphone privacy state.

## Real-TV testing checklist (onn 4K Pro)

- [ ] Feature OFF: reboot, confirm no screensaver service and no microphone activity.
- [ ] Turn ON, enter a local Frigate URL, preview, then clear URL; enter valid camera.* IDs and preview snapshots. Reject secret-bearing URLs, invalid entity IDs, and more than four cameras.
- [ ] Google TV source picker only shows Google Photos / Art Gallery / Custom AI Art: verify docs and UI do not claim Kellie's Voice must appear there.
- [ ] Toggle OFF then ON with **Display over other apps** enabled, check watcher RUNNING. Install signed dev APK over older version, then reboot; confirm watcher is requested again without opening the app. Existing TV notifications must not be interrupted.
- [ ] Use **Test Frigate as TV overlay** and **Close**. Confirm it does not start voice capture. Then allow Google Ambient Mode to start and check **Screensaver status & permissions** afterward. If DREAMING_STARTED arrived but overlay was blocked, capture the exact outcome; if it did not arrive, do not claim automatic Google Ambient support.
- [ ] On compatible Android TV builds offering a third-party DreamService picker, select **Kellie's Voice Frigate**, wait for idle and confirm the dashboard appears.
- [ ] In **Large + Desktop TV**, compare the web Frigate page against the laptop. Confirm multi-column camera layout and no massive single tile / mobile bottom navigation. Check stream decoder, navigation and Close/Back.
- [ ] Switch to **Automatic**, preview, confirm old WebView behavior, then return to Desktop TV. No change to Home Assistant snapshots or Frigate server settings.
- [ ] Preview works without waiting for idle.
- [ ] Close and Back leave the dashboard and restore normal TV playback.
- [ ] No microphone privacy indicator appears while the dashboard is showing.
- [ ] Unsafe URLs (tokens, passwords, javascript:) are rejected.
- [ ] Four-camera snapshot mode refreshes and stops network requests on dismiss.
- [ ] Existing 19 Settings items still route correctly (connection, follow-up, spoken announcements, etc.).

## Constraints

- Does not modify PR #61 actionable announcement files.
- Dev-only. Do not promote to main or publish a stable release until the owner's onn 4K Pro hardware QA passes.
