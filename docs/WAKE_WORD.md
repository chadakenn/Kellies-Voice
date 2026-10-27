# On-device wake word

**Draft, unreleased feature (#59).** Public stable is v0.16.12. This branch now includes current `dev`, including the microphone-off switch, reconnect work, and the current settings menu. Do not install a branch APK as a normal update or merge into `main`. It stays a draft until a real television records a full command after Okay Nabu.

## What it does

Kellie's Voice can listen on the TV for **Okay Nabu** using microWakeWord, the same small on-device model the Home Assistant Android app uses. The model runs on the TV. No audio is sent to Home Assistant until the phrase is recognized and the normal voice overlay opens. The overlay then uses the existing Assist connection.

The switch is **off** by default.

- Settings → **Wake word** → **Turn wake word ON** starts it.
- The same menu turns it off and releases the microphone.
- A reboot, an app update, or the TV-alert receiver does **not** start it.
- Android's microphone privacy indicator stays on the whole time the listener is armed.
- The listener lets go of the microphone before the voice overlay records.
- A recognized **Okay Nabu** is itself an explicit request to begin listening once. The normal voice overlay starts Assist capture **even when the separate Auto-listen setting is OFF**, and stops on detected speech silence. It does **not** toggle Auto-listen persistently.
- The one-shot request expires if Home Assistant cannot reconnect promptly (about 10 seconds). STOP, CLOSE, permission loss or connection timeout must not unexpectedly start the microphone later.

## What it does not do

- It does not use the low-power chip that hears "Hey Google". Detection runs on the TV's main processor.
- It does not add "Hey Kellie", "Hey Jarvis", or "Hey Mycroft".
- It does not change Home Assistant. There is no new service and no new entity.
- It does not require saying the assistant's name in ordinary mic-button commands. The wake phrase is only needed when you **opt in to hands-free wake listening**.
- It has not been tested on an onn 4K Pro or any other television. A quiet room is the first check. The same phrase with the TV playing is the second, because the microphone hears the speakers.

The capture rate is 16 kHz. If this TV cannot open a 16 kHz microphone, the listener turns itself back off and leaves a notification. It does not guess another sample rate.

## Privacy and outstanding blockers

- **The wake listener is always recording locally while it is enabled.** The wake model runs on-device, but once the wake phrase triggers, the normal HA Assist pipeline may send following command audio to **cloud STT** if that is the pipeline you configured. A false positive can therefore transmit unexpected room audio. Check **Settings → Voice assistants** in HA and choose local STT if privacy is important.
- The wake listener checks **Mic input detection → Disable microphone capture**. Turning that on, forgetting the Home Assistant login, or losing microphone or overlay permission turns the listener off.
- Full-screen TALK, the voice overlay, and microphone diagnostics do not take the microphone lease while the wake listener's `AudioRecord` is still open. The wake thread releases that recorder, then the waiting owner holds the lease. The overlay drops its lease only after it has asked Assist to shut down. This is still not one shared `AudioRecord` object. A television test still has to show that two captures do not overlap.
- The handoff still starts the overlay with `startForegroundService` from the wake service. Android 12 and 14 may block that or open the overlay without audio. A signed dev APK still has to show a nonempty transcript after Okay Nabu.
- The wake recorder stops before the overlay opens, then the overlay connects to Home Assistant and opens its own recorder. The first words of the command can be lost. A short pre-roll, a warm connection, and a ready chime are still required before calling this hands-free.
- This service is `START_NOT_STICKY`. After a reboot or a kill, Settings can still say ON while nothing is listening. It does not start from boot. A live status and an explicit resume action are still missing.
- An ordinary debug TV APK must not depend on the HWASan runtime. HWASan is disabled for the draft branch's sideloadable Debug configuration.

### Hardware acceptance before promotion

1. Check detection and **nonempty full spoken command transcript** on Android 12 and Android 14+ (or the Android versions available for testing).
2. Confirm full-screen TALK, overlay TALK and microphone diagnostics cannot run a second AudioRecord concurrently.
3. Turn off the global TV microphone switch, forget the HA login, revoke RECORD_AUDIO / overlay permissions while active, and verify the wake listener exits and drops the mic indicator.
4. Test failed/cancelled handoff, slow HA authentication, missing STT/TTS, bad token, empty transcript, and negative AudioRecord reads.
5. With TV audio playing, record missed/false wake counts across extended use; never present a false activation as harmless if cloud STT is configured.
6. Install the sideloaded debug build to verify JNI loads on the real TV and then test restart/kill/reboot behavior and visible failure notifications.
7. Keep off by default, never enable remotely, and require explicit TV-side consent. Include an accessible disclosure and an easy OFF control.


## Engine

The native code under `app/src/main/cpp` is the Home Assistant companion `microwakeword` module (Apache-2.0). CMake downloads TensorFlow Lite Micro when an APK is built. The pull-request workflow compiles that engine into the debug APK. The signed release workflow compiles it into the release APK. Passing `-PskipWakeNative=true` skips the native compile for a host unit-test run.
