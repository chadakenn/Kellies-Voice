# Voice timers in public v0.16.7 (plus optional voice setup)

**Name clarification:** Julia is the maintainer's nickname for their Home Assistant conversation assistant, **not** an included assistant or wake word. Other users can use any Assist conversation agent and do **not** need to say "Julia" to start timers once the voice input is active. Timer scripts and sentence routing must be configured for your own Home Assistant pipeline; see the [naming explanation](../README.md#kellies-voice-vs-julia-whats-the-difference).

Named TV timers show small countdown pills over the current app. At completion: one chime, a 180 ms gap, a second chime, the optional configured phrase, then a 500 ms pause before the next pair. **Alerts repeat until dismissed, with no automatic cutoff.** Multiple completed timers share one audio sequence; dismiss the current timer to hear the next. Back dismisses every ringing timer, leaving running timers alone. The completion window has D-pad buttons; countdowns do not take remote focus.

The bundled chime plays locally. Optional speech uses the TV's Android text-to-speech engine, not the microphone or HA voice agent; it may sound different from your Home Assistant voice assistant. If speech is unavailable, chimes continue. A stalled utterance is stopped after 15 seconds so the pair sequence can resume. The phrase is configured per timer and limited to 140 characters.

**Shipped:** signed Android v0.16.7 and matching HACS integration v0.16.7 support TV countdowns, completion chimes and the Home Assistant timer actions below. The maintainer previously confirmed countdown, recurring chimes and **Back** dismissal on the onn 4K Pro. The previous **remote OK on Dismiss** failure is addressed in [merged PR #55](https://github.com/chadakenn/Kellies-Voice/pull/55), but still needs real-device verification before closing [#54](https://github.com/chadakenn/Kellies-Voice/issues/54). Optional hands-free follow-up listening (#46) and a wake word (#59) are **not shipped**. [New tester installation guide](INSTALL.md).

## Requirements and behavior

- Matching Android and HA integration builds, an opted-in TV event receiver, overlay permission, and one paired TV device ID from Kellie's Voice. The target ID is required; timer commands are never broadcast by default.
- Up to eight timers per TV, with whole-second durations from 1 second to 24 hours. Names use 1–40 ASCII letters/digits/spaces/hyphens/underscores. Names ignore case and repeated spaces. Starting the same name replaces it; duplicate event delivery does not reset it.
- Timers use monotonic elapsed time while the receiver is running. Their deadlines and alert settings are saved locally and restored when the receiver restarts, including expired timers still awaiting dismissal. Turning TV events off explicitly forgets timers and silences alerts.
- The TV must receive the initial command. HA event delivery has no acknowledgement or offline command queue; a spoken confirmation that HA sent a command is not proof the TV received it. Once received, a timer does not require an HA connection to count down or ring. An offline cancel cannot reach the TV; use its Dismiss button or Back.
- This is an awake-TV feature. Android suspension, force-stop, muted alarm volume or firmware restrictions can prevent/delay alerts. No exact-alarm/wake-lock guarantee is implemented. Cameras/weather retain separate windows. Concurrent voice and timer audio, sleep/reboot, and focus interactions require onn testing.

## Home Assistant actions

Start a named timer through **Developer tools → Actions**:

```yaml
action: kellies_voice.start_timer
data:
  device_id: REPLACE_WITH_PAIRED_TV_ID
  name: Pizza
  duration_seconds: 300
  speak_alert: true
  alert_phrase: Your pizza timer is finished.
```

Set `speak_alert: false` for chimes only. `alert_phrase` is optional; the default is the timer name followed by “timer is finished.” Cancel a running timer or dismiss its completion alert:

```yaml
action: kellies_voice.cancel_timer
data:
  device_id: REPLACE_WITH_PAIRED_TV_ID
  name: Pizza
```

Dismiss all ringing timers, preserving running timers:

```yaml
action: kellies_voice.dismiss_timers
data:
  device_id: REPLACE_WITH_PAIRED_TV_ID
```

## Voice setup

Use Home Assistant's documented [custom sentence files](https://www.home-assistant.io/voice_control/custom_sentences_yaml/) and [intent scripts](https://www.home-assistant.io/integrations/intent_script/). The particular custom-sentence and intent-script YAML below is an **unverified alternative**. The maintainer has verified voice timer start and stop through a separate set of Home Assistant conversation-trigger automations, not via this exact configuration. An LLM agent may need local command handling enabled; verify exact matching before relying on it. Unprefixed commands are preferable when listening has already been manually activated, but can conflict with Home Assistant's native timer intents or other Assist satellites. Scope them carefully before enabling in a multi-device home.

Save this as `/config/custom_sentences/en/kellie_timers.yaml`:

```yaml
language: en
intents:
  KellieStartTimer:
    data:
      - sentences:
          - "set [a] {timer_duration} {timer_unit} timer [called {timer_name}]"
          - "set [a] {timer_name} timer for {timer_duration} {timer_unit}"
  KellieCancelTimer:
    data:
      - sentences:
          - "(cancel|stop|dismiss) [the] {timer_name} timer"
          - "cancel [the] timer"
  KellieDismissTimers:
    data:
      - sentences:
          - "dismiss [the] timer alerts"
lists:
  timer_duration:
    range:
      from: 1
      to: 1440
  timer_unit:
    values:
      - in: "second[s]"
        out: 1
      - in: "minute[s]"
        out: 60
      - in: "hour[s]"
        out: 3600
  timer_name:
    wildcard: true
```

Merge the following under the existing `intent_script:` key in `/config/configuration.yaml` (do not create a second key). Replace the TV ID in all three actions. Change `speak_alert` and `alert_phrase` in the start intent for your preferred default. Validate configuration and restart HA; install matching Android/integration builds only after approval.

```yaml
intent_script:
  KellieStartTimer:
    description: Start a Kellie TV timer. timer_duration is an integer; timer_unit is seconds per unit; timer_name is optional text.
    action:
      - action: kellies_voice.start_timer
        data:
          device_id: REPLACE_WITH_PAIRED_TV_ID
          name: "{{ timer_name | default('Timer', true) }}"
          duration_seconds: "{{ (timer_duration | int) * (timer_unit | int) }}"
          speak_alert: false
          alert_phrase: "{{ timer_name | default('Timer', true) }} is finished."
    speech:
      text: "Sent the {{ timer_name | default('Timer', true) }} timer to the TV."
  KellieCancelTimer:
    action:
      - action: kellies_voice.cancel_timer
        data:
          device_id: REPLACE_WITH_PAIRED_TV_ID
          name: "{{ timer_name | default('Timer', true) }}"
    speech:
      text: "Sent the cancel command for {{ timer_name | default('Timer', true) }}."
  KellieDismissTimers:
    action:
      - action: kellies_voice.dismiss_timers
        data:
          device_id: REPLACE_WITH_PAIRED_TV_ID
    speech:
      text: "Sent the dismiss command for ringing timers."
```

Examples: “Set a 5 minute timer”, “Set a pizza timer for 5 minutes”, “Cancel the pizza timer”, “Dismiss timer alerts”. If native HA satellite handling intercepts the generic phrase, verify the Assist pipeline and use a **TV-specific exposed script** or carefully scoped sentence trigger. The app does not require saying “Julia” or “Kellie” as an assistant-name prefix. For a more flexible home-specific setup, see the [draft no-wake-word timer automation guide](https://github.com/chadakenn/Kellies-Voice/blob/docs/voice-timers-llm-scripts-v0164/docs/VOICE_TIMERS_PLAIN_SPEECH.md). No app-side parsing of arbitrary assistant answers is used.

## What is tested, and what still needs testing

- [x] On the maintainer's onn 4K Pro: visible timer countdown, repeating chimes and **Back** to dismiss the completed alert.
- [x] Voice start and stop on that TV after configuring custom Home Assistant timer automations.
- [ ] v0.16.7 **OK/Dismiss** fix: press OK without first moving the D-pad, then test multi-timer selection while a video plays. Record results in #54.
- [ ] New tester devices, other Assist pipelines, simultaneous timers, network drops and extended sleep behavior.

## Further onn QA

Alerts stay silent unless overlay permission is granted and the attached completion window is visible with Dismiss controls. Losing permission or the window stops chimes and speech; an undismissed alert can resume when controls return. Temporary audio-focus loss pauses playback while retaining the request for focus gain. Permanent loss or denial retries after two seconds, so later timers are not left muted. These recovery paths have host regression tests; actual Android window/audio behavior still requires the checks below.

- [ ] Start two named timers over YouTube and verify countdowns without remote-focus theft.
- [ ] At expiry, verify exactly two chimes, optional complete phrase, short pause, repeat indefinitely until dismissed.
- [ ] **Fix validation from PR #55 (not yet in public v0.16.6):** When a 10-second timer finishes, press OK immediately **without moving the D-pad**. The Dismiss button should already have visible focus. With multiple completed timers, use Down/OK to dismiss the selected one while keeping the others available; Back must still dismiss all ringing alerts. While the countdown is running, the overlay must never steal TV remote focus.
- [ ] Test D-pad Dismiss, Back, named voice cancellation, replacement while ringing, and multiple expired timers.
- [ ] Verify dismiss stops MediaPlayer/TTS callbacks, restores audio focus, and cannot restart a stale alert.
- [ ] Interrupt a ringing alert with other audio, then verify recovery and a later timer. Revoke overlay permission or hide/remove the completion window during chimes and speech: audio must stop; restore controls and verify safe resumption and Dismiss.
- [ ] Test absent TTS engine/voice, muted alarm volume, HA/network loss, app force-stop, receiver disable, sleep and restart.
- [ ] Confirm timers do not enable microphones, replace cameras/weather, or change release behavior.
