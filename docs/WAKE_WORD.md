# On-device wake word

**Draft, unreleased feature (#59).** Public stable is v0.16.12. This branch now includes current `dev`, including the microphone-off switch, reconnect work, and the current settings menu. Do not install a branch APK as a normal update or merge into `main`. One phone video shows a successful command. The rest of the checklist below is still open, so this stays a draft.

## What it does

Kellie's Voice can listen on the TV for **Okay Nabu** using microWakeWord, the same small on-device model the Home Assistant Android app uses. The model runs on the TV. No audio is sent to Home Assistant until the phrase is recognized and the normal voice overlay opens. The overlay then uses the existing Assist connection.

While the user explicitly arms the wake listener, the draft app now also keeps a **pre-authenticated Home Assistant WebSocket** open so the next wake does not have to establish a new connection after "Okay Nabu". This connection transmits authentication/pings, **not microphone audio**. The wake phrase still opens the usual Assist microphone after handoff. When the overlay closes, its WebSocket is shut down and a new idle connection is warmed for the next wake. When wake listening is stopped or the app is killed, the idle socket is closed.

**Hardware so far:** On the onn 4K Pro, "Okay Nabu, turn on office lights" in one breath worked after `9585f62`, with no wait for Connected. Moving the box away from the TV speakers made detection more reliable while the TV was playing. That is one setup, not a finished checklist. Wake OFF, Disable microphone, Forget, STOPPED, reboot, and Hey Google are still open.

## Experimental volume ducking (new draft-only option)

The onn 4K Pro owner's wake-word activation improved when the box was moved away from the TV speakers. Optional **Wake audio ducking** is a separate experiment to reduce competing TV playback **AFTER** Okay Nabu fires, helping Kellie capture the rest of a command. It **cannot help** the detector hear "Okay Nabu" beforehand.

- Open **Settings → Wake word → Wake audio ducking**, then choose **OFF (default), 25%, 50%, or 75% quieter**. Percent is the reduction from the current local media volume, not the final output percentage.
- When an opted-in wake phrase is detected, the app attempts to lower Android's local `STREAM_MUSIC` volume. It stores the previous level and restores it when the overlay closes or a failed wake cleans up. If the TV lands on a nearby volume step, that actual level is what gets restored from. If the user changes the volume manually during the command, that new level takes precedence. A failed restore keeps the recovery record so the next open can try again, instead of ducking the already-lower volume a second time. A pending recovery record can also restore the previous level on the next app launch after a process kill, only when the volume still matches the applied ducked level.
- **Not guaranteed on all TVs:** fixed-volume Android TV/HDMI devices and HDMI-CEC audio routing may not honor the volume change. This is *not* app-specific YouTube volume mixing, and different volume steps mean the audible reduction can be approximate. There is no claim of a guaranteed exact duck percent.
- This option is only for a recognized wake phrase; it does not lower volume for ordinary TALK, background listening, other TV alerts, or TV audio before the wake. A force-stop can leave a previously reduced system volume until the app next opens, so test restoration carefully.
- Check both media volume and the ability to hear the assistant's answer. Wake OFF and ordinary media playback should remain unchanged.

The switch is **off** by default.

- Settings → **Wake word** → **Turn wake word ON** starts it.
- The same menu turns it off and releases the microphone.
- The **ON preference is saved** through reboots and app updates after an explicit enable in this updated build. Android 14+ **does not allow the boot receiver to start a microphone foreground service**; instead, boot/update posts a *tap-to-open* reminder where TV notifications permit. When Kellie's Voice becomes visible, it automatically resumes the opted-in listener without making you toggle it ON again. This is not guaranteed zero-touch listening immediately at the Android TV home screen.
- If an earlier development build had wake ON under the old no-restart promise, turn it ON or Resume once in this new build to explicitly approve the new restart behavior; pre-existing ON preferences are not silently upgraded. The new consent is cleared by Turn OFF, manual microphone disable, forgetting the Home Assistant login, or failure disarming wake.
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
- The listener is `START_NOT_STICKY`. Settings shows **OFF**, **LISTENING**, or **STOPPED**. STOPPED means the saved choice is ON but Android is not currently recording. Reopening the app tries to resume after reboot, using a visible Activity (per Android 14+ microphone policy). A reboot **does not start background recording by itself**. The notification is a reminder, not proof the listener is running. Explicit Turn OFF is always respected.
- Starting the overlay still uses `startForegroundService` from the wake service. The phone video shows that call succeeding once, over the apps screen. That does not prove every Android 12 or 14 build will allow it.
- The native library is linked for 16 KB memory pages. That alignment has not been checked on an Android 15 device.

## First test on an onn 4K Pro

Install the GitHub Actions **debug** APK from this pull request. Its package is `com.example.tvvoiceprobe.debug`, so it sits beside the stable app and does not use the stable login. Grant microphone and display-over-other-apps. Sign in to Home Assistant inside the debug app.

1. Confirm the settings row says **Wake word: OFF**. Say "Okay Nabu" and confirm nothing opens.
2. Turn it on. The row should say **LISTENING**, and the microphone privacy indicator should stay on.
3. Say "Okay Nabu", pause a beat, then "What time is it?" The overlay should open, a short beep should play, and the transcript should contain the question, not only the wake phrase.
4. Say the question immediately after the wake phrase. The owner reported a successful no-pause office-lights command on the onn 4K Pro using the October 27 build, but repeatability still needs checking.
   With TV audio playing, test the same command with wake audio ducking **OFF**, then **50%**, and verify audible reduction after detection and reliable restoration when the overlay closes.
5. Press TALK while it is LISTENING, then run one microphone diagnostic. Only one capture should be live, and the listener should return to LISTENING afterward.
6. Turn the wake word OFF; reboot; confirm it remains OFF and says nothing when you say the phrase. Then explicitly turn it ON and confirm **LISTENING**. Reboot while ON; confirm the saved ON choice persists and the boot reminder (if notifications are available) opens Kellie's Voice; the listener should automatically resume **after that foreground launch**. Verify the phrase now opens the overlay. Do not expect microphone recording to start directly from BOOT_COMPLETED on Android 14+.
8. With the wake word ON, force-stop the debug app. It is not running while force-stopped; open the app again and check whether Android allows foreground restart.
7. Turn **Disable microphone capture** on and confirm the listener stops. Turn the wake word off when you are done.

A successful wake is a nonempty transcript of the question plus a spoken answer. An overlay with an empty transcript, or a transcript of silence, is a failed capture. Do not treat a green unit test as that result.

## Engine

The native code under `app/src/main/cpp` is the Home Assistant companion `microwakeword` module (Apache-2.0). CMake downloads TensorFlow Lite Micro when an APK is built. The pull-request workflow compiles that engine into the debug APK. The signed release workflow compiles it into the release APK. Passing `-PskipWakeNative=true` skips the native compile for a host unit-test run.
