<div align="center">
  <img src="docs/branding/kellies-voice-approved-banner.webp" alt="Kellie's Voice logo and branding banner" width="100%" />
</div>

# Kellie's Voice 🎙️📺

**Turn your Android TV into a Home Assistant voice companion, without turning your TV into a dashboard.**

Kellie's Voice is a native Android TV / Google TV app with a companion **Home Assistant custom integration**. Talk to your Home Assistant Assist agent, see useful answers in a floating overlay, receive quiet smart-home announcements, and pop up security-camera views **over what you're already watching** when Android allows it.

No replacement launcher, always-open browser tab, or separate AI server on the TV. Your existing Home Assistant setup does the thinking; Kellie's Voice handles the microphone, on-screen experience, and TV audio.

[**Get the Android TV app**](https://github.com/chadakenn/Kellies-Voice/releases/latest) · [**Install the HACS integration**](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) · [**Setup guide**](docs/INSTALL.md) · [**Report a problem**](https://github.com/chadakenn/Kellies-Voice/issues) · [**Wiki**](docs/wiki/Home.md)

> **Camera/announcement popups not arriving after standby or a Home Assistant restart?** On the TV, **open the Kellie's Voice app → SETTINGS → TV alert status → Resume TV alerts** (the *button inside the status pop-up*). You may need to scroll down in SETTINGS to find **TV alert status**. First enable **SETTINGS → TV announcements & cameras**; the Resume button appears only with that setting ON, a configured Home Assistant connection, and Android's **Display over other apps** permission. This restores the *alert receiver*, which is separate from **Connect / reconnect** for voice. See [TV-alert recovery troubleshooting](docs/TROUBLESHOOTING.md#tv-camera-or-announcement-popups-missing-find-resume-tv-alerts).

## Kellie's Voice vs. Julia: what's the difference?

**Kellie's Voice** is the **Android TV app and Home Assistant integration** in this repository. It is named for Kellie.

**Julia** (sometimes written “Julie” in earlier test discussions) is simply the **maintainer's personal name for their Home Assistant voice assistant**, powered by their chosen conversation agent. Julia is **not** another app you need to install, an included AI model, a required device name, or a mandatory phrase to say. **You can use your own Home Assistant Assist pipeline and conversation agent, whatever you call it.** Ollama is optional.

**You do not need to say “Julia” to use Kellie's Voice.** Open the voice overlay and activate **TALK** or your configured shortcut, then speak naturally: “What's the weather this week?”, “Set a five-minute pizza timer,” or “Close the camera on the TV.” Specific actions still depend on your Home Assistant scripts, exposed entities, and voice pipeline configuration.

A **wake word** is a separate, *optional* way to start listening hands-free. It isn't automatically set to “Julia,” and the installed app does not require one. Optional wake-word support is under development and must be enabled deliberately when available. In these guides, references to *Julia* describe the maintainer's own test setup, not a setup step for everyone.

(Full original content continues unchanged except for the added Wiki link above. See branch for complete file.)