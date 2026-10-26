**Now available: [v0.16.12](https://github.com/chadakenn/Kellies-Voice/releases/tag/v0.16.12)** with opt-in four-live-camera screensaver, selected-camera full-screen sound, remote Back and focus controls verified on onn 4K Pro. [Install the matching TV APK + HACS integration](Getting-Started.md), then see [Voice and Timers](Voice-and-Timers.md). Alternate TVs and optional native timer bridge still require testing.

![Kellie's Voice: Your home, your voice, your way](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/branding/kellies-voice-approved-banner.webp)

# Kellie's Voice | Documentation

**Your TV. Your Home Assistant. One simple conversation.**

Turn an Android TV or Google TV into a voice companion for Home Assistant. Speak to your configured Assist agent, see answers without leaving what you're watching, and optionally show announcements, camera views, timers, and a seven-day weather card.

[**Download the signed TV app**](https://github.com/chadakenn/Kellies-Voice/releases/latest) · [**Install with HACS**](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) · [**Start here**](Getting-Started.md) · [**Report an issue**](https://github.com/chadakenn/Kellies-Voice/issues)

---

## 📺 See Kellie's Voice on a real TV

| Voice answer above Google TV | Muted live doorbell popup |
|:---:|:---:|
| [![Actual Kellie's Voice Assist overlay](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/screenshots/onn-voice-time.jpg)](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/screenshots/onn-voice-time.jpg) | [![Actual Kellie's Voice doorbell popup](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/screenshots/onn-live-doorbell.jpg)](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/screenshots/onn-live-doorbell.jpg) |
| **Ask a question without changing apps** | **Open a supported camera when you want** |

[Still camera snapshot example](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/screenshots/onn-snapshot-popup.jpg) · [TV settings tour](TV-App-and-Permissions.md#settings-menu-map) · [Microphone troubleshooting](Troubleshooting.md#connected-but-no-speech-is-recognized)

_These photos are real onn 4K Pro captures, not design mockups. Camera and microphone behavior varies by device._

## 🚀 New here? Start in three steps

| Step | What you'll do | Guide |
|:---|:---|:---|
| **01 · Install** | Get the signed Android TV APK, set permissions and connect to Home Assistant | [Getting Started](Getting-Started.md) |
| **02 · Pair** | Install the companion HACS integration and pair your TV | [Home Assistant Setup](Home-Assistant-HACS-Setup.md) |
| **03 · Try it** | Test a voice command, announcement or camera popup | [TV App & Permissions](TV-App-and-Permissions.md) · [YAML Cookbook](YAML-Cookbook.md) |

> **Two components, one experience:** the Android TV app and the Home Assistant HACS integration are separate downloads. Use matching released versions. The current public release is **v0.16.12**; experimental wake-word work is not included.

## Explore by what you want to do

| 🗣️ Talk to your home | 📷 See what's happening |
|:---|:---|
| [**Voice & Timers**](Voice-and-Timers.md) <br> Ask questions, control devices, use countdown timers. | [**Cameras & Frigate**](Cameras-and-Frigate.md) <br> See snapshots and up to four muted live screensaver feeds; select one full-screen for available audio. |

| 📣 Get notifications | 🧩 Copy working examples |
|:---|:---|
| [**Announcements & Weather**](Announcements-and-Weather.md) <br> Put household alerts and forecasts on the screen. | [**YAML Cookbook**](YAML-Cookbook.md) <br> Home Assistant actions, scripts and automations. |

## Need help?

| Problem or question | Where to go |
|:---|:---|
| **Camera popups or TV alerts stopped showing** | [Resume TV alerts, step by step](Troubleshooting.md#resume-tv-alerts-exact-steps) |
| **Microphone or remote button doesn't work** | [TV App & Permissions](TV-App-and-Permissions.md) · [FAQ](FAQ.md) |
| **Will it work on my TV?** | [Supported TVs & Testing](Supported-TVs-and-Testing.md) |
| **Want to report a bug or help test?** | [GitHub Issues](https://github.com/chadakenn/Kellies-Voice/issues) |

## Kellie's Voice vs Julia

**Kellie's Voice** is the Android TV app and HACS integration. **Julia** is simply the maintainer's name for their personal Home Assistant conversation agent. You **do not** need to install Julia, call your assistant Julia, or use a wake word. Activate **TALK** and speak normally with your own configured Assist agent.

---

**A note on features:** the Google Assistant remote microphone button is not automatically available to third-party apps. Optional hands-free follow-up listening remains OFF by default. The opt-in HA/Frigate screensaver is included in v0.16.12; its four-camera live mode is advanced and may require stronger hardware. Always-on wake-word support remains an unreleased PR and unverified extra TV models need community testing. See the [FAQ](FAQ.md) for more.

[**Begin installation →**](Getting-Started.md) · [**Back to the main repository**](https://github.com/chadakenn/Kellies-Voice)
