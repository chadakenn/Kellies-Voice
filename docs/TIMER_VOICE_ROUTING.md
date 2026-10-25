# Spoken named TV timers: routing design and safe alternative (Issue #57)

**Status:** Design + pure mapping prototype. Native handler is **not** auto-enabled.
**Branch base:** `dev`. Does not touch spoken-announcement files from PR #104.

## Problem

Home Assistant's built-in `HassStartTimer` / `HassCancelTimer` intents raise
`TimersNotSupportedError` unless the requesting `device_id` is registered with
the core `TimerManager`. Kellie's Voice TVs use a custom event
(`kellies_voice_timer`) and the actions `kellies_voice.start_timer`,
`cancel_timer`, and `dismiss_timers`. They are not native timer satellites, so
phrases such as "set a five-minute pizza timer" fail when spoken on the TV
(often with `device_id=None` or an unregistered ID).

Supplying the registry device ID (PR #50) is necessary but not sufficient.
The device must also register a timer handler.

## Supported Home Assistant architecture

From `homeassistant/components/intent/timers.py` (current core):

- `type TimerHandler = Callable[[TimerEventType, TimerInfo], None]`
- `TimerEventType`: `STARTED`, `UPDATED`, `CANCELLED`, `FINISHED`
- `async_register_timer_handler(hass, device_id, handler) -> unregister`
- `is_timer_device(device_id)` is True only after registration
- Native start/cancel intents succeed only for registered devices (or when a
  `conversation_command` delayed-command slot is used)

ESPHome and Wyoming satellites use this path. A custom integration can too.

## Dual-ownership limitation (why this is not enabled by default)

Kellie's Voice timers are **TV-local**. Once the start event arrives, the
Android receiver owns the monotonic countdown, chimes, and dismiss. Home
Assistant's `TimerManager` also owns the countdown and calls the handler on
finish.

If both run:

- Two clocks can drift.
- A HA `FINISHED` event can race with the TV's own expiry.
- A cancel must reach the correct owner or both.

The safest product path is therefore:

1. Keep the existing TV-local timer as the source of truth for display and chimes.
2. Use a thin handler only to translate native intents into the existing
   `kellies_voice_timer` events (start / cancel / dismiss).
3. Leave registration **opt-in and off by default** until device QA confirms
   no double-ring, no stale cancel, and no cross-satellite leakage.

## Pure mapping (implemented and tested)

`custom_components/kellies_voice/timer_routing.py` is dependency-free:

- `map_start(device_id, name, total_seconds)` → existing start payload
  (name defaults to `"Timer"`, duration clamped 1–86400)
- `map_cancel(device_id, name)` → existing cancel payload
- `map_dismiss(device_id)` → existing dismiss payload
- `event_for(payload)` → `("kellies_voice_timer", payload)`

Unit tests: `tests/test_timer_routing.py` (3 passed, no Home Assistant required).

## Recommended safe alternative (works today, no app change)

Until the native handler is enabled and device-tested, use Home Assistant
custom sentences + intent scripts **scoped to one paired TV**. This reuses
the existing `kellies_voice.start_timer` / `cancel_timer` actions. It does not
hijack other satellites when the sentences are distinct or when the action
always targets the single TV device ID.

See `docs/VOICE_TIMERS.md` for the portable YAML. Summary:

1. Add `/config/custom_sentences/en/kellie_timers.yaml` with patterns for
   "set a {duration} {unit} timer [called {name}]" and
   "cancel [the] {name} timer".
2. Add matching `intent_script` entries that call `kellies_voice.start_timer`
   and `kellies_voice.cancel_timer` with the paired TV's device ID.
3. Validate configuration and restart. No Android update required.
4. Prefer these over global unprefixed sentence triggers in multi-satellite
   homes so other Assist devices keep their native timers.

Bare "stop" / "cancel" is intentionally **not** mapped globally. It is
ambiguous with media controls. The safe form is "cancel the pizza timer" or
"cancel the timer" (named or default name).

## What a future opt-in handler would do

```python
# Sketch only — not registered by default
def make_handler(hass, device_id):
    def handler(event_type, timer_info):
        if event_type == "started":
            hass.bus.async_fire(*event_for(map_start(
                device_id, timer_info.name, timer_info.seconds)))
        elif event_type == "cancelled":
            hass.bus.async_fire(*event_for(map_cancel(
                device_id, timer_info.name)))
        # FINISHED and UPDATED: leave to TV-local logic for now
    return handler
```

Registration would use `async_register_timer_handler` only when an explicit
config flag is on and the registry device ID is known. Unregister on unload.

## Acceptance criteria still requiring physical QA

- "Set a five-minute pizza timer" and "Cancel the pizza timer" succeed only on
  the requesting paired TV.
- Other Assist satellites keep native timer behavior.
- No broadcast, no duplicate timers, no stale cancel after reconnect.
- No microphone or wake-word side effects.
- Existing manual actions and name-free "Timer" default still work.

## Constraints honored

- No edits to spoken-announcement or audio-focus code (PR #104).
- No always-on microphone, no forced intent rerouting, no model-specific prompt.
- Draft only. Do not merge, publish a release, or install an APK.

