# Getting Started

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

You need **both** parts of Kellie's Voice:

1. The **signed Android TV / Google TV APK** (installed on the TV)
2. The **matching Home Assistant custom integration** (installed via HACS)

They are separate downloads. Installing one does not install the other. Always use matching versions (currently **v0.16.9**).

> **Julia is not required.** It is only the maintainer's personal assistant name. Use your own Home Assistant Assist pipeline.

## What you need

- A working Home Assistant installation with an Assist pipeline (speech-to-text, conversation agent, and text-to-speech if you want spoken answers).
- An Android TV or Google TV device that can sideload an APK and grant microphone + "Display over other apps" permissions. The **onn 4K Pro** is the primary field-tested device. Other models (including many Philips TVs) may work but need community testing. Some TVs have no accessible microphone for third-party apps.
- The TV must be able to reach your Home Assistant URL on the network.
- A long-lived access token created in your Home Assistant user profile (Security section). **Never share this token.**

## Step 1: Install the Android APK

1. Download `app-release.apk` from the [latest GitHub Release](https://github.com/chadakenn/Kellies-Voice/releases/latest).
2. Transfer it to the TV (USB, "Send Files to TV", or ADB).
3. Open the APK on the TV and complete the installer (allow "Install unknown apps" if asked).
4. Launch **Kellie's Voice** from the Apps list.

**Updating an existing signed app:** use **Settings → Check for app updates** or install a newer signed APK over the current one. **Do not uninstall** — that deletes your saved connection.

## Step 2: Connect the TV to Home Assistant

1. On the TV open **Kellie's Voice → SETTINGS → Configure Home Assistant**.
2. Enter your Home Assistant base URL (e.g. `https://ha.example.net` or `http://homeassistant.local:8123`) and your long-lived access token.
3. Choose **Save and connect**. You should see **Connected and authenticated**.
4. Grant the microphone permission when prompted.
5. Select **TALK** with the remote, speak a simple question such as "What time is it?", and confirm the answer appears (and is spoken if TTS is configured).

**Note:** The Google Assistant microphone button on the remote usually still opens Google, not Kellie's Voice. Speak toward the TV's built-in microphone (or a USB microphone if needed). A remote's Bluetooth mic is often reserved for Google/Alexa and is not accessible to this app.

## Step 3: Install the HACS integration

See the dedicated page: [Home Assistant HACS Setup](Home-Assistant-HACS-Setup.md).

After pairing, enable **Settings → Home Assistant layout** on the TV so Home Assistant can control overlay position, size, and style.

## Step 4: Enable TV announcements & cameras (recommended)

1. On the TV: **Settings → TV announcements & cameras → ON**.
2. Grant **Display over other apps** permission when requested.
3. Use the on-TV **Preview announcement** to test a floating popup.

This enables announcements, camera popups, timers, and the weather card. The alert receiver is separate from the main voice connection.

## Next steps

- Test a simple announcement from Home Assistant (see [YAML Cookbook](YAML-Cookbook.md)).
- If popups stop after standby or a Home Assistant restart, follow the exact recovery steps in [Troubleshooting](Troubleshooting.md#resume-tv-alerts-exact-steps).
- Explore cameras, timers, and weather on their dedicated pages.

**Full detailed checklist:** the repository's [docs/INSTALL.md](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/INSTALL.md).

---

[← All documentation](Home.md) · [Next: Home Assistant HACS Setup →](Home-Assistant-HACS-Setup.md)
