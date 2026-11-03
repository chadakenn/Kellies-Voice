"""Validate owner-configured TV to Home Assistant Frigate camera mappings.

The owner selects an existing HA camera and explicitly names its Frigate
camera. We derive the Frigate installation from HA's entity registry rather
than accepting a URL, access token or unverified client-controlled entry ID.
"""
from __future__ import annotations

from collections.abc import Mapping
import re

_ENTITY = re.compile(r"camera\.[a-z0-9_]{1,80}\Z", re.ASCII)
_FRIGATE_CAMERA = re.compile(r"[a-zA-Z0-9_]{1,80}\Z", re.ASCII)
MAX_MAPPINGS = 4


class ReplayMappingError(ValueError):
    """Invalid owner-specified pairing; do not save partial configuration."""


def mapped_camera_options(registry_entries: object, frigate_entry_ids: object) -> list[str]:
    """Enumerate HA camera entities owned by currently configured Frigate entries.

    Registry/state sources must come from HA, never from the TV.
    """
    if not isinstance(frigate_entry_ids, (set, frozenset)):
        return []
    try:
        records = registry_entries.values()
    except (AttributeError, TypeError):
        return []
    return sorted({
        item.entity_id for item in records
        if isinstance(getattr(item, "entity_id", None), str)
        and _ENTITY.fullmatch(item.entity_id)
        and getattr(item, "config_entry_id", None) in frigate_entry_ids
        and not getattr(item, "disabled_by", None)
    })


def validate_replay_mapping(
    entity_id: object,
    frigate_camera: object,
    *,
    registry_entities: Mapping,
    frigate_entry_ids: set[str],
) -> dict[str, dict[str, str]]:
    """Return a mapping suitable for one TV's HA-owned config-entry options.

    Both blank means explicitly disabled; a partially entered mapping is an
    error rather than a silent fallback to the wrong recording.
    """
    if entity_id == "" and frigate_camera == "":
        return {}
    if (not isinstance(entity_id, str)
            or _ENTITY.fullmatch(entity_id) is None
            or not isinstance(frigate_camera, str)
            or _FRIGATE_CAMERA.fullmatch(frigate_camera) is None):
        raise ReplayMappingError("Choose one Frigate camera and enter its exact Frigate name")

    record = registry_entities.get(entity_id)
    entry_id = getattr(record, "config_entry_id", None)
    if (not isinstance(entry_id, str)
            or entry_id not in frigate_entry_ids
            or getattr(record, "disabled_by", None) is not None):
        raise ReplayMappingError("Camera is not owned by a configured Frigate instance")
    return {entity_id: {
        "frigate_entry_id": entry_id,
        "frigate_camera": frigate_camera,
    }}


def current_replay_fields(options: object) -> tuple[str, str]:
    """Graceful display for incomplete/obsolete HA options; never authorize them."""
    if not isinstance(options, Mapping):
        return "", ""
    values = options.get("replay_camera_map", {})
    if not isinstance(values, Mapping) or len(values) != 1:
        return "", ""
    try:
        entity, info = next(iter(values.items()))
    except (ValueError, TypeError):
        return "", ""
    if (not isinstance(entity, str) or not isinstance(info, Mapping)
            or set(info) != {"frigate_camera", "frigate_entry_id"}
            or not isinstance(info.get("frigate_camera"), str)):
        return "", ""
    return entity, info["frigate_camera"]
