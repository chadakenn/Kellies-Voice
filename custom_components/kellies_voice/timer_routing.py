"""Prototype mapping for native HA timer events to Kellie's Voice timer bus events.

This module is pure and unit-testable. It does not register with TimerManager.
Full registration belongs in a later implementation once device-ID transport
from PR #50 is confirmed and physical tests pass.
"""

from typing import Any

EVENT_TIMER = "kellies_voice_timer"


def map_start(device_id: str, name: str | None, total_seconds: int) -> dict[str, Any]:
    """Map a HA timer start to the existing start event payload."""
    return {
        "operation": "start",
        "device_id": device_id,
        "name": name or "Timer",
        "duration_seconds": max(1, min(86400, int(total_seconds))),
    }


def map_cancel(device_id: str, name: str | None) -> dict[str, Any]:
    """Map a HA timer cancel to the existing cancel event payload."""
    return {
        "operation": "cancel",
        "device_id": device_id,
        "name": name or "Timer",
    }


def map_dismiss(device_id: str) -> dict[str, Any]:
    """Map a dismiss-all request."""
    return {
        "operation": "dismiss",
        "device_id": device_id,
    }


def event_for(payload: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    """Return (event_type, data) ready for hass.bus.async_fire."""
    return EVENT_TIMER, payload
