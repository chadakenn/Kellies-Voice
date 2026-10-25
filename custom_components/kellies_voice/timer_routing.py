"""Pure mapper for *future* native HA timer events to existing TV commands.

This module does not register a native TimerHandler or alter any HA runtime.
The Android receiver requires both a fresh Unix-issued timestamp (seconds)
and an absolute deadline (milliseconds) for a start command.
"""

from __future__ import annotations

import re
import time
from typing import Any

EVENT_TIMER = "kellies_voice_timer"
_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9 _-]{0,39}\Z")


def _target(device_id: str) -> str:
    """Never create an untargeted event; broadcasts are not allowed for timers."""
    if not isinstance(device_id, str) or not device_id.strip():
        raise ValueError("A specific TV device ID is required.")
    return device_id.strip()


def _name(name: str | None) -> str:
    cleaned = " ".join((name or "Timer").split())
    if not _NAME.fullmatch(cleaned):
        raise ValueError("Timer name must be 1-40 ASCII letters, digits, spaces, _ or -.")
    return cleaned


def _issued_ms(now_ms: int | None) -> int:
    # Explicit clock injection keeps tests deterministic.
    instant = (time.time_ns() // 1_000_000) if now_ms is None else now_ms
    if type(instant) is not int or instant <= 0:
        raise ValueError("A positive Unix timestamp in milliseconds is required.")
    return instant


def map_start(
    device_id: str, name: str | None, total_seconds: int, *,
    now_ms: int | None = None,
) -> dict[str, Any]:
    """Map a start to the full payload understood by TimerOverlayController."""
    target = _target(device_id)
    timer_name = _name(name)
    if type(total_seconds) is not int:
        raise ValueError("Timer duration must be a whole number of seconds.")
    seconds = max(1, min(86400, total_seconds))
    instant = _issued_ms(now_ms)
    return {
        "operation": "start",
        "device_id": target,
        "name": timer_name,
        "duration_seconds": seconds,
        "issued": instant // 1000,
        "ends_at_ms": instant + seconds * 1000,
        "speak_alert": False,
    }


def map_cancel(
    device_id: str, name: str | None, *, now_ms: int | None = None,
) -> dict[str, Any]:
    """Cancel a single named TV timer with a fresh timestamp."""
    return {
        "operation": "cancel",
        "device_id": _target(device_id),
        "name": _name(name),
        "issued": _issued_ms(now_ms) // 1000,
    }


def map_dismiss(device_id: str, *, now_ms: int | None = None) -> dict[str, Any]:
    """Dismiss ringing alerts, never stop arbitrary running timers."""
    return {
        "operation": "dismiss",
        "device_id": _target(device_id),
        "issued": _issued_ms(now_ms) // 1000,
    }


def event_for(payload: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    """Return one per-TV event for an explicitly validated command."""
    if not payload.get("device_id") or payload.get("issued", 0) <= 0:
        raise ValueError("Missing TV target or timestamp.")
    return EVENT_TIMER, payload
