"""Opt-in native Assist timer bridge for paired Kellie's Voice TVs.

Registers a Home Assistant TimerHandler against the **HA device-registry ID**
(not the TV pairing UUID). Translates STARTED / UPDATED / CANCELLED into the
existing kellies_voice_timer events the Android receiver already understands.
FINISHED is intentionally ignored so the TV's own countdown owns the chime
and there is no double-ring.

Disabled by default. Callers must explicitly opt a TV in.
"""

from __future__ import annotations

import logging
from typing import Any, Callable

try:
    from .timer_routing import event_for, map_cancel, map_start
except ImportError:  # unit test loads files without the package
    from timer_routing import event_for, map_cancel, map_start  # type: ignore


_LOGGER = logging.getLogger(__name__)

# Event type strings match homeassistant.components.intent.timers.TimerEventType
STARTED = "started"
UPDATED = "updated"
CANCELLED = "cancelled"
FINISHED = "finished"


class TimerBridge:
    """Per-TV bridge. Pure enough to unit-test without Home Assistant.

    registry_device_id: the HA device registry ID used to register the handler.
    pairing_id: the Kellie's Voice pairing UUID sent in event payloads.
    """

    def __init__(
        self,
        registry_device_id: str,
        pairing_id: str,
        fire: Callable[[str, dict[str, Any]], None],
        now_ms: Callable[[], int] | None = None,
    ) -> None:
        if not registry_device_id or not pairing_id:
            raise ValueError("Both registry device ID and pairing ID are required.")
        if registry_device_id == pairing_id:
            # They must be distinct; registering against the pairing UUID is wrong.
            raise ValueError("Registry device ID must differ from the TV pairing ID.")
        self.registry_device_id = registry_device_id
        self.pairing_id = pairing_id
        self._fire = fire
        self._now_ms = now_ms
        # ha_timer_id -> (name, issued_seconds) so stale cancels are dropped.
        self._owned: dict[str, tuple[str, int]] = {}

    def handle(self, event_type: str, timer_info: Any) -> None:
        """Translate one native timer event. Safe to call from the HA callback."""
        ha_id = str(getattr(timer_info, "id", "") or "")
        name = getattr(timer_info, "name", None)
        seconds = int(getattr(timer_info, "seconds", 0) or 0)
        now = self._now_ms() if self._now_ms else None

        if event_type == STARTED:
            payload = map_start(self.pairing_id, name, seconds, now_ms=now)
            self._owned[ha_id] = (payload["name"], payload["issued"])
            self._emit(payload)
        elif event_type == UPDATED:
            # Replace the TV timer with the new remaining duration.
            # Same-name start replaces; no second countdown is created.
            if ha_id not in self._owned:
                return
            payload = map_start(self.pairing_id, name, seconds, now_ms=now)
            self._owned[ha_id] = (payload["name"], payload["issued"])
            self._emit(payload)
        elif event_type == CANCELLED:
            owned = self._owned.pop(ha_id, None)
            if owned is None:
                # Stale or unknown cancel — do not touch the TV.
                return
            payload = map_cancel(self.pairing_id, owned[0], now_ms=now)
            self._emit(payload)
        elif event_type == FINISHED:
            # TV-local countdown already owns the chime. Ignore to avoid double-ring.
            self._owned.pop(ha_id, None)
        else:
            _LOGGER.debug("Ignoring unknown timer event %s", event_type)

    def _emit(self, payload: dict[str, Any]) -> None:
        event_type, data = event_for(payload)
        self._fire(event_type, data)

    def drop_all(self) -> None:
        """Forget tracked timers without firing events (e.g. on unload)."""
        self._owned.clear()


class TimerHandlerRegistry:
    """Tracks opt-in handlers so they can be unregistered cleanly.

    Does not call Home Assistant itself. The integration supplies the
    register/unregister callables from async_register_timer_handler.
    """

    def __init__(self) -> None:
        self._unregisters: dict[str, Callable[[], None]] = {}
        self._bridges: dict[str, TimerBridge] = {}

    def enable(
        self,
        bridge: TimerBridge,
        register: Callable[[str, Callable], Callable[[], None]],
    ) -> None:
        """Register the bridge's handler against its registry device ID."""
        rid = bridge.registry_device_id
        if rid in self._unregisters:
            return  # already registered
        handler = bridge.handle
        unregister = register(rid, handler)
        self._unregisters[rid] = unregister
        self._bridges[rid] = bridge

    def disable(self, registry_device_id: str) -> None:
        """Unregister one TV and drop its tracked timers."""
        unregister = self._unregisters.pop(registry_device_id, None)
        bridge = self._bridges.pop(registry_device_id, None)
        if bridge is not None:
            bridge.drop_all()
        if unregister is not None:
            try:
                unregister()
            except Exception:  # noqa: BLE001 — never fail unload
                _LOGGER.debug("Timer handler unregister failed for %s", registry_device_id)

    def disable_all(self) -> None:
        for rid in list(self._unregisters):
            self.disable(rid)

    def is_enabled(self, registry_device_id: str) -> bool:
        return registry_device_id in self._unregisters

    @property
    def enabled_ids(self) -> list[str]:
        return list(self._unregisters)
