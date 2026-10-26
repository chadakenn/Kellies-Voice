# Frigate / Home Assistant camera screensaver (opt-in, v0.16.12)

**Status:** Included in **stable v0.16.12 (build 50)**, OFF by default. The maintainer verified four simultaneous live cameras, automatic TV screensaver launch, reboot recovery, full-screen Doorbell with audible audio, physical remote Back and gold focus on an onn Google TV. Other TV models, performance and individual camera audio tracks need community testing.

## Quick setup for new users

1. Install the matching signed Android **v0.16.12 APK** and **v0.16.12 Home Assistant HACS integration**, then restart Home Assistant.
2. In the TV app, enable **Frigate screensaver** and **Home Assistant layout**. Grant Android **Display over other apps** permission. Keep your TV paired and authenticated to HA.
3. In Home Assistant open the Kellie's Voice TV device controls. Select up to four available **camera entities**. Leave **Frigate web URL** empty to use the HA-selected grid; specifying a Frigate website URL takes priority.
4. Set **HA camera grid playback** to **Live (one camera)** and test; increase to two or four advanced only if your TV decodes HLS streams reliably. The default **Snapshots (reliable)** uses refreshed still images. The grid is always muted.
5. Let the TV screensaver start naturally. Use D-pad to highlight a camera, press **OK** to expand it with its audio *if that stream has an audio track*. Press physical **Back** to return to the silent 2×2 grid or select **Close** to exit. The focused on-screen Back/Close button has a gold border.
6. If HA shows cameras but TV shows nothing, open TV **Settings → Frigate screensaver → Screensaver status & permissions**, confirm **Cameras saved on TV**, overlay permission and watcher status, and use **Resync HA cameras** once. Report repeated startup problems.

**Safety and limits:** The TV's microphone is not activated by video playback; no audio plays from unselected grid cameras. Streams are authenticated via your HA URL and validated HLS paths; unsupported video and no-first-frame cases fall back to snapshots. Four software HLS streams may overload weaker devices and audio cannot be invented when a camera has no sound track. Frigate website mode is a different, opt-in interface. Do not publicly share unblurred home camera images.

## Historical development testing notes (dev.10 and earlier)

The sections below preserve beta diagnostics and acceptance history. Their previous "not in stable" warnings describe older beta builds, not the current v0.16.12 stable release.

## Development build 49: remote Back and high-contrast focus controls

The owner reported that dev.9's on-screen **Back to cameras** worked, but the TV remote **Back** key did not, and it was unclear whether Back or Close was focused. In dev.10, Back is intercepted before dispatch to a focused footer control; Android 13+ receives a window-attached overlay Back callback as a fallback. The selected live player should be muted and returned to the 2×2 grid without closing the screen. Both **Back to cameras** and **Close** use gold focus borders over dark teal backgrounds. D-pad Left/Right switches controls. Frigate webpage's browsing history remains supported.

Test on a physical TV in **automatic** screensaver mode, not just manual preview: open Doorbell, verify sound, press the physical Back key, confirm muted four-camera grid. Reenter Doorbell, confirm yellow focus moves from Back to Close via Right then back via Left, and OK activates the focused button. Also test API 26–32 legacy key behavior, Preview, and Android native DreamService if available. The new behavior has Android CI coverage but is not hardware-verified yet.

## Development build 48: remote-selectable full-screen camera with audio

In Home Assistant camera-grid mode (Frigate web URL **blank**), the TV remote can focus any of the four camera tiles with the D-pad. A gold outline indicates the focused camera. Press **OK** to open the selected camera in an enlarged full-screen viewing area. Press **Back** or **Back to cameras** to restore the 2×2 grid. The **Close** button still exits the entire screensaver.

All four grid streams are **muted by default**. Opening a live-streaming camera deliberately enables the chosen player's audio **only while full-screen**, if that Home Assistant HLS stream actually carries an audio track. The full-screen status says **Camera audio ON** if an audio track was selected, or reports that no audio track is present. Back and Close mute before leaving; other tiles never start audio. Home Assistant snapshots or an unavailable stream remain viewable enlarged but cannot supply sound. This does not open the microphone.

This experiment reuses the already-running selected Media3 player, does not create another decoder, and retains safe software-only decoder mode, strict HA HLS URL validation, 18-second failure fallback, and cleanup when the screensaver ends. Remote Back needs to work for preview and Android DreamService, not only overlay mode. The video uses its intrinsic aspect ratio instead of being stretched. **New in dev.9; not yet verified on TV:** owner QA is required for remote navigation, camera audio actually audible where an audio track exists, immediate mute on Back/Close, video return, and wake/reboot behavior. Works only for the HA-selected grid, not the Frigate webpage cursor.

## Development build 47: opt-in moving video from selected HA cameras

**HACS and Android v0.16.12-dev.8** add **HA camera grid playback** to the paired TV's Home Assistant controls (and to the on-TV Frigate screensaver menu). When the Frigate web URL is blank, the four existing camera selectors supply entities to a mixed live/snapshot grid. Camera dropdowns retain "snapshot" in the label for backwards compatibility; with live playback enabled, their selected cameras actually play video.

**First test:** Keep Frigate web URL blank, choose up to four HA camera entities, then select **HA camera grid playback → Live (one camera)**. The first camera streams Home Assistant HLS video, and remaining tiles continue five-second snapshots. Use Preview and confirm movement and Close. Next choose **Live (two cameras)** if performance permits. **Live (four cameras, advanced)** may tax four software video decoders on an onn box, and must never start by default. **Snapshots (reliable)** restores all snapshot tiles.

When the TV's live-camera decoder mode is **Snapshots only**, none of these HLS players start regardless of the HA setting. Safe software mode allows only explicitly software-only video codecs (no silent fallback to risky HDMI hardware decoders). HLS URLs come from HA over its authenticated WebSocket and must have the same host and a validated API HLS path. Audio tracks are disabled. An unsupported stream, decoding error or no first frame within 18 seconds falls back to the already-working snapshot. Players and WebSocket close when the overlay exits or its source changes.

**Frigate web URL still takes priority**. In web mode, Frigate itself controls live groups and Smart Streaming. This HLS feature affects only the four HA-selected camera tiles in camera-grid mode, not Frigate's own Live website page. Verify on the real onn 4K Pro before promoting this feature to stable main.

## Development build 46: diagnose blank camera-only screensaver

**Owner report:** Home Assistant shows four selected camera entities with Frigate web URL cleared, yet no overlay appears when Google TV Ambient Mode starts. The previous code required a **TV-local** configured URL or camera list before opening the overlay, and the diagnostics did not say whether settings had reached the TV. This is a synchronization/configuration issue until on-device data proves otherwise, not proof cameras themselves are offline.

With **Android v0.16.12-dev.7 / build 46**, check Kellie's Voice → **Settings → Frigate screensaver → Screensaver status & permissions**. It now shows:
- **Home Assistant layout** ON/OFF. This must be ON to accept remotely selected cameras.
- **Source on TV**, either Frigate web page, HA snapshot grid, or no source.
- **Cameras saved on TV** (0–4, without disclosing names), **HA settings link** state, and **Last HA source** update.
- Existing screensaver listener, permission, and dream event diagnostics.

If HA shows four dropdowns but the TV shows 0 cameras, choose **Resync HA cameras** in the diagnostics. This uses the existing saved HA credential, refreshes that TV's HA entity states, and does not change the microphone, reset Frigate, or overwrite HA selections. A feature-enabled HA-managed TV no longer silently skips the overlay before its first settings sync: it shows a setup message which is replaced once valid camera settings arrive. The original Frigate Live web URL mode still takes priority and is unchanged.

**Quick recovery:** Re-enter the known working local Frigate URL in the Home Assistant `Frigate web URL` text control to return to moving Frigate video. Clearing the URL is only necessary to test the alternative snapshot grid.

Build 46 uses the existing HACS camera entity protocol. Published HACS dev.7 metadata matches Android dev.7, but the TV diagnostics fix does not require a new camera entity migration.

## Development build 45: pick snapshot cameras from Home Assistant dropdowns

With **Home Assistant HACS v0.16.12-dev.6**, open **Settings → Devices & services → Kellie's Voice → your TV**. There are now four **Frigate snapshot camera 1–4** dropdowns. Each dropdown automatically lists the camera entities already exposed by your HA instance, with their friendly names and entity IDs, so you no longer need to type a `camera.*` ID.

Choose **camera 1 first**, then camera 2, 3, and 4 as needed. Choose **None** to remove a camera; remaining cameras move into the earlier slots. A camera may be selected only once. The choices refresh when HA camera entities appear/disappear and on regular HA updates; saved selections survive an HA restart through the existing `Frigate HA camera entities (manual override)` text entity. Manual editing remains possible as a fallback and updates the dropdown selections too.

**These dropdowns configure only the Home Assistant snapshot grid.** A configured **Frigate web URL takes priority**; clear it to display the 1–4 HA camera snapshots instead. Selecting four snapshots does not rearrange Frigate's own Live webpage. For the Frigate webpage, change the selected Frigate camera group and use the TV's **Select camera** cursor to choose tiles.

If the dropdown only says **None**, Home Assistant does not currently expose any `camera.*` entities. Add/expose the cameras through the existing Frigate or camera integration first. The TV never scans your network itself for camera credentials.

This dev.6 release includes a re-signed Android build 45 only to keep published development versions aligned; **HACS dev.6 uses the same camera-list protocol as Android dev.5/build 44**. You can test the dropdown in HA without replacing build 44.

## Development build 44: Home Assistant and TV remote controls

With **Android + HACS v0.16.12-dev.5** (development only), open Home Assistant → **Settings → Devices & services → Kellie's Voice → your TV → Controls**. After connecting the TV with its existing **Home Assistant layout** option, configure the per-TV entities:

- **Frigate screensaver** switch (opt-in)
- **Frigate web URL** text field (HTTP(S) only; never include credentials)
- **Frigate HA camera entities** text field (zero to four comma-separated `camera.*` IDs)
- **Frigate dashboard size** select (Large recommended)
- **Frigate page layout** select (Desktop TV recommended)
- **Frigate page fit** select (Fit more cameras recommended, Balanced = previous sizing, Larger text = fewer tiles)

The TV's existing local settings stay available as a backup. A paired TV with **Home Assistant layout ON** keeps a read-scoped background settings subscription, independently of TV announcements, and listens only for this TV's configuration entities. The connection uses the previously saved HA login. There is no microphone capture. The HACS integration and APK **must both be dev.5**; restart Home Assistant after installing HACS. If changing the controls on the TV while the voice UI is open, its changes also publish back to HA.

### Selecting Frigate cameras with the remote

The full Frigate web-page mode now has a **Select camera** button in the lower-left corner alongside **Close**. Press **OK** on Select camera to enter a visible gold cursor mode, move the cursor with D-pad arrows, and press **OK** over a camera tile or a Frigate control to click. **Back** navigates within Frigate if the page has history, otherwise leaves selection mode; **Close** dismisses the dashboard. This is touch emulation rather than full Frigate UI keyboard support, so test actual camera tiles and return navigation on the onn box.

If the bottom row is cut off, choose **Frigate page fit → Fit more cameras**. It uses a 1920 CSS-pixel page viewport; Balanced uses 1440 and Larger text 1200. Frigate's live layout may still need the camera group arranged once in its own TV browser storage. This setting does not change the underlying camera streams or your Frigate server.

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
