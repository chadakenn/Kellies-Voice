# Native Assist timers on Kellie's Voice (experimental)

**Status:** Development only. OFF by default. No stable release or automatic
Home Assistant installation. See Issue #57 and the separate Grok draft PR #105.

## How it works

Home Assistant has its own native timer manager. Registering a
`TimerHandler` for the **Home Assistant registry device ID** lets the
native `HassStartTimer` / update / cancel intents call a TV-specific
handler. The TV **pairing UUID** is a different ID: it is used only when
forwarding scoped `kellies_voice_timer` events to the correct Android TV.
Native intent routing does *not* happen through the LLM automatically.

The bridge maps:
- **started** to one Android TV countdown using HA's actual `seconds_left`.
- **updated (time changed)** to replace the same TV countdown with remaining time.
- **updated (paused)** to cancel the TV countdown without deleting the
  native HA timer. **Unpaused** restarts with its then-current remaining time.
- **cancelled** to a TV cancel only if the HA timer still owns an active TV slot.
- **finished** to no TV command; the TV owns its own single completion chime.

Home Assistant timers have unique IDs, but Android timers are keyed by
human-friendly names. To avoid a stale cancel killing the wrong identically
named timer, each *native* timer uses a stable, ASCII-safe display slot:
`HA Pizza 9F042821AB`, where the suffix is a short digest of the HA timer
ID. Users should cancel by the timer's original name via the HA intent;
Android's display name is only for avoiding collisions. Regular TV-local
timers and manual Home Assistant actions are unchanged.

## Opt in only when ready

1. Ensure the HACS integration running on Home Assistant includes this
   development branch. **Do not update a working HA installation without
   a backup, explicit approval, and coordinated restart.**
2. Open **Settings → Devices & Services → Kellie's Voice → Configure**
   for the correct TV. Turn on **Experimental native Assist timers**.
   This triggers a config-entry reload and registers a native timer handler
   for that TV alone. The default stays **OFF** for all existing users.
3. In the TV, enable **Home Assistant layout** so the HA registry device ID
   is synchronized into Assist requests. This is different from the pairing ID.
4. Select an Assist pipeline that *actually uses native local intents*.
   An Ollama/conversation LLM may choose to answer the user itself rather
   than call Home Assistant's timer intents, and could still say
   "timers are not supported." Registration alone cannot override an LLM.
5. On the TV use an explicitly launched TALK session; try
   "Set a five minute pizza timer", "Pause the pizza timer",
   "Resume the pizza timer", and "Cancel the pizza timer".
   Confirm one TV only, no double chime, and no stale cancel.

**Critical limitation:** Home Assistant and the TV still keep separate
clocks. If Android misses a WebSocket event or disconnects, timers may drift
or fail to ring. These are not safety-critical alarms. HA's timer callback
does **not** prove the TV displayed or rang the timer. Do not promise this
feature is production-ready without physical device and reconnect testing.

For Ollama pipelines that do not route to native intents, continue using
the existing, documented, per-TV script/custom-sentence method from
[VOICE_TIMERS.md](VOICE_TIMERS.md). That approach has already passed
backend sentence-trigger checks for the owner's named Coffee timer, but not
every hardware spoken route.

## Testing and rollout

Python regression tests cover remaining duration, pause/resume, changed
time, duplicate names, per-TV targeting, stale cancel, and unload. The
Android timer receiver is not changed by this native integration PR.

**Do not publish to main or install another APK to test this PR alone.**
It belongs in the owner's next consolidated QA batch once other changes
are ready. Never restart Home Assistant automatically to chase this timer test.
