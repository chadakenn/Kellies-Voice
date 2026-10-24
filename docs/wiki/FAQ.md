# FAQ

**Do I need to install or name an assistant "Julia"?**  
No. Julia is only the maintainer's personal name for their own Home Assistant voice assistant. Use your existing Assist pipeline. You do not need to say "Julia" to use Kellie's Voice.

**Does the Google Assistant button on the remote work with Kellie's Voice?**  
No. It usually still launches Google. Use the on-screen **TALK** button and speak toward the TV's built-in microphone (or a USB mic if needed).

**Can I use spoken timers with no extra setup?**  
No. Direct `kellies_voice.start_timer` actions work from Home Assistant. Ordinary phrases like "set a five-minute timer" often hit Home Assistant's native timer intent, which replies that the device does not support timers. Optional custom sentences or automations are required for voice routing and must be configured carefully.

**Why do camera or announcement popups stop after the TV goes to sleep?**  
The alert receiver is separate from the main voice connection and Android may block background starts. Use the exact **SETTINGS → TV alert status → Resume TV alerts** path. See [Troubleshooting](Troubleshooting#resume-tv-alerts).

**Is live camera video guaranteed on every TV?**  
No. Moving video is verified on the onn 4K Pro with specific cameras. Other devices and cameras need testing. On some Philips TVs use the safe software decoder mode.

**Do I need both the APK and the HACS integration?**  
Yes. They are separate. Always install matching versions.

**Where are the YAML examples?**  
[YAML Cookbook](YAML-Cookbook).

**Is this an official Home Assistant product?**  
No. It is a community project.
