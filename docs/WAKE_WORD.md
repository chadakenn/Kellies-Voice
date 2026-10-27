# On-device wake word

**Draft, unreleased feature (#59).** Public stable is v0.16.12. The PR branch is old and conflicts with newer microphone, settings, and diagnostic code. Do not install a branch APK as a normal update or merge into main until the branch is rebased, security checks are complete, and real-device QA has passed.

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
- As of this draft, **full-screen TALK, microphone diagnostics, and the newer TV microphone-disable switch are not coordinated with the wake listener**. Do not enable wake word on a TV in routine use until those paths share one microphone ownership policy.
- The handoff currently opens a microphone foreground service from the background, which Android 12/14+ may block or leave with silent capture. Test the actual post-wake transcript, not merely whether an overlay appeared.
- Releasing the wake recorder and reconnecting HA may lose the first words of the command. A short audio pre-roll, warm HA connection, and ready chime are future requirements before claiming hands-free reliability.
- This branch is **START_NOT_STICKY**. After a reboot or service kill, the setting may still say ON even when Android is not listening; it never silently restarts from boot. A true status indicator and explicit resume action remain to be implemented.
- Wake disable, lost permission, missing microphone and forgetting the HA login must stop the listener. Invalid negative recorder reads must not busy-loop. The current branch adds initial stop/failure guards, but still needs verified shared microphone-disable behavior after rebasing.
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
