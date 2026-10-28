# TV App and Permissions

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

The Android TV app handles the microphone, floating overlays, on-screen speech, and the optional alert receiver.

## Main controls on the TV

- **TALK / STOP** — start and stop listening for a Home Assistant Assist question.
- **SETTINGS** — Home Assistant URL/token, layout control, announcements & cameras, TV alert status, diagnostics, updates, and more.
- **Connect / reconnect** — manages the main Assist connection (not the alert receiver).

## Settings menu map

Open **Kellie's Voice → SETTINGS** with the remote. v0.16.14 uses **six focusable categories**, not one scrolling list. Choose a category with D-pad and OK; **Back** returns to the category hub.

| Category | Key controls |
| --- | --- |
| **Home Assistant** | Connect/reconnect, edit URL/token, start new conversation, HA-managed TV layout |
| **Voice & microphone** | Sensitivity, follow-up, microphone input checks and diagnostics, **Wake word: OFF/LISTENING/STOPPED** |
| **Overlay & appearance** | Open voice overlay, choose appearance/position/controls |
| **Alerts & cameras** | Announcements, spoken volume, camera decoder, **TV alert status → Resume TV alerts** |
| **Screensaver** | Frigate screensaver setup and preview |
| **Updates & account** | Stable/development update channel, install downloaded APK, forget saved HA connection |

**What connected means:** the main voice socket and TV alerts receiver are separate. A connected label does **not** prove a microphone captured speech or a specific popup rendered. The colored highlighted row indicates remote **focus**, not whether a setting is ON.

The older [onn-settings-menu.jpg](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/screenshots/onn-settings-menu.jpg) photo shows the **previous** long settings list, not v0.16.14's categories. We still need a privacy-redacted six-category photo. See [current Settings guide](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/TV_SETTINGS.md).
## Microphone routing and phantom inputs

Some Android TV devices expose echo-reference or playback inputs that cannot capture spoken commands. These may be mistaken for a built-in microphone.

1. Open **SETTINGS → Voice & microphone → Test microphone hardware**, and check the listed input sources.
2. Leave **Microphone input checks (Safe)** for normal usage and ensure Android microphone permission is granted.
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

Announcements, cameras, timers, and the weather card use a separate opt-in receiver. Enable it with **Settings → Alerts & cameras → TV notifications & camera popups**.

If popups stop after standby, a reboot, or a Home Assistant restart:

1. Open **Kellie's Voice** (not Android system Settings).
2. Choose **SETTINGS → Alerts & cameras → TV alert status**.
3. Press OK to open the status dialog.
4. Select the **Resume TV alerts** button **inside** that dialog (it is not the neighboring toggle and not the main Connect button).

See [Troubleshooting](Troubleshooting.md#resume-tv-alerts-exact-steps) for the full recovery path and limitations.

## Updates

Use **Settings → Updates & account → Update channel** for signed APK updates. Do not uninstall the app just to update.

## Next

- [Home Assistant HACS Setup](Home-Assistant-HACS-Setup.md)
- [YAML Cookbook](YAML-Cookbook.md)
- [Troubleshooting](Troubleshooting.md)

---

[← All documentation](Home.md) · [Next: Home Assistant HACS Setup →](Home-Assistant-HACS-Setup.md)

## Optional Okay Nabu wake listener (v0.16.14)

Choose **Voice & microphone → Wake word** to enable local phrase detection. Default **OFF**; **LISTENING** means the Android microphone is currently capturing audio locally. Android 14+ may require launching the app once after reboot. Optionally set wake audio ducking to Off, 25%, 50% or 75%, applied **after** the phrase. Disable microphone capture and Forget HA connection both disarm wake. [More details and a real-device demo](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/WAKE_WORD.md).
