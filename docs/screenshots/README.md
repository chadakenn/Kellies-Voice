# Real-device screenshot gallery 📸

## Direct ADB captures for PR #192 — development preview

[October 28 onn 4K Pro capture gallery](PR-192-CAPTURES.md): six Compose settings categories, all five local theme selections, a local announcement sample, a heavily redacted idle voice overlay and a fully camera-redacted screensaver preview. These were captured from the existing **0.16.14-test debug APK**. **Compose is development-only, not released Settings.** The installed APK predates the latest popup/theme fixes; the images do not verify those fixes. New captures use opaque privacy masks, not reconstructed UI or promotional artwork. See the gallery for each image's limits.

> **Updated October 28, 2026:** Public stable v0.16.14 has a **six-category Settings hub** and optional **Okay Nabu**. The older `onn-settings-menu.jpg` shows the previous long Settings list and is historical, **not** the new v0.16.14 UI. New unredacted captures must be reviewed for private details before embedding.

Phone photos from an onn 4K Pro are in this folder; a small selection appears in the main README. The SVG previews and `night-signal.png` are still **design references**, not photos of the TV.

| File | What it is |
| --- | --- |
| [onn-voice-time.jpg](onn-voice-time.jpg) | Voice overlay over Google TV. Question: "What time is it?" Answer: "4:23 AM". |
| [onn-voice-doorbell.gif](onn-voice-doorbell.gif) | Voice overlay while Home Assistant answers "Show me the doorbell camera." |
| [onn-snapshot-popup.jpg](onn-snapshot-popup.jpg) | Still snapshot popup, "Snapshot Test After Reopening App." Not live video. |
| [onn-live-doorbell.jpg](onn-live-doorbell.jpg) | Live doorbell card starting, muted, with the voice card still visible. One photo, not a motion recording. |
| [onn-home-connected.jpg](onn-home-connected.jpg) | Main screen on the onn. Voice is connected and TV alerts show connected. The address is the normal `homeassistant.local` name, not a token. |
| [onn-settings-menu.jpg](onn-settings-menu.jpg) | **Historical screenshot:** old v0.16.12-or-earlier mixed Settings list, not the current six-category v0.16.14 hub. |
| [onn-mic-routing.jpg](onn-mic-routing.jpg) | Microphone routing. Safe checks are on. This does not capture the Google remote microphone. |
| [onn-tv-alert-status.jpg](onn-tv-alert-status.jpg) | TV alert status while Home Assistant is denying legacy event subscriptions. Resume TV alerts is the button inside this dialog. |
| [onn-live-camera-grid.jpg](onn-live-camera-grid.jpg) | Owner-tested four muted live cameras (living room, doorbell, driveway, back) with Close; camera footage pixelated for privacy. Included in stable v0.16.12. |
| [onn-frigate-dashboard.jpg](onn-frigate-dashboard.jpg) | The opt-in Frigate website over the TV, with Kellie's Voice Close button. Private camera footage and people in the event strip are obscured. |
| [onn-frigate-screensaver-diagnostics.jpg](onn-frigate-screensaver-diagnostics.jpg) | Frigate screensaver diagnostics. In this photo the watcher is not running and no Android dream event has arrived. |
| [wake-okay-nabu-office-lights.mp4](wake-okay-nabu-office-lights.mp4) | Real **development demo video** of Okay Nabu opening the overlay and an office-lights command. Not evidence of zero-touch reboot wake or universal TV compatibility; review video frames for private details before promoting it broadly. |

## How the v0.17 QA screenshots will be used

The [PR #192 ADB capture index](PR-192-CAPTURES.md) has **all six Compose settings sections**, local Aurora/Copper/Charcoal/Night/Neon theme selections, a neutral announcement popup preview, a heavily masked voice overlay and a fully redacted camera-area screensaver preview. These real device pixels are already in this **combined QA branch** and can be linked from the v0.17 README, HACS description and source Wiki, with accurate labels. Their source debug app was **0.16.14-test**, not the final v0.17 candidate. See [v0.17 QA release checklist](../V017_BETA_STATUS.md).

Once a release candidate passes hardware testing, capture the **final version** again before making those developer images the primary public screenshots. The privacy-redacted screensaver image does not prove live camera playback; use the previously hardware-verified live-grid photo for that claim.

## Requested captures

| Screenshot / photo | What it should show | Proposed file |
| --- | --- | --- |
| TV app home/settings | Real on-TV app showing name, connection status and settings, no secrets | Have [onn-home-connected.jpg](onn-home-connected.jpg); historical long-menu [onn-settings-menu.jpg](onn-settings-menu.jpg). **Current six-category Settings screenshot still wanted.** |
| Voice overlay | Kellie's Voice overlay on Google TV with a harmless Assist request and reply | Have [onn-voice-time.jpg](onn-voice-time.jpg) and [onn-voice-doorbell.gif](onn-voice-doorbell.gif) |
| Announcement popup | The opt-in on-screen text announcement above a TV app | Still wanted |
| Frigate popup | A real camera **still snapshot** overlay | Have [onn-snapshot-popup.jpg](onn-snapshot-popup.jpg) |
| HA device settings | HA device page with overlay position, size, style, auto-hide and auto-listen | `actual-ha-device-controls.png` |
| Live camera | Live doorbell card on the onn. Moving playback was already verified; this file is one photo of the card opening | Have [onn-live-doorbell.jpg](onn-live-doorbell.jpg) |

| Okay Nabu demo | Hands-free command after explicit wake opt-in, with no identifying home content | Have [wake-okay-nabu-office-lights.mp4](wake-okay-nabu-office-lights.mp4) (development capture); final signed-release/reboot demo still wanted |

## Easy way to capture and contribute

**Phone photos are okay.** Pause on each screen or use a 20–30 second test popup, photograph the TV square-on with minimal glare, and upload the originals in a GitHub issue or chat. A direct Android screenshot is even better if you already have ADB working. No rooting or ADB setup is required just to help with documentation.

If using an existing ADB connection, you can use `adb shell screencap -p /sdcard/kellies-voice.png`, then `adb pull /sdcard/kellies-voice.png`. Some protected video apps intentionally block or black out screenshots; in that case, a phone photo showing the popup is more honest and useful.

**Before putting images in a public repo**, remove or blur:
- Home Assistant URLs, logins, access tokens, TV pairing IDs, Wi-Fi and device details.
- Private camera views, house numbers, faces, street addresses, vehicle/license plate details, family photos and notifications.
- Streaming-account profiles or copyrighted TV-program imagery. A neutral TV background is ideal.

Cropping, straightening and redacting private details is fine; don't redraw the on-screen UI or imply an edited concept image is a device capture. Use meaningful ALT text such as "Real onn 4K Pro Frigate driveway snapshot popup over TV content." If showing a camera feed, state whether it is a **snapshot** or **live video**. Please get consent before publishing photos containing recognizable people.

## Gallery publication checklist

1. Verify the image was really taken from the running app on Android TV.
2. Record the tested app/integration version and TV model in the PR.
3. Keep user/private details out of the public image.
4. Add each verified image here and to the root README gallery with a truthful caption.
5. Update the HACS `info.md` and integration README to display/link the same gallery. Do not replace known working examples with illustrations.
6. Remove "capture needed" notes only for the specific screenshots we actually have.

## Illustrative design references (not device screenshots)

- [Old reconstructed Assist response](conversation-preview.svg)
- [Old reconstructed floating overlay](overlay-preview.svg)
- [Night Signal visual concept](night-signal.png)
- [Alternate Night Signal concept](night-signal-warm-preview.svg)

These remain available as design references but are deliberately separate from the public real-device gallery.
