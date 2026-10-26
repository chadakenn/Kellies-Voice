# TV App and Permissions

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

The Android TV app handles the microphone, floating overlays, on-screen speech, and the optional alert receiver.

## Main controls on the TV

- **TALK / STOP** — start and stop listening for a Home Assistant Assist question.
- **SETTINGS** — Home Assistant URL/token, layout control, announcements & cameras, TV alert status, diagnostics, updates, and more.
- **Connect / reconnect** — manages the main Assist connection (not the alert receiver).

![Main screen on an onn 4K Pro, voice and TV alerts connected](https://github.com/chadakenn/Kellies-Voice/wiki/raw/master/onn-home-connected.jpg)

![Settings menu on the TV](https://github.com/chadakenn/Kellies-Voice/wiki/raw/master/onn-settings-menu.jpg)

The settings list scrolls. Microphone routing is under **Mic input detection**.

![Microphone routing choices, with safe checks available](https://github.com/chadakenn/Kellies-Voice/wiki/raw/master/onn-mic-routing.jpg)

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
