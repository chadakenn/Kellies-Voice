# FAQ

[← Documentation home](Home.md) · [Getting started](Getting-Started.md) · [YAML Cookbook](YAML-Cookbook.md) · [Troubleshooting](Troubleshooting.md)

---

**Do I need to install or name an assistant "Julia"?**  
No. Julia is only the maintainer's personal name for their own Home Assistant voice assistant. Use your existing Assist pipeline. You do not need to say "Julia" to use Kellie's Voice.

**Does the Google Assistant button on the remote work with Kellie's Voice?**  
No. It usually still launches Google. Use the on-screen **TALK** button or enable the optional **Okay Nabu** local wake detector under **SETTINGS → Voice & microphone → Wake word**. Both require an app-accessible microphone (some TVs need USB).

**The screen says “connected” and “Nothing yet.” Is Home Assistant broken?**  
Not necessarily. The voice connection may be authenticated even when no speech has been captured. Select TALK and test a short phrase. If no transcript appears, see [microphone troubleshooting](Troubleshooting.md#connected-but-no-speech-is-recognized).

**Should I select “Try unverified microphone” or “Disable microphone capture”?**  
Run Hardware microphone diagnostics first. **Disable** deliberately prevents recording. The **unverified** override is for troubleshooting a real microphone that Android reports incorrectly, not a workaround for hardware the app cannot access. Keep Safe checks ON for normal use.

**Where did the old long SETTINGS list go?**  
Since v0.16.14, SETTINGS opens six categories. Choose **Alerts & cameras** for TV alert status/Resume and **Updates & account** for updater controls. Use D-pad Up/Down/OK/Back. See the [TV settings map](TV-App-and-Permissions.md#settings-menu-map).

**Can I use spoken timers with no extra setup?**  
No. Direct `kellies_voice.start_timer` actions work from Home Assistant. Ordinary phrases like "set a five-minute timer" often hit Home Assistant's native timer intent, which replies that the device does not support timers. Optional custom sentences or automations are required for voice routing and must be configured carefully.

**Why do camera or announcement popups stop after the TV goes to sleep?**  
The alert receiver is separate from the main voice connection and Android may block background starts. Use the exact **SETTINGS → Alerts & cameras → TV alert status → Resume TV alerts** path. See [Troubleshooting](Troubleshooting.md#resume-tv-alerts-exact-steps).

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


**Does Okay Nabu run from power-on without opening the app?**  
Not reliably. On Android 14+, background microphone foreground services cannot start directly from the boot receiver. An opted-in ON preference is remembered, and opening Kellie's Voice can resume the listener. **OFF** and the manual microphone-disable control are respected. See [issue #163](https://github.com/chadakenn/Kellies-Voice/issues/163).

**Where can I see actual screenshots and videos?**  
The [real-device gallery](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/screenshots/README.md) includes privacy-redacted camera screenshots, a live-doorbell GIF and the Okay Nabu demonstration video. Older single-list Settings photos are historical and do not show the new six-category menu.
