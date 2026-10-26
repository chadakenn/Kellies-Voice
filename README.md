![Kellie's Voice approved logo and branding banner](https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/docs/branding/kellies-voice-approved-banner.webp)

# Kellie's Voice 🎙️📺

**Your Home Assistant voice companion for Android TV and Google TV.** Ask questions, control your home, see responses over what you're watching, and open opt-in announcement and camera overlays.

[![Latest release](https://img.shields.io/github/v/release/chadakenn/Kellies-Voice?label=Latest%20release)](https://github.com/chadakenn/Kellies-Voice/releases/latest)
[![Android APK build](https://github.com/chadakenn/Kellies-Voice/actions/workflows/build-apk.yml/badge.svg)](https://github.com/chadakenn/Kellies-Voice/actions/workflows/build-apk.yml)
[![HACS validation](https://github.com/chadakenn/Kellies-Voice/actions/workflows/hacs-validate.yml/badge.svg)](https://github.com/chadakenn/Kellies-Voice/actions/workflows/hacs-validate.yml)
[![Wiki links](https://github.com/chadakenn/Kellies-Voice/actions/workflows/check-wiki.yml/badge.svg)](https://github.com/chadakenn/Kellies-Voice/actions/workflows/check-wiki.yml)

[**Download the signed TV APK**](https://github.com/chadakenn/Kellies-Voice/releases/latest) · [**Install the HACS integration**](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) · [**Start here**](docs/wiki/Getting-Started.md) · [**Wiki**](docs/wiki/Home.md) · [**Issues & testing**](https://github.com/chadakenn/Kellies-Voice/issues)

> **Two pieces, one experience.** The Android TV app handles microphone input, voice feedback and overlays. The **Home Assistant custom integration** handles TV pairing, settings and actions. Install matching released versions. **Ollama is optional**: use any compatible Home Assistant Assist conversation agent, whether local or cloud-backed. No separate AI service is installed by the TV APK.

## See it on a real TV

These are genuine captures from the maintainer's **onn 4K Pro**, not UI mockups. TV and microphone compatibility varies by device.

<table>
<tr>
<td width="50%" align="center"><a href="docs/screenshots/onn-voice-time.jpg"><img src="docs/screenshots/onn-voice-time.jpg" width="100%" alt="Kellie's Voice answering a question above Google TV"></a><br><b>Voice over the TV</b><br>Ask Home Assistant without leaving what you're watching.</td>
<td width="50%" align="center"><a href="docs/screenshots/onn-live-doorbell.jpg"><img src="docs/screenshots/onn-live-doorbell.jpg" width="100%" alt="Muted live doorbell view appearing above Kellie's Voice"></a><br><b>Live doorbell</b><br>An opt-in, on-demand camera window.</td>
</tr>
<tr>
<td width="50%" align="center"><a href="docs/screenshots/onn-snapshot-popup.jpg"><img src="docs/screenshots/onn-snapshot-popup.jpg" width="100%" alt="Still camera snapshot notification on the TV"></a><br><b>Snapshot alert</b><br>A still security camera image, not live video.</td>
<td width="50%" align="center"><a href="docs/screenshots/onn-voice-doorbell.gif"><img src="docs/screenshots/onn-voice-doorbell.gif" width="100%" alt="Voice request asking to show the doorbell camera"></a><br><b>Ask for a camera</b><br>A voice request to show the doorbell feed.</td>
</tr>
</table>

The animated [voice-to-doorbell example](docs/screenshots/onn-voice-doorbell.gif) and [additional screenshots](docs/screenshots/README.md) show real device tests. Automatic security-camera alerts use **still snapshots**; muted **live** camera overlays are separately available on demand with compatible Home Assistant camera streams.

## What can it do?

| Feature | What to expect |
| --- | --- |
| **Ask Home Assistant** | Use TALK or a configured shortcut. Replies depend on your Assist pipeline, exposed devices and available tools. |
| **Floating voice overlay** | Read and hear replies without switching away from your TV app when Android permits overlays. |
| **TV announcements** | Opt-in, on-screen messages. Optional spoken announcements with adjustable speech gain; text-only is silent by default. |
| **Doorbell and Frigate snapshots** | Automations can send opt-in still camera popups. |
| **Live camera on request** | Experimental muted live HLS window for supported HA cameras, with on-demand dismissal. |
| **Weather and timers** | Seven-day weather and named TV timers via configured Home Assistant scripts/actions. |
| **TV-specific controls** | HA device controls for voice overlays, alert placement, sensitivity, and more. |

**Verified hardware:** Voice replies, snapshot and live doorbell/driveway camera overlays, and several alert/timer behaviors have been tested on the maintainer's **onn 4K Pro**. Optional **follow-up listening** and **spoken announcement volume** were tested with **v0.16.11**; follow-up is **OFF by default**. Other devices, combinations of streaming apps and microphone sources need community tests. This is not a claim of universal Android TV compatibility.

## Install in three steps

1. **TV:** Download the signed `app-release.apk` from the [latest release](https://github.com/chadakenn/Kellies-Voice/releases/latest). Sideload it onto an Android TV/Google TV device using a USB drive, Send Files to TV, a file manager, or ADB (`adb install -r app-release.apk`). Android may require enabling **Install unknown apps** for your installer. For updates, install the same-signer release **over** the old app; don't uninstall or clear its data.
2. **Connect:** Open Kellie's Voice → **SETTINGS → Home Assistant connection**. Enter your Home Assistant URL and a long-lived access token from your HA profile. Grant microphone permission. Press **TALK** and confirm a short phrase appears under **YOU SAID**, then an answer under **HOME ASSISTANT SAYS**. A displayed **Connected** status by itself doesn't prove microphone capture works.
3. **Home Assistant:** In HACS, add `https://github.com/chadakenn/Kellies-Voice` as a **custom Integration repository**, install the **matching released integration**, restart HA, then pair the TV under **Settings → Devices & services**. Enable **TV announcements & cameras** on the TV, and allow Android **Display over other apps** if you want popups.

**Current stable at documentation review (October 26, 2026):** **[v0.16.11](https://github.com/chadakenn/Kellies-Voice/releases/tag/v0.16.11)** for the signed APK and HACS integration. Always check [latest releases](https://github.com/chadakenn/Kellies-Voice/releases/latest) instead of assuming this text is current forever.

[**Full installation guide**](docs/INSTALL.md) · [**HACS pairing details**](docs/wiki/Home-Assistant-HACS-Setup.md) · [**TV settings tour**](docs/wiki/TV-App-and-Permissions.md#settings-menu-map) · [**Troubleshooting**](docs/wiki/Troubleshooting.md)

## Everyday examples

**Show a text announcement** from Home Assistant:

```yaml
action: kellies_voice.announce
data:
  title: Laundry
  text: The washer has finished.
  duration_seconds: 10
```

**Request a still doorbell image**:

```yaml
action: kellies_voice.show_camera
data:
  entity_id: camera.doorbell
  title: Someone is at the door
  duration_seconds: 15
```

Replace example entity IDs with your own. The target TV can be selected through the action's **TV device ID** field; leaving it empty may target all configured TVs. **Don't expose sensitive camera scripts broadly to Assist.** Anyone who can successfully issue voice commands through an authorized pipeline may be able to invoke the scripts you expose. Scope scripts to appropriate entities and TVs.

[**Copyable camera open/close scripts**](docs/VOICE_CAMERA_SCRIPTS.md) · [**YAML Cookbook**](docs/wiki/YAML-Cookbook.md) · [**Weather**](docs/WEATHER_FORECAST.md) · [**Timers**](docs/VOICE_TIMERS.md)

## Compatibility and honest limits

| Capability | Tested/available on public v0.16.11 | Important limitation |
| --- | --- | --- |
| Voice answers and TV audio | Onn 4K Pro field-tested | Other TV microphones, especially some Philips inputs, need testing [#71](https://github.com/chadakenn/Kellies-Voice/issues/71) |
| Still camera alerts | Onn 4K Pro field-tested | Popup receiver is opt-in; Android standby can stop it |
| Muted on-demand live camera | Moving doorbell and driveway video verified on onn 4K Pro | HLS support and protected-TV-app overlays differ by hardware |
| Optional spoken alerts and follow-up listening | Onn 4K Pro tests on v0.16.11 | Follow-up OFF by default; no always-on wake word |
| Named TV timers | Direct HA actions and scripts tested | Natural-language timer routing depends on configured Assist scripts |
| Fire TV / Fire OS | Community testing requested | Not guaranteed; overlays and microphone access may be restricted |

**Google Assistant microphone-button takeover is not built in.** Kellie's Voice and **Julia** are not two required components: Julia is merely the maintainer's personal name for their configured Home Assistant assistant. You can name yours anything and do not need to say a wake word to use TALK.

If camera popups stop after standby or an HA restart, use **TV app → SETTINGS → TV alert status → Resume TV alerts**. This is different from **Connect / reconnect** for voice. See [exact recovery steps](docs/wiki/Troubleshooting.md#resume-tv-alerts-exact-steps) and the [community hardware test page](docs/wiki/Supported-TVs-and-Testing.md).

## Privacy, trust and updates

- Home Assistant credentials are stored using an **Android Keystore-backed encryption key**; Android app-data backup is disabled.
- Prefer **trusted HTTPS/WSS** to your Home Assistant. **HTTP/WS is allowed, but it does not encrypt your token or microphone audio over the network.** Use it only when you accept that risk on a trusted isolated LAN.
- The signed in-app updater checks the allowed GitHub release source, SHA-256 metadata, package identity and signing certificate before handing installation to Android. Public releases include `app-release.apk` and `update.json`.
- Use a dedicated HA user where practical, with only the permissions actually needed, and limit the devices/scripts you expose to Assist. This is not a per-entity permission boundary supplied by the TV app.
- **Never post** your token, private HA URL, TV pairing ID, camera credentials, private camera images, or signing keys in issues or screenshots. Revoke an exposed token in Home Assistant.

[**Security policy**](SECURITY.md) · [**Signing and updater details**](docs/UPDATES.md) · [**Test compatibility**](docs/wiki/Supported-TVs-and-Testing.md)

## Development and what's next

The project is evolving. Priorities include reliability across more TVs, microphone input detection, manual pairing feedback, voice-to-timer routing and Frigate screensaver camera behavior. Some ideas have branches or pull requests; **planned and experimental work is not a released promise**. Follow the [open issues](https://github.com/chadakenn/Kellies-Voice/issues), [release history](CHANGELOG.md) and [release-specific notes](docs/releases/v0.16.11.md).

Want to help? Start with the [contribution guide](CONTRIBUTING.md), [device report template](https://github.com/chadakenn/Kellies-Voice/issues/new/choose), or [wiki](docs/wiki/Home.md). Real-world results from Google TV, Android TV and Fire TV devices are valuable.

_Kellie's Voice is a community project and is not an official Home Assistant or HACS product._
