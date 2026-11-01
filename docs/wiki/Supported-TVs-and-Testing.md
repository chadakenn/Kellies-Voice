# Supported TVs and Testing

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

## Primary tested device

**onn 4K Pro** (Google TV) is the principal field-tested device. The maintainer has confirmed:

- Basic voice conversation and TTS
- Floating overlay, remote navigation, and YouTube audio behavior
- Text announcements
- Moving live driveway and doorbell camera video (v0.16.1+)
- Seven-day weather card
- Named countdown timers with repeating chimes and Back dismissal
- Optional follow-up listening after spoken responses, physically tested on v0.16.11
- Spoken TV announcements with adjustable voice volume, physically tested on v0.16.11
- Manual **Resume TV alerts** recovery (v0.16.8)
- Four-camera live HLS screensaver, automatic idle and reboot recovery (dev.8 accepted)
- Doorbell full-screen with audible audio and working physical Back/gold focus controls (dev.9/dev.10 accepted)

## Device compatibility matrix

This table distinguishes **maintainer-tested results** from **community testing requests**. A device that can launch the APK is not necessarily able to expose a microphone or draw overlays over protected video.

| Device / platform | Voice microphone | Announcements | Still camera | Muted live camera | Status |
| --- | --- | --- | --- | --- | --- |
| **onn 4K Pro (Google TV)** | Field-tested | Field-tested | Field-tested | Moving driveway and doorbell verified | **Primary tested device** |
| **Other Android TV / Google TV boxes** | Varies by Android input access | Testing requested | Testing requested | Testing requested | No general compatibility claim |
| **Philips 55OLED856/12 (OLED) and 65PUS8507/12 (LED)** | Some Philips hardware exposes phantom/echo-reference inputs; microphone not verified by this report | Home Assistant dashboards and camera popups verified by community tester on both | Camera popups verified over TiviMate and HDMI | Live camera popup mode reported working; individual stream/decoder coverage not exhaustive | [Community report #261](https://github.com/chadakenn/Kellies-Voice/issues/261#issuecomment-6562897320) · [OLED screensaver caution](Cameras-and-Frigate.md#philips-oled-factory-screensaver-covering-the-dashboard-community-report-november-2026) · [Microphone issue #71](https://github.com/chadakenn/Kellies-Voice/issues/71) |
| **Fire TV / Fire OS** | Unverified | Unverified | Unverified | Unverified | [Community testing #41](https://github.com/chadakenn/Kellies-Voice/issues/41) |

**Important:** The *brand printed on the television frame* may differ from the streaming device running the app. Report the device running the Android/Google TV/Fire TV OS, not just the display brand. Never label a community report as maintainer-confirmed without a documented physical test.

[**Submit a device compatibility report**](https://github.com/chadakenn/Kellies-Voice/issues/new/choose) using the device-test template. We need confirmations and failures alike: sideload, TALK transcription, optional USB mic support, screen overlays, snapshot/live video, and wake/reconnect behavior.

## Other devices

Other Android TV / Google TV / Fire TV models are welcome for community testing but are **not comprehensively verified**. Some Philips models have been used for camera and HDMI-safe decoder testing; microphone access is limited on devices that only expose echo-reference or line-in inputs. USB microphones may be required.

## What is *not* verified on every TV in v0.17.0

- Guaranteed zero-touch wake recording immediately after power-on (Okay Nabu is shipped as **opt-in**, but may require a foreground app launch after reboot on Android 14+)
- Automatic always-on follow-up without enabling the TV setting (the shipped opt-in follow-up is OFF by default)
- Automatic spoken named timers on every Assist pipeline without configuration (the optional native timer bridge is OFF by default and still requires device testing)
- Universal Fire TV support
- Spoken/actionable announcement buttons

The shipped app includes opt-in Okay Nabu and the categorized Settings hub; other hardware and advanced features still need validation.

## Reporting results

Open a GitHub issue with:

- TV brand and model
- Android/Google/Fire TV OS version
- Kellie's Voice APK version and HACS integration version
- Home Assistant version
- Reproducible steps and redacted screenshots (never post tokens, camera credentials, or private IPs)

## Next

- [Getting Started](Getting-Started.md)
- [Troubleshooting](Troubleshooting.md)
- [FAQ](FAQ.md)

---

[← All documentation](Home.md) · [Next: FAQ →](FAQ.md)


**Okay Nabu on onn 4K Pro:** Maintainer verified a one-breath command and post-wake YouTube ducking/volume restoration on a development build, but final signed-release reboot, permission-loss and cross-device acceptance are still outstanding. Do not infer a TV's remote mic works merely because the hardware has Google Assistant.
