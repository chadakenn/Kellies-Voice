# PR #183: Energy VAD / fallback silence acceptance

**Scope:** Android voice-capture behavior on a development test APK. These tests do **not** require changing Home Assistant scripts, Ollama/PS5LM, the installed stable app or user credentials. This branch is not a released update.

## Expected logic

Home Assistant's `stt-vad-end` remains the **primary** signal ending a voice turn. The local energy detector acts as a backup if HA never sends that event. Local quiet thresholds are **4 seconds (Quiet), 5 seconds (Normal), 6.5 seconds (TV playback)**, after speech has been detected. These thresholds **do not guarantee** the user can pause for that long: if HA's VAD ends the turn sooner, it still takes precedence.

The detector consumes the 16 kHz PCM already being sent to Assist, including first words buffered by Okay Nabu and follow-up handoff. No new microphone, background service or cloud endpoint is introduced.

## Real onn 4K Pro QA, with TV audio both quiet and playing

Run against the **PR #183 signed-off debug build** (its debug package can coexist with stable), after the Android JNI build and tests succeed. It does not inherit your stable app's HA login, wake settings, or preferences.

- **TALK, Normal room:** Say "Turn on the office lights" naturally. Confirm complete transcript and actual HA action. Then say "What's the weather today ... and tomorrow?" with a natural **2-second pause** mid-sentence. Confirm neither words nor the response are cut off.
- **Long pause with voice continuation:** During TALK, speak a first phrase, pause about **3–4 seconds**, then continue. Record **whether HA stt-vad-end stopped first**. Do not call it a PR regression merely because the server ended earlier; the local backup cannot override HA's VAD. Log reproducible early cutoffs.
- **Quiet and TV-playback modes:** Repeat with the chosen ambient-noise setting, especially while YouTube audio is playing. Watch for endless "LISTENING", premature auto-stop, or undue delay after finishing. Verify music volume ducks and restores as before.
- **Okay Nabu, immediate speech:** Enable the opt-in wake phrase, say "Okay Nabu, turn on the office lights" without a pause. Verify the command's first word survives capture handoff and the action truly occurs; don't accept a spoken claim without observing the device.
- **Okay Nabu, delayed speech:** Say "Okay Nabu", pause briefly, and ask a short question. Confirm the transcript contains the complete question. Repeat at least three times with and without TV audio.
- **Follow-up:** Answer one question, let the configured follow-up microphone activate and ask a second question. Confirm normal timeout, no missed prefix, and the wake listener returns to its expected state.
- **STOP / cancel:** Press STOP while listening. Verify recording ends and never resumes on its own, and no prior buffered lead leaks into a later run.
- **Reconnect / reboot:** Restart HA, recover a Wi-Fi interruption, and reboot the TV. Verify microphone privacy, wake preference, foreground rearm rules and existing camera/TV-alert behavior remain unchanged.

## Record and release gates

For each failed case record app build/commit, Android version, selected noise mode, whether auto-stop was enabled, actual spoken phrase, transcript and approximate pause length. **Never publish recordings of household conversations, credentials, microphone logs containing private audio, pairing IDs or private camera images.**

Automated tests verify pause thresholds and capture-state regressions; they cannot verify live microphone timing, HA pipeline VAD interplay or wake-to-question audio fidelity. Keep #183 **DRAFT** until an actual physical onn test passes. Do not publish a public APK or merge it with Grok's UI work merely for convenience.
