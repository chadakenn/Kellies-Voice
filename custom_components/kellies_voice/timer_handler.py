"""Opt-in native Home Assistant timer handler bridged to TV-local countdowns.

Home Assistant owns the native timer identity, seconds left, pause/resume.
The Android TV owns visible countdown and its one completion chime.
No input changes microphones, other satellites, or existing manual timers.
"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import logging
import re
import time
import unicodedata
from typing import Any, Callable

from .timer_routing import event_for, map_cancel, map_start

_LOGGER = logging.getLogger(__name__)
STARTED, UPDATED, CANCELLED, FINISHED = "started", "updated", "cancelled", "finished"


def tv_timer_name(original: str | None, ha_id: str) -> str:
    """Per-native-timer namespace prevents two Pizza timers sharing a TV slot.

    Must fit the Android 40-character ASCII timer name constraint. The suffix
    is an opaque stable digest, not credentials or the HA timer ID itself.
    """
    if not isinstance(ha_id, str) or not ha_id or len(ha_id) > 128:
        raise ValueError("Expected a bounded, nonempty HA timer ID")
    normalized = unicodedata.normalize("NFKD", original or "Timer")
    readable = normalized.encode("ascii", "ignore").decode("ascii")
    readable = re.sub(r"[^A-Za-z0-9 _-]+", " ", readable)
    readable = re.sub(r"\s+", " ", readable).strip(" _-")
    readable = (readable or "Timer")[:25].rstrip(" _-")
    tag = hashlib.sha256(ha_id.encode("utf-8")).hexdigest()[:10].upper()
    return f"HA {readable} {tag}"


@dataclass
class _Owned:
    name: str
    active: bool


class TimerBridge:
    """Translate a *single registered HA device* to a *single paired TV*."""

    def __init__(
        self, registry_device_id: str, pairing_id: str,
        fire: Callable[[str, dict[str, Any]], None],
        now_ms: Callable[[], int] | None = None,
    ) -> None:
        if not registry_device_id or not pairing_id or registry_device_id == pairing_id:
            raise ValueError("Separate HA registry and TV pairing IDs are required")
        self.registry_device_id = registry_device_id
        self.pairing_id = pairing_id
        self._fire = fire
        self._now_ms = now_ms or (lambda: time.time_ns() // 1_000_000)
        self._owned: dict[str, _Owned] = {}

    def handle(self, event_type: str, timer_info: Any) -> None:
        """Never let malformed native events interrupt HA's timer manager."""
        try:
            self._handle(str(event_type), timer_info)
        except (ValueError, TypeError, AttributeError, OverflowError) as exc:
            _LOGGER.warning("Skipped invalid native TV timer event: %s", type(exc).__name__)

    def _handle(self, event_type: str, timer_info: Any) -> None:
        ha_id = getattr(timer_info, "id", None)
        device = getattr(timer_info, "device_id", None)
        # Home Assistant's own TimerManager dispatches to this registry ID.
        # Reject a callback for any other satellite instead of broadcasting.
        if device != self.registry_device_id or not isinstance(ha_id, str) or not ha_id:
            return
        now = self._now_ms()
        owned = self._owned.get(ha_id)
        if event_type == STARTED:
            if owned is not None:
                return  # Do not duplicate a native start replay.
            name = tv_timer_name(getattr(timer_info, "name", None), ha_id)
            if any(slot.name == name for slot in self._owned.values()):
                _LOGGER.warning("Native timer hash collision; did not overwrite another timer")
                return
            seconds = self._remaining(timer_info)
            if seconds == 0:
                return
            self._emit(map_start(self.pairing_id, name, seconds, now_ms=now))
            self._owned[ha_id] = _Owned(name=name, active=True)
            return
        if owned is None:
            return  # Unknown or already-finished native timer.
        if event_type == CANCELLED:
            if owned.active:
                self._emit(map_cancel(self.pairing_id, owned.name, now_ms=now))
            self._owned.pop(ha_id, None)
        elif event_type == FINISHED:
            # Android countdown owns the chime. Do not play a second chime.
            self._owned.pop(ha_id, None)
        elif event_type == UPDATED:
            active = bool(getattr(timer_info, "is_active", False))
            if not active:
                if owned.active:
                    self._emit(map_cancel(self.pairing_id, owned.name, now_ms=now))
                    owned.active = False
                return  # Paused timers must not keep counting on TV.
            seconds = self._remaining(timer_info)
            if seconds <= 0:
                if owned.active:
                    self._emit(map_cancel(self.pairing_id, owned.name, now_ms=now))
                    owned.active = False
                return
            # A resumed or adjusted timer starts ONE TV countdown using the
            # actual remaining seconds, not the original total timer duration.
            self._emit(map_start(self.pairing_id, owned.name, seconds, now_ms=now))
            owned.active = True

    @staticmethod
    def _remaining(timer_info: Any) -> int:
        seconds = getattr(timer_info, "seconds_left", None)
        if type(seconds) is not int or seconds < 0 or seconds > 86400:
            raise ValueError("Invalid or unsupported native timer remaining duration")
        return seconds

    def _emit(self, payload: dict[str, Any]) -> None:
        event, data = event_for(payload)
        try:
            self._fire(event, data)
        except Exception:  # noqa: BLE001 - never break HA's timer event callback
            _LOGGER.exception("Could not forward native TV timer event")

    def drop_all(self) -> None:
        """Unloading never cancels user TV timers or broadcasts commands."""
        self._owned.clear()


class TimerHandlerRegistry:
    """Own unregister callbacks and handle repeated setup/unload safely."""

    def __init__(self) -> None:
        self._unregisters: dict[str, Callable[[], None]] = {}
        self._bridges: dict[str, TimerBridge] = {}

    def enable(self, bridge: TimerBridge, register: Callable) -> None:
        key = bridge.registry_device_id
        if key in self._unregisters:
            return
        unregister = register(key, bridge.handle)
        if not callable(unregister):
            raise TypeError("Timer handler registration returned no unregister")
        self._unregisters[key] = unregister
        self._bridges[key] = bridge

    def disable_all(self) -> None:
        for key in list(self._unregisters):
            bridge = self._bridges.pop(key, None)
            unregister = self._unregisters.pop(key)
            if bridge is not None:
                bridge.drop_all()
            try:
                unregister()
            except Exception:  # noqa: BLE001
                _LOGGER.exception("Could not unregister Kellie's Voice timer handler")

    @property
    def enabled_ids(self) -> list[str]:
        return list(self._unregisters)
