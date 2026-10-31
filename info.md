# Kellie's Voice 🎙️📺

**Make your Android TV part of your Home Assistant experience without replacing what you're watching.**

Kellie's Voice pairs a **native Android TV / Google TV voice app** with this **HACS custom integration**. Ask Home Assistant questions through the TV, show useful answers in a floating overlay, and let automations display unobtrusive text announcements or Frigate camera snapshots.

### What this HACS integration adds
- **Per-TV controls:** move and resize the voice overlay, choose its look, auto-hide and auto-listen behavior.
- **Text announcements:** use `kellies_voice.announce` for laundry reminders, home status and other automations.
- **Camera popups:** use `kellies_voice.show_camera` to show an HA camera snapshot over the TV. Trigger from Frigate person detection for driveway and doorbell alerts.
- **Experimental live view:** use `kellies_voice.show_live_camera` for a muted temporary HLS overlay on request. Moving **driveway and doorbell** HLS video is verified on the onn 4K Pro running Android v0.16.1; other cameras and devices need testing.

**Important:** HACS installs only the Home Assistant integration. You must separately install the [signed Kellie's Voice Android TV APK](https://github.com/chadakenn/Kellies-Voice/releases/latest) and enable **Home Assistant layout** and, for alerts, **TV announcements & cameras** in its settings.

### What's new with v0.17.0

**v0.17.0** It pairs refreshed TV Settings and Home Assistant Quick Actions with improved dashboard login/remote navigation, reliable four-camera screensavers and bundled community themes. Onn 4K Pro hardware QA passed the camera grid, fullscreen playback with sound, themes after reboot and Quick Actions. The standard `kellies_voice` HACS source now contains **owner-approved Home Assistant-first pairing**, including matching eight-digit TV verification and a renewable dedicated non-admin TV login with no manual HA URL/token entry on the TV. The owner consent warns that a non-admin HA user can control all entities. Automatic renewable login currently requires a Home Assistant **HTTPS** origin; local HTTPS needs no Cloud subscription, while HTTP-only auto-login remains unsupported pending [#262](https://github.com/chadakenn/Kellies-Voice/issues/262). The v3 onboarding is an opt-in public preview, with fresh-install and independent security review still tracked in [#214](https://github.com/chadakenn/Kellies-Voice/issues/214); ordinary users should approve only TVs they trust. See the [latest published signed release](https://github.com/chadakenn/Kellies-Voice/releases/latest) for the current stable version.

![Actual onn 4K Pro Compose Settings developer preview, Aurora Glass theme; private data masked](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/integration/v0.17.0-combined-qa/docs/screenshots/compose-aurora-preview.png)

*Development preview screenshot only, not proof that the pictured theme/layout has shipped to stable users.*

**Real ADB photos from an onn 4K Pro, developer build:** [Compose Settings and theme gallery](https://github.com/chadakenn/Kellies-Voice/blob/integration/v0.17.0-combined-qa/docs/screenshots/PR-192-CAPTURES.md). These are privacy-redacted captures of an *older 0.16.14-test preview*, not the final public UI. Existing released screenshots remain in the main README.

### Quick setup
1. Add `https://github.com/chadakenn/Kellies-Voice` as an **Integration** in **HACS → Custom repositories** if HACS doesn't already know this repo.
2. Download the **matching version** of the integration for the [latest published signed Android APK](https://github.com/chadakenn/Kellies-Voice/releases/latest), then restart Home Assistant.
3. For v0.17.0, start **Secure Home Assistant pairing** on the TV, compare the eight digits, press TV remote OK, then approve the dedicated TV login from the Home Assistant owner panel. This needs trusted local or Cloud **HTTPS** for renewable login; otherwise the existing manual connection remains available. See [installation](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/INSTALL.md) and [local HTTPS](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/LOCAL_HTTPS_SETUP.md).
4. Open the TV's HA device page to configure the overlay and use the actions above.

**Screenshots:** Real onn 4K Pro photos are in the [README gallery](https://github.com/chadakenn/Kellies-Voice/blob/main/README.md#see-it-in-action-). They show the voice overlay, a still camera snapshot, and the live doorbell card starting. The brand banner and older illustrated previews are still concept graphics.

[**Full overview and examples**](https://github.com/chadakenn/Kellies-Voice#readme) · [**Detailed integration guide**](https://github.com/chadakenn/Kellies-Voice/blob/main/custom_components/kellies_voice/README.md) · [**Releases**](https://github.com/chadakenn/Kellies-Voice/releases/latest)

> Built and verified primarily on the onn 4K Pro. Other Android TV models and additional camera streams are not yet hardware-tested. This is a community project, not an official Home Assistant or HACS product.

### New in v0.16.2: close cameras on demand

Use `kellies_voice.hide_camera` (optionally targeted to one TV) to dismiss a camera popup immediately, without affecting TV playback or text announcements. Expose an Assist script called **Close camera on TV** to say “Julia, close the camera.” Live-camera actions support 15–180 second durations; voice agents may need a script with an explicit duration, such as **Show doorbell for 30 seconds**, to honor that phrase. Requires both v0.16.2 components.

**Copy-and-paste setup:** [Home Assistant voice camera scripts (close, 30-second doorbell/driveway, pairing and Assist exposure)](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/VOICE_CAMERA_SCRIPTS.md). The TV app and HACS integration are separate updates. Sample script YAML is also in the [project README](https://github.com/chadakenn/Kellies-Voice#copy-and-paste-home-assistant-script-setup).

### v0.16.3: alert layout and self-recovery

New Home Assistant device controls independently choose **TV alert position** (five locations) and **TV alert size** (small, medium, large) for text announcements, camera stills, and live video. Android v0.16.3 also tries to resume previously enabled TV alerts after an app update or reboot, without starting the microphone. When Android blocks background startup, open **TV app → Settings → TV alert status** to diagnose and resume. Reconnect never replays missed events. Both Android and HACS must be v0.16.3 for layout controls. Read the [full v0.16.3 update guide](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/UPDATES.md).

### New in v0.16.14

- Six-category TV settings hub with remote-friendly navigation.
- **Okay Nabu** on-device wake word (OFF by default); Android may require reopening the app after reboot before listening resumes.
- Optional 25%, 50%, or 75% TV media ducking **after** wake detection.
- Existing cameras, four-feed Frigate screensaver, announcements and timers remain available, subject to platform and pipeline compatibility.

[Real screenshots + wake demonstration video](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/screenshots/README.md). Older Settings screenshots show the previous menu.
