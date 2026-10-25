"""Bounded, per-TV event payloads for the authenticated WebSocket alert channel.

The older admin-only event bus is retained for backwards compatibility.
Do not blindly forward arbitrary event data to ordinary Home Assistant users.
"""

import json

from .const import (
    EVENT_ANNOUNCEMENT,
    EVENT_CAMERA_POPUP,
    EVENT_FORECAST,
    EVENT_HIDE_CAMERA,
    EVENT_LIVE_CAMERA,
)

EVENT_FIELDS = {
    EVENT_ANNOUNCEMENT: ("device_id", "title", "text", "speak", "duration_seconds"),
    EVENT_CAMERA_POPUP: ("device_id", "entity_id", "title", "speak", "duration_seconds", "show_header", "image_fit", "refresh_seconds"),
    EVENT_LIVE_CAMERA: ("device_id", "entity_id", "title", "speak", "duration_seconds", "show_header", "image_fit", "refresh_seconds"),
    EVENT_HIDE_CAMERA: ("device_id",),
    EVENT_FORECAST: ("device_id", "location", "units", "issued", "duration_seconds", "days"),
    "kellies_voice_timer": (
        "device_id", "operation", "name", "issued",
        "ends_at_ms", "speak_alert", "alert_phrase"
    ),
}
MAX_BYTES = 8192
STRING_LIMITS = {
    "title": 65, "text": 500, "location": 80, "units": 8,
    "entity_id": 88, "name": 40, "alert_phrase": 140, "speak": 180,
    "device_id": 64, "operation": 12, "image_fit": 8,
}
INT_FIELDS = {"issued", "duration_seconds", "ends_at_ms", "refresh_seconds"}
BOOL_FIELDS = {"show_header"}
DAY_FIELDS = {"label", "condition", "high", "low", "precipitation"}


def sanitize_event(event_type: str, payload: dict, this_tv: str) -> dict | None:
    """Return an allowlisted payload only when addressed to the subscribed TV.

    No HA tokens, signed stream URLs or unexpected keys are forwarded.
    """
    allowed = EVENT_FIELDS.get(event_type)
    if allowed is None or not isinstance(payload, dict):
        return None
    addressed = payload.get("device_id")
    if addressed not in (None, "", this_tv):
        return None
    safe = {"device_id": this_tv}
    for key in allowed:
        if key == "device_id" or key not in payload:
            continue
        value = payload[key]
        if key == "speak":
            # Explicit opt-in speech only. Never synthesize arbitrary URLs or
            # oversized strings from user-controlled HA automation payloads.
            if not isinstance(value, str):
                continue
            phrase = " ".join(value.split())
            lower = phrase.lower()
            if not phrase or len(phrase) > STRING_LIMITS["speak"] or (
                "://" in lower or lower.startswith("http") or "www." in lower
            ):
                continue
            safe["speak"] = phrase
            continue
        if key in STRING_LIMITS:
            if not isinstance(value, str):
                return None
            safe[key] = value[:STRING_LIMITS[key]]
        elif key in INT_FIELDS:
            if type(value) is not int or value < 0 or value > 10 ** 15:
                return None
            safe[key] = value
        elif key in BOOL_FIELDS or key == "speak_alert":
            if not isinstance(value, bool):
                return None
            safe[key] = value
        elif key == "days":
            if not isinstance(value, list) or len(value) > 7:
                return None
            days = []
            for day in value:
                if not isinstance(day, dict):
                    return None
                values = {}
                for k in DAY_FIELDS:
                    if k in day and day[k] is not None:
                        v = day[k]
                        if not isinstance(v, (str, int, float)) or isinstance(v, bool):
                            return None
                        # Never forward arbitrary nested metadata.
                        values[k] = str(v)[:65]
                days.append(values)
            safe[key] = days
    if len(json.dumps(safe, ensure_ascii=False).encode("utf-8")) > MAX_BYTES:
        return None
    return safe
