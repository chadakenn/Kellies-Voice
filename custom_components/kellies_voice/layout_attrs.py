"""State attributes shared by the layout controls. This module does not import Home Assistant."""


def layout_attributes(device_id, setting, generation, registry_device_id=None):
    """Pairing id stays separate from Home Assistant's device-registry id."""
    attrs = {
        "kellie_device_id": device_id,
        "kellie_setting": setting,
        "kellie_generation": generation,
    }
    if isinstance(registry_device_id, str):
        cleaned = registry_device_id.strip()
        if cleaned:
            attrs["ha_registry_device_id"] = cleaned
    return attrs
