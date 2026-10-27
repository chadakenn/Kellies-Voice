# Floating overlay field log (v0.4)

> **Current development build (v0.13+):** download artifact **`kellies-voice-signed`** from the latest successful [Build Kellie's Voice Signed APK](../.github/workflows/build-apk.yml) run and install `app-release.apk` **over** the existing signed app. Do not look for `julia-voice-signed`, and do not uninstall. The notes below are the **v0.4** onn session, from when the in-app name was briefly Julia Voice. They are not the current test plan.

> **First hardware milestone reached (2026-10-20):** onn 4K Pro showed the floating panel above **Google TV Home** and displayed “Connected and authenticated. Select TALK.” The panel's microphone capture, speaker TTS, CLOSE/BACK behavior and appearance over playing video remain **untested**. This is NOT yet Frigate PiP, a persistent satellite, or an always-on wake-word assistant.

## What's new

- An opt-in, focusable `TYPE_APPLICATION_OVERLAY` panel with **TALK, STOP, CLOSE**, HA status, transcript and spoken TTS replies.
- While the panel is visible, the Android service runs as a **microphone-type foreground service** with an ongoing notification. **Microphone recording begins only when TALK is selected**, and stops on STOP, server STT/VAD ending, or when you dismiss the overlay.
- The overlay obtains its **encrypted Home Assistant settings** from the app's existing Android Keystore store. No second token setup should be necessary after installing a *signed* update over another signed version.
- Two ways to start it:
  1. Open the regular **Julia Voice** → **SETTINGS → Open floating voice overlay (experimental)**.
  2. After permission setup, launch the **Julia Voice Overlay** shortcut shown as a separate TV app entry. If Google TV offers an installed-app action for the programmable **⭐ button**, this shortcut is the one to choose.
- CLOSE or Back dismisses the window and stops the service, including its mic capture and connection. This is opt-in, not auto-started at boot.

## Install

Install the latest **successful** [signed GitHub Actions build](../.github/workflows/build-apk.yml), artifact `kellies-voice-signed`, ZIP containing `app-release.apk`.

The checklist under this heading was written for **`versionCode` 4 / `0.4-overlay-prototype`**. Later signed builds use a higher `versionCode` and the same release key, so they still install over that APK without retyping the Home Assistant token. If Android reports a signature conflict, stop. Do not clear app data.

## Verified so far

- ✅ Screenshot confirms a visible translucent **Julia Voice** panel at the upper-right of Google TV Home.
- ✅ Overlay displays **Connected and authenticated. Select TALK.**
- ✅ TALK, STOP and CLOSE buttons are visible; TALK appears highlighted, but button activation is **not yet verified**.
- ⏳ Test audio capture, STT, reply and TTS inside the overlay; D-pad and BACK exit, then overlay over YouTube or another ordinary video app.

## First test (avoid disrupting movies)

1. Launch the updated **Julia Voice**; check the normal foreground voice conversation still works.
2. Select **SETTINGS → Open floating voice overlay (experimental)**. Grant Android's **Display over other apps** permission if it offers that setting. Some TV firmware may not expose it.
3. Reopen the app and select the same overlay action after granting permission. If the microphone runtime permission prompt appears, approve it.
4. The app should return to the previous task, and a small translucent panel should appear near the **top-right**. Depending on the TV task manager, you may be looking at the TV launcher rather than a previously playing app. That is a firmware limitation to test, not a guarantee of playback preservation.
5. Confirm remote D-pad **LEFT/RIGHT** switches TALK / STOP / CLOSE, **OK** selects, and **BACK** closes.
6. Press **TALK**, speak toward the **onn 4K Pro box** (not the Google remote mic), then **STOP**. Check the transcript and answer and whether spoken TTS works.
7. Select **CLOSE**. Confirm the overlay disappears, its ongoing notification is removed, and TV controls return to the underlying app.
8. Optionally test again while YouTube or a non-DRM app is already open. DRM-protected apps may suppress overlays; don't assume Netflix, Nuvio, Stremio, etc. behave alike.

## ⭐ button quick-launch experiment

**The onn 4K Pro has a native customizable star button that can launch a favorite installed app.** Its exact choices vary by Google TV firmware. The native star-button feature is our first choice, before installing any remapper.

1. On your onn remote, **press and hold ⭐ for about 1–2 seconds**. If it opens the shortcut picker, choose **Open favorite app**.
2. Look for **Julia Voice Overlay** in the installed apps list. Select it and complete the **Create shortcut** confirmation. **Do not pick “Julia Voice”** if the separate overlay entry is available.
3. If a long press doesn't open the shortcut picker because ⭐ is already configured for TV input switching, try **Google TV Settings → Remotes & Accessories → Set up remote buttons → Customizable button**. (Menu wording may vary.)
4. Once configured, launch **YouTube** or another harmless non-DRM app, start a video, and **briefly press ⭐**. Ideally the quick-launch activity starts the already-authorized foreground overlay, then returns to the video.
5. Use the overlay's TALK / STOP to ask a question and CLOSE or BACK to dismiss. Confirm whether video remains visible, whether it pauses, and whether playback is interrupted or ducked.

**Important implementation caveat:** although our APK declares a separate `Julia Voice Overlay` launcher activity, Google's favorite-app picker might collapse launcher entries to their **parent Android package**. If **Julia Voice Overlay** is missing, or ⭐ always launches the full-screen Julia Voice, send us a photo of the app picker instead of installing more tools. We may need a separate-package launcher, an activity shortcut, or a vetted third-party button mapper. The working voice app and its token remain intact.

The microphone button still invokes Google Assistant. **This does not remap the mic button or grant access to the Bluetooth remote mic.**

Sources for native ⭐ behavior: [onn 4K Pro review and star button long press](https://www.digitaltrends.com/home-theater/onn-4k-pro-streaming-device-review-google-tv/), [onn hardware review on app shortcuts](https://www.androidauthority.com/onn-4k-pro-review-vs-3445267/).

## Known constraints

- Android TV firmware may deny overlays, may block focus/key input, may bring the launcher forward, or may suppress windows over DRM video. This is precisely what we need to test on real hardware.
- The foreground-service notification exists only while overlay is active. Do not start service from the background or when a microphone permission is missing. Android 14+ imposes foreground service microphone rules.
- The overlay **does not** run continuously in the background and **does not** listen for wake words. It intentionally closes when dismissed.
- Audio focus/ducking, camera PiP, automation announcements, a dedicated HA media player entity and secure phone pairing are separate future milestones.
- Use **trusted HTTPS** to Home Assistant where possible. Existing warned local HTTP transmits token and audio unencrypted.

## Troubleshooting

If nothing appears, return to the main app and check **Settings → Display over other apps** permission, and whether an onn/Google TV permission screen is supported. If the TV returns home rather than the prior app, test launching the **Julia Voice Overlay** shortcut from another app. If voice transcription fails, verify whether the original full-screen TALK / STOP continues to work.

For bug reports provide firmware/OS version, whether overlay permission existed, whether the panel appeared over a **normal non-DRM app**, D-pad behavior, and any status error **without posting HA tokens or signing secrets**.


## Optional spoken announcements with audio ducking (onn 4K Pro checklist)

1. Enable Settings → Spoken announcements.
2. Play YouTube or a streaming app with volume up.
3. Trigger a Home Assistant announce with `speak: "The washer is finished."`
4. Confirm popup appears, speech is heard, YouTube audio ducks (or lowers), then restores after speech ends.
5. Dismiss the popup mid-speech: audio restores immediately.
6. Fire two announcements quickly: only the latest speaks; focus is not left ducked.
7. Turn off Wi-Fi during speech, reconnect: audio should not stay ducked.
8. Confirm normal voice responses, timers, and camera popups still work and do not leave audio ducked.
9. Disable Spoken announcements: text popups continue, no speech.
10. Confirm no permanent system volume change after any of the above.
