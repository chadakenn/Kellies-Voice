# Opt-in typed Assist requests on Android TV

**Development feature:** `feat/opt-in-typed-assist-input`; not enabled on the current stable APK or automatically published.

## On-TV setup

1. Open **Kellie's Voice → Settings → Type to Kellie: OFF (opt in)**.
2. Read the confirmation that typed requests are sent to Home Assistant and **can control exposed home devices**, the same as spoken Assist commands.
3. Choose **Enable**. A **TYPE** button appears beside TALK, STOP and SETTINGS on the full-screen TV app. The setting is **local to this TV**, stays off for new installs, and does not change other devices.
4. Select **TYPE** with the remote. Use Android TV's on-screen keyboard to enter a request (up to 500 characters), then choose **Send**.
5. The transcript shows what was sent, HA sends the response, and Kellie's Voice plays TTS when the selected Assist pipeline returns audio. No microphone permission prompt or recording takes place during a typed request. Press **STOP** while the request is in progress to cancel the HA pipeline run.
6. To remove the TYPE button, go to **Settings → Type to Kellie: ON**, then disable it. Turning the setting OFF prevents sending even if an input dialog was already open.

## Can the Google TV remote microphone type commands?

The TYPE dialog deliberately opens the **Android TV's currently installed keyboard**
using a standard `EditText`. On an onn Google TV with Google/Gboard as the
default IME, this normally opens that keyboard. **If** the keyboard displays
a mic/dictation affordance **and** accepts the remote microphone, speaking
into the Google keyboard should populate the text field; the user still must
select **Send** before Kellie's Voice sends anything to HA.

This is **not** direct microphone access for Kellie's Voice. Google's keyboard
(or its chosen speech recognizer) does the speech-to-text processing, with
that provider's privacy/network behavior. On some Google TV firmware, the
remote's physical Assistant/mic button launches Google Assistant instead of
dictating into a focused keyboard field. The app cannot guarantee this
functionality or take over the remote's mic using EditText. No background
dictation, no automatic Send and no bypass of the opt-in switch.

**Hardware acceptance:** with TYPE enabled, open the keyboard and try the
remote's microphone button while the text field is focused. Record whether
(a) dictation fills the field, (b) Google Assistant takes over, or (c) nothing
happens. Only label it supported after actual hardware testing. You can still
type with the D-pad even when dictation is unavailable.

## Privacy and behavior

- The typed feature starts OFF. Its enablement is stored in this Android TV's local SharedPreferences, **not** sent through HA layout controls or background services.
- Typed commands are submitted to the TV's saved, authenticated HA Assist socket using `assist_pipeline/run` with `start_stage: "intent"`, `end_stage: "tts"`, and `input: {"text": "..."}`. The standard 60-second Assist timeout applies. Requests can operate HA devices based on that token's permissions and the configured conversation agent.
- A typed run does **not** initialize microphone hardware, require RECORD_AUDIO, start STT or wake-word capture, or enable automatic follow-up listening. The main voice TALK button and floating voice overlay are otherwise unchanged.
- Text is displayed in the existing conversation transcript and transmitted to the configured HA server. There is **no new local text history database**, phone inbox, logging hook, or external account; Android's installed keyboard may handle keystrokes according to its own privacy policy.
- The typed entry uses the existing Android IME, not a custom keyboard or automatically activated microphone. Key events are routed to the IME while the text dialog is open.
- A single typed or spoken Assist run may be active at once. Switching between runs keeps normal HA conversation context. When PR #148's pipeline picker is eventually merged, typed requests should follow the selected TV pipeline too; **that integration requires a separate rebase and test**.

## Real-TV QA before merge

- [ ] On a clean/install-updated TV, TYPE is absent. Settings says **OFF (opt in)**. Reboot; still OFF.
- [ ] Enable through explicit confirmation; TYPE appears and can be focused with D-pad. Cancel confirmation; it remains OFF.
- [ ] Disable and confirm TYPE disappears, is not accidentally selected, and cannot send a pending request.
- [ ] Disable Android microphone permission (or TV's mic switch). Type an innocuous question; verify answer and TTS without a microphone permission prompt. Also test on a device without a usable microphone.
- [ ] Normal TALK still acquires/cleans up microphone and responds as before. No automatic follow-up recording on typed responses.
- [ ] Type a harmless HA intent (e.g. request a sensor state). Confirm it uses HA actions, response and conversation history.
- [ ] Send 500 characters and reject empty, over-limit, and pasted control characters.
- [ ] Test STOP during a slow typed response, then retry another typed and spoken request without a stuck run.
- [ ] Disconnect/restart HA while a typed request is active. Confirm the request ends safely and the client reconnects normally. Verify missing TTS still shows text.
- [ ] Verify TV remote keyboard navigation, back/dismiss, editor focus, and no unseen underlying button clicks.
- [ ] Verify existing Frigate screensaver, live cameras, announcements and overlay remain unchanged.

**Do not publish stable or promote this to main until real-TV acceptance.**
