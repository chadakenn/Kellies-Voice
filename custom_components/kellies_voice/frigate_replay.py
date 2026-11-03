"""Select recorded Frigate events for a single explicitly mapped TV camera.

This module does no I/O and never constructs media URLs. The caller must fetch
events through an authenticated Frigate client, enforce TV authorization, and
supply the confirmed Frigate camera name (not guessed from an HA entity id).
"""

from __future__ import annotations

from dataclasses import dataclass
from math import isfinite
import re

# Frigate IDs commonly resemble 1730571482.487155-abc123. Do not accept
# paths, hostnames, percent-encoding, query strings, or control characters.
_EVENT_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{5,95}\Z", re.ASCII)
_CAMERA = re.compile(r"[A-Za-z0-9_]{1,80}\Z", re.ASCII)
_LABEL = re.compile(r"[a-z][a-z0-9_]{0,39}\Z", re.ASCII)
MAX_RESULTS = 10
DEFAULT_LOOKBACK_SECONDS = 24 * 60 * 60


@dataclass(frozen=True)
class ReplayEvent:
    """Non-secret metadata, never a playable URL or Frigate credential."""

    event_id: str
    camera: str
    label: str
    started_at: float
    ended_at: float


def valid_event_id(value: object) -> bool:
    """Only Frigate-like opaque IDs, not URL/path input or traversal."""
    return (
        isinstance(value, str)
        and _EVENT_ID.fullmatch(value) is not None
        and ".." not in value
        and not value.startswith(".")
    )


def valid_camera(value: object) -> bool:
    return isinstance(value, str) and _CAMERA.fullmatch(value) is not None


def _seconds(value: object) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (float, int)):
        return None
    seconds = float(value)
    return seconds if isfinite(seconds) else None


def _parse_event(item: object, expected_camera: str, now: float,
                 lookback_seconds: int) -> ReplayEvent | None:
    if not isinstance(item, dict):
        return None
    event_id = item.get("id")
    camera = item.get("camera")
    label = item.get("label")
    if not valid_event_id(event_id) or camera != expected_camera:
        return None
    if type(item.get("has_clip")) is not bool or not item["has_clip"]:
        return None
    if item.get("false_positive") is True:
        return None
    if not isinstance(label, str) or _LABEL.fullmatch(label) is None:
        return None
    start = _seconds(item.get("start_time"))
    end = _seconds(item.get("end_time"))
    if start is None or end is None or start <= 0 or end < start:
        return None
    if not (now - lookback_seconds <= start <= now + 120
            and end <= now + 120):
        return None
    return ReplayEvent(event_id, camera, label, start, end)


def recent_recorded_events(
    items: object,
    *,
    frigate_camera: str,
    now: float,
    requested_event_id: str | None = None,
    max_results: int = 8,
    lookback_seconds: int = DEFAULT_LOOKBACK_SECONDS,
) -> list[ReplayEvent]:
    """Filter authenticated Frigate /api/events results for one mapped camera.

    A request with a specific event ID **must never** fall back to the newest
    different clip, even if it is from the same camera. This prevents a
    doorbell event accidentally showing unrelated footage.
    """
    if not valid_camera(frigate_camera):
        raise ValueError("A known, explicitly mapped Frigate camera is required")
    if requested_event_id is not None and not valid_event_id(requested_event_id):
        raise ValueError("Invalid Frigate event ID")
    if not isinstance(max_results, int) or isinstance(max_results, bool) or not 1 <= max_results <= MAX_RESULTS:
        raise ValueError("Result limit must be from 1 to 10")
    if (not isinstance(lookback_seconds, int) or isinstance(lookback_seconds, bool)
            or not 60 <= lookback_seconds <= 7 * 24 * 60 * 60):
        raise ValueError("Lookback must be from one minute to seven days")
    timestamp = _seconds(now)
    if timestamp is None or timestamp <= 0:
        raise ValueError("Invalid reference time")
    if not isinstance(items, list):
        raise ValueError("Expected Frigate event list")
    # Never process more than a bounded page from the upstream API.
    if len(items) > 100:
        raise ValueError("Frigate event page is too large")
    candidates: list[ReplayEvent] = []
    seen: set[str] = set()
    for item in items:
        event = _parse_event(item, frigate_camera, timestamp, lookback_seconds)
        if event is None or event.event_id in seen:
            continue
        if requested_event_id is not None and event.event_id != requested_event_id:
            continue
        candidates.append(event)
        seen.add(event.event_id)
    candidates.sort(key=lambda event: (event.started_at, event.event_id), reverse=True)
    return candidates[:1] if requested_event_id is not None else candidates[:max_results]
