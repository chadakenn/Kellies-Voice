"""Fail-closed replay permission gate for a paired, authenticated TV.

No network I/O, media URLs, or credentials. Mapping comes only from the
owner-managed Kellie's Voice config entry options, NEVER from an Android TV
command or an event payload. No replay route is registered by this module.
"""
from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
import re

from .const import DOMAIN
from .frigate_replay import valid_camera

_ENTITY = re.compile(r"camera\.[a-z0-9_]{1,80}\Z", re.ASCII)
_MAPPING_KEY = "replay_camera_map"


class ReplayAccessDenied(Exception):
    """Do not reveal existence of another TV, camera or Frigate instance."""


@dataclass(frozen=True)
class ReplayScope:
    tv_id: str
    camera_entity_id: str
    frigate_entry_id: str
    frigate_camera: str


def authorize_replay_request(hass, user, tv_id: str, camera_entity_id: str,
                             entity_registry) -> ReplayScope:
    """Authorize replay for a single mapped Frigate camera.

    `user` MUST be the active Home Assistant WebSocket connection.user, not a
    user ID supplied by the TV. `entity_registry` is HA's registered entity
    registry. The existing WebSocket TV permission gate is checked each time.
    No fallback by camera name, friendly name or guessed Frigate instance.
    """
    # The imported gate checks that a TV config entry is paired, its
    # per-TV select has loaded, and this exact user may read that select.
    from .websocket import _tv_can_read, POLICY_READ

    if (user is None or not isinstance(tv_id, str)
            or not _tv_can_read(hass, user, tv_id)):
        raise ReplayAccessDenied("Replay not authorized")
    if (not isinstance(camera_entity_id, str)
            or _ENTITY.fullmatch(camera_entity_id) is None):
        raise ReplayAccessDenied("Replay not authorized")
    # Separate camera permission in addition to the paired TV permission.
    permissions = getattr(user, "permissions", None)
    if permissions is None or not (
        user.is_admin
        or permissions.access_all_entities(POLICY_READ)
        or permissions.check_entity(camera_entity_id, POLICY_READ)
    ):
        raise ReplayAccessDenied("Replay not authorized")

    tv_entries = [
        e for e in hass.config_entries.async_entries(DOMAIN)
        if e.data.get("device_id") == tv_id
    ]
    if len(tv_entries) != 1:
        raise ReplayAccessDenied("Replay not authorized")
    configured = tv_entries[0].options.get(_MAPPING_KEY, {})
    if not isinstance(configured, Mapping) or len(configured) > 16:
        raise ReplayAccessDenied("Replay not authorized")
    mapping = configured.get(camera_entity_id)
    if not isinstance(mapping, Mapping) or set(mapping) != {
        "frigate_entry_id", "frigate_camera"
    }:
        raise ReplayAccessDenied("Replay not authorized")
    entry_id = mapping["frigate_entry_id"]
    frigate_camera = mapping["frigate_camera"]
    if not isinstance(entry_id, str) or not entry_id or len(entry_id) > 64:
        raise ReplayAccessDenied("Replay not authorized")
    if not valid_camera(frigate_camera):
        raise ReplayAccessDenied("Replay not authorized")

    # The HA camera must actually belong to the explicitly mapped Frigate
    # config entry. This disallows unrelated generic cameras, renamed
    # lookalikes, and cross-instance playback.
    state = hass.states.get(camera_entity_id)
    registered = entity_registry.async_get(camera_entity_id)
    if state is None or registered is None:
        raise ReplayAccessDenied("Replay not authorized")
    if registered.config_entry_id != entry_id:
        raise ReplayAccessDenied("Replay not authorized")
    instances = [
        e for e in hass.config_entries.async_entries("frigate")
        if e.entry_id == entry_id
    ]
    if len(instances) != 1:
        raise ReplayAccessDenied("Replay not authorized")
    return ReplayScope(tv_id, camera_entity_id, entry_id, frigate_camera)
