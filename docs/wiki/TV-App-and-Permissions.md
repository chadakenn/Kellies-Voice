# TV App and Permissions

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

The Android TV app handles the microphone, floating overlays, on-screen speech, and the optional alert receiver.

## Main controls on the TV

- **TALK / STOP** — start and stop listening for a Home Assistant Assist question.
- **SETTINGS** — Home Assistant URL/token, layout control, announcements & cameras, TV alert status, diagnostics, updates, and more.
- **Connect / reconnect** — manages the main Assist connection (not the alert receiver).

## Settings menu map

Open **Kellie's Voice → SETTINGS** with your TV remote. Use **Up / Down / OK / Back**. The menu **scrolls**: on smaller displays, lower rows are off-screen until you press **Down**.

| Setting visible on the TV | What it does |
| --- | --- |
| **Home Assistant connection** | Configure the URL and authentication token. Keep your token private. |
| **Connect / reconnect** | Reconnect the main voice/Assist connection (not the TV alert receiver). |
| **Start new conversation** | Begin another conversation with the selected Assist pipeline. |
| **Hardware microphone diagnostics** | Inspect which microphone inputs Android makes available. |
| **Microphone sensitivity: Normal** | Set pause/speech sensitivity to match a quiet room or TV playback. This is not a mic selector. |
| **Mic input detection: Safe checks ON** | Keep conservative checks that reject suspicious phantom audio inputs. |
| **Open floating voice overlay** | Display the voice overlay above supported TV content. |
| **Overlay appearance & position** | Adjust the voice overlay's location and styling. |
| **Home Assistant layout: ON** | Let the paired Home Assistant device synchronize supported layout settings. |
| **TV announcements & cameras: ON** | Enable the separate TV-alert receiver for eligible announcements and cameras. |
| **TV alert status** | Inspect and, if needed, choose **Resume TV alerts** inside the dialog. |
| **Live camera: Safe software (default)** | Set the camera player compatibility mode. |
| **Check for app updates** | Check for a newer signed Android APK. |
| **Install downloaded update** | Install an APK that has already downloaded. |
| **Forget saved connection** | Remove stored Home Assistant pairing and authentication from the TV. |

**What connected means:** A main screen showing **Home Assistant voice: ✓ connected** and **TV alerts: ✓ connected** confirms two connections, but **YOU SAID: Nothing yet** still means no transcript is displayed. The bright blue menu row indicates **selection focus**, not a toggle's ON state.

## Microphone routing and phantom inputs

Some Android TV devices expose echo-reference or playback inputs that cannot capture spoken commands. These may be mistaken for a built-in microphone.

1. Open **SETTINGS → Hardware microphone diagnostics**, and check the listed input sources.
2. Leave **Mic input detection: Safe checks ON** for normal usage and ensure Android microphone permission is granted.
3. The **Microphone routing** screen offers **Disable microphone capture on this TV**, **Try unverified microphone (compatibility override)**, and **Open microphone diagnostics**.
4. **Disable** intentionally prevents capture. The **unverified microphone** option is for troubleshooting a *real accessible mic* classified incorrectly; it **cannot bypass a device with no accessible microphone**.
5. If no usable input appears, try a supported USB microphone. The Google Assistant remote microphone is often reserved by the operating system.

Microphone support on some Philips setups remains under investigation in [issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71). A photo of a Philips **display** does not tell you whether Kellie's Voice is running on a Philips Android TV or a separate Google TV box. When reporting a problem, specify the **actual Android TV/Google TV hardware model**.

[Connected but no speech recognized?](Troubleshooting.md#connected-but-no-speech-is-recognized)

## Required permissions

- **Microphone** — needed for voice. The app attempts to reject non-microphone inputs, but microphone detection is still being investigated on some Philips TVs in v0.16.8 ([issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71)); those TVs may still show Listening and send silence. A compatible USB microphone may be needed.
- **Display over other apps** — required for floating overlays (voice card, announcements, cameras, timers, weather).

## Overlay themes (v0.16.8)

Four shipped overlay styles are available on the device page and in TV Settings:

- Copper Glass
- Compact Charcoal
- Night Signal
- Aurora Glass

When **Home Assistant layout** is ON, choosing a style in Home Assistant or on the TV updates the overlay and (in v0.16.8) syncs the main app screen, Settings dialogs, and focus colors. Reopen a Settings dialog after a change to see the latest palette.

## TV alert receiver

Announcements, cameras, timers, and the weather card use a separate opt-in receiver. Enable it with **Settings → TV announcements & cameras**.

If popups stop after standby, a reboot, or a Home Assistant restart:

1. Open **Kellie's Voice** (not Android system Settings).
2. Choose **SETTINGS** and scroll down to **TV alert status**.
3. Press OK to open the status dialog.
4. Select the **Resume TV alerts** button **inside** that dialog (it is not the neighboring toggle and not the main Connect button).

See [Troubleshooting](Troubleshooting.md#resume-tv-alerts-exact-steps) for the full recovery path and limitations.

## Updates

Use **Settings → Check for app updates** for signed APK updates. Do not uninstall the app just to update.

## Next

- [Home Assistant HACS Setup](Home-Assistant-HACS-Setup.md)
- [YAML Cookbook](YAML-Cookbook.md)
- [Troubleshooting](Troubleshooting.md)

---

[← All documentation](Home.md) · [Next: Home Assistant HACS Setup →](Home-Assistant-HACS-Setup.md)
