# On-device wake word

**Draft, unreleased feature (#59).** Public stable is v0.16.12. This branch now includes current `dev`, including the microphone-off switch, reconnect work, and the current settings menu. Do not install a branch APK as a normal update or merge into `main`. One phone video shows a successful command. The rest of the checklist below is still open, so this stays a draft.

## What it does

Kellie's Voice can listen on the TV for **Okay Nabu** using microWakeWord, the same small on-device model the Home Assistant Android app uses. The model runs on the TV. No audio is sent to Home Assistant until the phrase is recognized and the normal voice overlay opens. The overlay then uses the existing Assist connection.

While the user explicitly arms the wake listener, the draft app now also keeps a **pre-authenticated Home Assistant WebSocket** open so the next wake does not have to establish a new connection after "Okay Nabu". This connection transmits authentication/pings, **not microphone audio**. The wake phrase still opens the usual Assist microphone after handoff. When the overlay closes, its WebSocket is shut down and a new idle connection is warmed for the next wake. When wake listening is stopped or the app is killed, the idle socket is closed.

**Hardware validation is still required:** preconnection may be unavailable just after opting in, after Wi-Fi changes, or if Android suspends the listener. The onn clip still needed a pause before the command. This draft keeps recording the question until Assist claims it, for at most eight seconds after detection, so that pause should not be required. A television still has to prove the full sentence "Okay Nabu, turn on office lights" with no pause.

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
- It does not replace "Hey Google". One phone video, [wake-okay-nabu-office-lights.mp4](screenshots/wake-okay-nabu-office-lights.mp4), shows the overlay opening over the Google TV apps screen and Home Assistant turning on the office lights. The transcript was "Turn on office lights." The television set in that clip is a Philips. The Android version, a command spoken immediately after the wake phrase, TV-speaker audio, and reboot behavior are still unchecked.

The capture rate is 16 kHz. If this TV cannot open a 16 kHz microphone, the listener turns itself back off and leaves a notification. It does not guess another sample rate.

## Privacy and what is sent

- **The wake listener records locally while it is LISTENING.** That audio is scored on the TV. It is not uploaded.
- After the local detector fires, the same recorder keeps the question until Assist claims it, at most **eight seconds** of 16 kHz PCM. That clip can include the tail of "Nabu" and the start of the question. Assist sends that clip, then the rest of the question, to whatever speech-to-text your pipeline uses. Audio from before the detector fires is discarded. If the overlay never starts, the clip is discarded too.
- A short beep plays when the overlay microphone is actually recording. The TV microphone can hear that beep.
- Cloud speech-to-text, if that is your pipeline, receives the question audio. A false wake can therefore send room audio. Prefer a local speech-to-text engine if that matters.
- **Disable microphone capture**, forgetting the Home Assistant login, or losing microphone or overlay permission turns the listener off.
- Full-screen TALK and diagnostics ask for the microphone without freezing the screen. They do not open a recorder until the wake recorder has been released. Closing the screen cancels a handoff that has not started. The overlay drops its lease after Assist shutdown.
- Hey Google is a separate listener. This app cannot turn it off. Both can contend for the microphone. The remote microphone button still belongs to Google Assistant unless you change the default assistant yourself.
- The listener is `START_NOT_STICKY`. Settings shows **OFF**, **LISTENING**, or **STOPPED**. STOPPED means the switch was on but Android ended the service. **Resume wake word listener** starts it again. A reboot does not.
- Starting the overlay still uses `startForegroundService` from the wake service. The phone video shows that call succeeding once, over the apps screen. That does not prove every Android 12 or 14 build will allow it.
- The native library is linked for 16 KB memory pages. That alignment has not been checked on an Android 15 device.

## First test on an onn 4K Pro

Install the GitHub Actions **debug** APK from this pull request. Its package is `com.example.tvvoiceprobe.debug`, so it sits beside the stable app and does not use the stable login. Grant microphone and display-over-other-apps. Sign in to Home Assistant inside the debug app.

1. Confirm the settings row says **Wake word: OFF**. Say "Okay Nabu" and confirm nothing opens.
2. Turn it on. The row should say **LISTENING**, and the microphone privacy indicator should stay on.
3. Say "Okay Nabu", pause a beat, then "What time is it?" The overlay should open, a short beep should play, and the transcript should contain the question, not only the wake phrase.
4. Say the question immediately after the wake phrase. Note whether the first words are missing.
5. Press TALK while it is LISTENING, then run one microphone diagnostic. Only one capture should be live, and the listener should return to LISTENING afterward.
6. Force-stop the debug app. The row should say **STOPPED**. Resume it from the menu. Reboot the TV and confirm it stays off.
7. Turn **Disable microphone capture** on and confirm the listener stops. Turn the wake word off when you are done.

A successful wake is a nonempty transcript of the question plus a spoken answer. An overlay with an empty transcript, or a transcript of silence, is a failed capture. Do not treat a green unit test as that result.

## Engine

The native code under `app/src/main/cpp` is the Home Assistant companion `microwakeword` module (Apache-2.0). CMake downloads TensorFlow Lite Micro when an APK is built. The pull-request workflow compiles that engine into the debug APK. The signed release workflow compiles it into the release APK. Passing `-PskipWakeNative=true` skips the native compile for a host unit-test run.
