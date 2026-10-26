# FAQ

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

**Do I need to install or name an assistant "Julia"?**  
No. Julia is only the maintainer's personal name for their own Home Assistant voice assistant. Use your existing Assist pipeline. You do not need to say "Julia" to use Kellie's Voice.

**Does the Google Assistant button on the remote work with Kellie's Voice?**  
No. It usually still launches Google. Use the on-screen **TALK** button and speak toward the TV's built-in microphone (or a USB mic if needed).

**The screen says “connected” and “Nothing yet.” Is Home Assistant broken?**  
Not necessarily. The voice connection may be authenticated even when no speech has been captured. Select TALK and test a short phrase. If no transcript appears, see [microphone troubleshooting](Troubleshooting.md#connected-but-no-speech-is-recognized).

**Should I select “Try unverified microphone” or “Disable microphone capture”?**  
Run Hardware microphone diagnostics first. **Disable** deliberately prevents recording. The **unverified** override is for troubleshooting a real microphone that Android reports incorrectly, not a workaround for hardware the app cannot access. Keep Safe checks ON for normal use.

**Why is the bottom of SETTINGS missing?**  
The settings list scrolls. Press **Down** on your remote to reach TV alert status and the update buttons. See the [TV settings map](TV-App-and-Permissions.md#settings-menu-map).

**Can I use spoken timers with no extra setup?**  
No. Direct `kellies_voice.start_timer` actions work from Home Assistant. Ordinary phrases like "set a five-minute timer" often hit Home Assistant's native timer intent, which replies that the device does not support timers. Optional custom sentences or automations are required for voice routing and must be configured carefully.

**Why do camera or announcement popups stop after the TV goes to sleep?**  
The alert receiver is separate from the main voice connection and Android may block background starts. Use the exact **SETTINGS → TV alert status → Resume TV alerts** path. See [Troubleshooting](Troubleshooting.md#resume-tv-alerts-exact-steps).

**Is live camera video guaranteed on every TV?**  
No. Moving video is verified on the onn 4K Pro with specific cameras. Other devices and cameras need testing. On some Philips TVs use the safe software decoder mode.

**Do I need both the APK and the HACS integration?**  
Yes. They are separate. Always install matching versions.

**Where are the YAML examples?**  
[YAML Cookbook](YAML-Cookbook.md).

**Is this an official Home Assistant product?**  
No. It is a community project.

---

[← All documentation](Home.md) · [Next: Getting Started →](Getting-Started.md)
