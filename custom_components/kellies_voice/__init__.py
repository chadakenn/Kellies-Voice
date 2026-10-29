"""Kellie's Voice layout, TV events and optional native timer handler."""

import logging

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers import device_registry as dr

from .const import DOMAIN, PLATFORMS
from .oauth_callback import async_setup_oauth_callback
from .services import async_register_services
from .timer_handler import TimerBridge, TimerHandlerRegistry
from .websocket import async_register_alert_subscription

_LOGGER = logging.getLogger(__name__)
_TIMER_KEY = "native_timer_registries"


async def async_setup(hass: HomeAssistant, config: dict) -> bool:
    async_register_services(hass)
    async_register_alert_subscription(hass)
    # Same-origin OAuth callback for owner-approved pairing (#213).
    # Safe to register even when no pairing is in progress.
    await async_setup_oauth_callback(hass)
    return True


def _registry_device_id(hass: HomeAssistant, entry: ConfigEntry) -> str | None:
    pairing = entry.data.get("device_id")
    if not isinstance(pairing, str) or not pairing:
        return None
    registry = dr.async_get(hass)
    device = registry.async_get_device(identifiers={(DOMAIN, pairing)})
    return device.id if device else None


def _enable_native_timers(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Explicit opt-in, fail closed if the HA timer system is unavailable."""
    pairing_id = entry.data.get("device_id")
    registry_id = _registry_device_id(hass, entry)
    if not pairing_id or not registry_id or registry_id == pairing_id:
        _LOGGER.warning("Native timers not enabled: missing HA registry device")
        return
    try:
        from homeassistant.components.intent import (
            async_device_supports_timers,
            async_register_timer_handler,
        )
        if async_device_supports_timers(hass, registry_id):
            # HA's TimerManager overwrites an existing handler for the same
            # ID, and its unregister would remove another satellite's owner.
            _LOGGER.warning("Native timer handler already registered; not replacing it")
            return

        manager = TimerHandlerRegistry()
        bridge = TimerBridge(
            registry_id,
            pairing_id,
            fire=lambda event, data: hass.bus.async_fire(event, data),
        )
        manager.enable(
            bridge, lambda device, cb: async_register_timer_handler(hass, device, cb)
        )
        hass.data.setdefault(DOMAIN, {}).setdefault(_TIMER_KEY, {})[entry.entry_id] = manager
    except (ImportError, KeyError, ValueError, TypeError):
        _LOGGER.warning("Native timer manager not ready; voice timers stay disabled")


async def _options_updated(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Re-register only after an explicit options change, not on every event."""
    await hass.config_entries.async_reload(entry.entry_id)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    async_register_services(hass)
    async_register_alert_subscription(hass)
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    entry.async_on_unload(entry.add_update_listener(_options_updated))
    if entry.options.get("native_timer_intents", False) is True:
        _enable_native_timers(hass, entry)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        # Drop the camera-picker coordinator for this paired TV on integration reload.
        # The snapshot camera list itself is restored by the existing text entity.
        hass.data.setdefault(DOMAIN, {}).setdefault("frigate_camera_pickers", {}).pop(
            entry.entry_id, None
        )
        registries = hass.data.setdefault(DOMAIN, {}).setdefault(_TIMER_KEY, {})
        manager = registries.pop(entry.entry_id, None)
        if manager:
            manager.disable_all()
    return unloaded
