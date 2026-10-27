# Choosing an Assist pipeline on Android TV

**Development-only candidate.** No stable release or OTA install is triggered by this feature branch.

Home Assistant may have multiple voice-assistant pipelines with different STT,
conversation (including local LLM), and TTS providers. Until this change,
Kellie's Voice did not send a pipeline ID and therefore always followed
Home Assistant's preferred pipeline.

## On-TV instructions

1. In Home Assistant, create/configure your pipelines under
   **Settings → Voice assistants**. Do not change the HA global preferred
   pipeline unless you want other devices to change as well.
2. On the TV, open **Kellie's Voice → Settings → Assist pipeline**.
3. The TV makes a read-only authenticated WebSocket call
   `assist_pipeline/pipeline/list`, showing each HA pipeline by name and ID.
   Select one, or leave **Use Home Assistant preferred (default)** selected.
4. Press **TALK**. The normal `assist_pipeline/run` request sends the chosen
   pipeline ID only when a non-default pipeline was selected. This also
   affects the existing floating voice overlay, which shares TV settings.
5. To return to normal, select **Use Home Assistant preferred (default)**.
   No HA-wide setting was modified.

The selected ID is stored **on the TV only** and persists across app restarts.
Switching pipelines starts a fresh conversation so a conversation identifier
created under one conversation agent does not leak into another pipeline.

## Compatibility and testing

- This is a **picker of existing pipelines**, not an engine selector. It never
  creates, edits, deletes or sets Home Assistant's preferred pipeline.
- Pipeline listing requires an authenticated HA WebSocket connection. If HA
  denies the list command, the TV keeps the last saved selection and shows an
  error. If a saved pipeline is later deleted in HA, select Preferred or another
  pipeline before talking.
- The existing audio path remains explicit push-to-talk, with the same
  microphone permissions, recorder shutdown, STOP and spoken TTS handling.
  The picker never opens the microphone.
- Verify at least two working HA pipelines: choose Preferred, then a second
  pipeline with a distinguishable agent/TTS. Confirm the TV uses each
  respective pipeline while the HA global preferred pipeline remains
  unchanged. Reboot and test persistence. Test a disconnected HA and a
  missing/removed pipeline without breaking TV reconnect.
- Source can overlap with other unmerged `HaAssistClient` changes in PRs
  #144 and #146. Rebase and rerun Android/HA/HACS tests before any dev merge.

Future independent UX work: text-only Assist prompts via
`assist_pipeline/run` starting at `intent` with `input.text`. This should
not ask for microphone permission, start an AudioRecord, or automatically
enable an always-listening wake word.
