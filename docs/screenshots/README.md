# Real-device screenshot gallery 📸

Phone photos from an onn 4K Pro are now in this folder and in the main README. The SVG previews and `night-signal.png` are still **design references**, not photos of the TV.

| File | What it is |
| --- | --- |
| [onn-voice-time.jpg](onn-voice-time.jpg) | Voice overlay over Google TV. Question: "What time is it?" Answer: "4:23 AM". |
| [onn-voice-doorbell.gif](onn-voice-doorbell.gif) | Voice overlay while Home Assistant answers "Show me the doorbell camera." |
| [onn-snapshot-popup.jpg](onn-snapshot-popup.jpg) | Still snapshot popup, "Snapshot Test After Reopening App." Not live video. |
| [onn-live-doorbell.jpg](onn-live-doorbell.jpg) | Live doorbell card starting, muted, with the voice card still visible. One photo, not a motion recording. |
| [wake-okay-nabu-office-lights.mp4](wake-okay-nabu-office-lights.mp4) | Phone video, about 15 seconds. Hands-free Okay Nabu over the Google TV apps screen. The overlay connects, listens, shows "Turn on office lights.", and Home Assistant answers "Turned on the light." The set in the frame is a Philips. |

Still wanted: the settings screen, a text-only announcement with no camera picture, and the Home Assistant device page.

## Requested captures

| Screenshot / photo | What it should show | Proposed file |
| --- | --- | --- |
| TV app home/settings | Real on-TV app showing name, connection status and settings, no secrets | `actual-tv-settings.png` |
| Voice overlay | Kellie's Voice overlay on Google TV with a harmless Assist request and reply | Have [onn-voice-time.jpg](onn-voice-time.jpg) and [onn-voice-doorbell.gif](onn-voice-doorbell.gif) |
| Announcement popup | The opt-in on-screen text announcement above a TV app | Still wanted |
| Frigate popup | A real camera **still snapshot** overlay | Have [onn-snapshot-popup.jpg](onn-snapshot-popup.jpg) |
| HA device settings | HA device page with overlay position, size, style, auto-hide and auto-listen | `actual-ha-device-controls.png` |
| Live camera | Live doorbell card on the onn. Moving playback was already verified; this file is one photo of the card opening | Have [onn-live-doorbell.jpg](onn-live-doorbell.jpg) |

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
