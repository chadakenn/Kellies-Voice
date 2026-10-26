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

## Device compatibility matrix

This table distinguishes **maintainer-tested results** from **community testing requests**. A device that can launch the APK is not necessarily able to expose a microphone or draw overlays over protected video.

| Device / platform | Voice microphone | Announcements | Still camera | Muted live camera | Status |
| --- | --- | --- | --- | --- | --- |
| **onn 4K Pro (Google TV)** | Field-tested | Field-tested | Field-tested | Moving driveway and doorbell verified | **Primary tested device** |
| **Other Android TV / Google TV boxes** | Varies by Android input access | Testing requested | Testing requested | Testing requested | No general compatibility claim |
| **Philips setups** | Some hardware exposes phantom/echo-reference inputs | Device-dependent | Some camera/decoder tests reported | Needs per-device verification | [Microphone investigation #71](https://github.com/chadakenn/Kellies-Voice/issues/71) |
| **Fire TV / Fire OS** | Unverified | Unverified | Unverified | Unverified | [Community testing #41](https://github.com/chadakenn/Kellies-Voice/issues/41) |

**Important:** The *brand printed on the television frame* may differ from the streaming device running the app. Report the device running the Android/Google TV/Fire TV OS, not just the display brand. Never label a community report as maintainer-confirmed without a documented physical test.

[**Submit a device compatibility report**](https://github.com/chadakenn/Kellies-Voice/issues/new/choose) using the device-test template. We need confirmations and failures alike: sideload, TALK transcription, optional USB mic support, screen overlays, snapshot/live video, and wake/reconnect behavior.

## Other devices

Other Android TV / Google TV / Fire TV models are welcome for community testing but are **not comprehensively verified**. Some Philips models have been used for camera and HDMI-safe decoder testing; microphone access is limited on devices that only expose echo-reference or line-in inputs. USB microphones may be required.

## What is *not* in the public v0.16.11 release

- Always-on wake word
- Automatic always-on follow-up without enabling the TV setting (the shipped opt-in follow-up is OFF by default)
- Automatic spoken named timers on every Assist pipeline without configuration (the optional native timer bridge is OFF by default and still requires device testing)
- Universal Fire TV support
- Spoken/actionable announcement buttons

Draft PRs exist for some of these; they are not part of the public release.

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
