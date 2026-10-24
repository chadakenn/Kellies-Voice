# Supported TVs and Testing

## Primary tested device

**onn 4K Pro** (Google TV) is the principal field-tested device. The maintainer has confirmed:

- Basic voice conversation and TTS
- Floating overlay, remote navigation, and YouTube audio behavior
- Text announcements
- Moving live driveway and doorbell camera video (v0.16.1+)
- Seven-day weather card
- Named countdown timers with repeating chimes and Back dismissal
- Manual **Resume TV alerts** recovery (v0.16.8)

## Other devices

Other Android TV / Google TV / Fire TV models are welcome for community testing but are **not comprehensively verified**. Some Philips models have been used for camera and HDMI-safe decoder testing; microphone access is limited on devices that only expose echo-reference or line-in inputs. USB microphones may be required.

## What is *not* in the public v0.16.9 release

- Always-on wake word
- Automatic follow-up listening after the assistant finishes speaking
- Native Home Assistant timer intent interception (spoken timers need extra HA configuration)
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

- [Getting Started](Getting-Started)
- [Troubleshooting](Troubleshooting)
- [FAQ](FAQ)
