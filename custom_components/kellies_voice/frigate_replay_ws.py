"""Authenticated, per-TV Frigate replay *metadata* lookup.

This WebSocket command never plays media, issues a clip URL, changes a camera,
or accepts a Frigate host, account, entry ID or camera name from Android.
All selection originates from owner-managed TV config-entry options and an
actual authenticated TV user's HA camera-read permissions.
"""
from __future__ import annotations

from collections.abc import Mapping
import time

import probatio
from homeassistant.components import websocket_api
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import entity_registry as er

from .const import DOMAIN
from .frigate_replay_access import ReplayAccessDenied, authorize_replay_request
from .frigate_replay_client import (
    FrigateReplayUnavailable, fetch_recorded_event_metadata,
)

_REGISTERED = "frigate_replay_metadata_ws_registered"
_WS_TYPE = "kellies_voice/replay_events"


@callback
def async_register_replay_metadata(hass: HomeAssistant) -> None:
    state = hass.data.setdefault(DOMAIN, {})
    if state.get(_REGISTERED):
        return
    websocket_api.async_register_command(hass, websocket_replay_metadata)
    state[_REGISTERED] = True


@websocket_api.websocket_command({
    probatio.Required("type"): _WS_TYPE,
    probatio.Required("device_id"): str,
    probatio.Required("entity_id"): str,
    probatio.Optional("event_id"): str,
    probatio.Optional("limit", default=8): int,
})
@websocket_api.async_response
async def websocket_replay_metadata(hass, connection, msg) -> None:
    """Return only scoped event summaries, not media links or untrusted JSON."""
    try:
        scope = authorize_replay_request(
            hass, connection.user, msg["device_id"], msg["entity_id"],
            er.async_get(hass),
        )
    except ReplayAccessDenied:
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "Replay not authorized")
        return

    # Read the existing Frigate integration's in-memory *loaded* API client.
    # Its token, URL and auth mode remain entirely inside HA. No new login.
    instance = hass.data.get("frigate", {}).get(scope.frigate_entry_id)
    if not isinstance(instance, Mapping):
        connection.send_error(msg["id"], "replay_unavailable",
                              "Frigate replay not available")
        return
    client = instance.get("client")
    config = instance.get("config")
    cameras = config.get("cameras") if isinstance(config, Mapping) else None
    if not isinstance(cameras, Mapping) or scope.frigate_camera not in cameras:
        connection.send_error(msg["id"], "replay_unavailable",
                              "Frigate replay not available")
        return

    limit = msg.get("limit", 8)
    if type(limit) is not int or not 1 <= limit <= 10:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT,
                              "Invalid replay request")
        return

    try:
        matches = await fetch_recorded_event_metadata(
            client,
            configured_cameras=cameras,
            frigate_camera=scope.frigate_camera,
            now=time.time(),
            requested_event_id=msg.get("event_id"),
            limit=limit,
        )
    except ValueError:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT,
                              "Invalid replay request")
        return
    except FrigateReplayUnavailable:
        connection.send_error(msg["id"], "replay_unavailable",
                              "Frigate replay not available")
        return

    # An explicit bounded wire shape; never return credentials, untrusted
    # Frigate response JSON, images, thumbnails, or video/clip URLs.
    connection.send_result(msg["id"], {
        "camera_entity_id": scope.camera_entity_id,
        "events": [
            {
                "event_id": event.event_id,
                "label": event.label,
                "start_time": event.started_at,
                "end_time": event.ended_at,
            } for event in matches
        ],
    })
