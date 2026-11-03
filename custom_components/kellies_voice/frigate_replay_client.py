"""Bounded replay metadata lookup using an already-authorized Frigate client.

Callers must resolve the client from the *existing Home Assistant Frigate
integration* after verifying the requesting TV's permission to view the
specific HA camera and the Frigate instance/camera mapping. This module
cannot invent/configure a Frigate connection or issue media URLs.
"""

from __future__ import annotations

from collections.abc import Mapping
from .frigate_replay import (
    DEFAULT_LOOKBACK_SECONDS,
    ReplayEvent,
    recent_recorded_events,
    valid_camera,
    valid_event_id,
)


class FrigateReplayUnavailable(Exception):
    """Safe, non-secret error class for an unavailable recording."""


async def fetch_recorded_event_metadata(
    client: object,
    *,
    configured_cameras: Mapping[str, object],
    frigate_camera: str,
    now: float,
    requested_event_id: str | None = None,
    limit: int = 8,
    lookback_seconds: int = DEFAULT_LOOKBACK_SECONDS,
) -> list[ReplayEvent]:
    """Obtain only recorded, authorized-camera event summaries.

    `client` must be the Frigate HA integration's already authenticated
    FrigateApiClient for the *correct* config entry. The caller separately
    enforces TV, camera entity and instance authorization; never accept a
    client, URL, account ID or configured camera list from an Android event.
    """
    if not isinstance(configured_cameras, Mapping):
        raise ValueError("Confirmed Frigate camera mapping required")
    if not valid_camera(frigate_camera) or frigate_camera not in configured_cameras:
        raise ValueError("Camera is not configured on this Frigate instance")
    if requested_event_id is not None and not valid_event_id(requested_event_id):
        raise ValueError("Invalid Frigate event ID")
    if not isinstance(limit, int) or isinstance(limit, bool) or not 1 <= limit <= 10:
        raise ValueError("Invalid replay result limit")
    if not isinstance(lookback_seconds, int) or isinstance(lookback_seconds, bool) or not 60 <= lookback_seconds <= 7 * 86400:
        raise ValueError("Invalid replay lookback")
    if not callable(getattr(client, "async_get_event", None)) or not callable(
        getattr(client, "async_get_events", None)
    ):
        raise FrigateReplayUnavailable("Frigate client lacks required event API")
    # Validate time before making any request. Reuse selection validation.
    recent_recorded_events(
        [], frigate_camera=frigate_camera, now=now,
        requested_event_id=requested_event_id, max_results=limit,
        lookback_seconds=lookback_seconds,
    )
    try:
        if requested_event_id is not None:
            # Never request the entire home archive for an exact event.
            raw = await client.async_get_event(requested_event_id)
            results = [raw]
        else:
            results = await client.async_get_events(
                cameras=[frigate_camera],
                after=int(now - lookback_seconds),
                limit=min(50, max(10, limit * 3)),
                has_clip=True,
            )
    except Exception as exc:
        # Do not leak endpoint URLs, login state, internal host or credentials.
        raise FrigateReplayUnavailable("Frigate event lookup unavailable") from None

    return recent_recorded_events(
        results, frigate_camera=frigate_camera, now=now,
        requested_event_id=requested_event_id, max_results=limit,
        lookback_seconds=lookback_seconds,
    )
