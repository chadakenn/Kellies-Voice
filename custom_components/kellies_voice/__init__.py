"""Kellie's Voice layout controls and TV event services."""

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .const import PLATFORMS
from .services import async_register_services
from .websocket import async_register_alert_subscription


async def async_setup(hass: HomeAssistant, config: dict) -> bool:
    async_register_services(hass)
    async_register_alert_subscription(hass)
    return True


from .timer_handler import TimerBridge, TimerHandlerRegistry

# Opt-in only. Set options["native_timer_intents"] = True to enable.
_TIMER_REGISTRIES: dict[str, TimerHandlerRegistry] = {}


def _registry_device_id(hass, entry) -> str | None:
    """Return the HA device-registry ID for this config entry, or None."""
    try:
        from homeassistant.helpers import device_registry as dr
        registry = dr.async_get(hass)
        device = registry.async_get_device(identifiers={("kellies_voice", entry.data["device_id"])})
        return device.id if device else None
    except Exception:
        return None


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    async_register_services(hass)
    async_register_alert_subscription(hass)
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    # Native timer intents are off unless explicitly opted in.
    if entry.options.get("native_timer_intents"):
        _enable_native_timers(hass, entry)
    return True


def _enable_native_timers(hass, entry) -> None:
    pairing_id = entry.data.get("device_id")
    registry_id = _registry_device_id(hass, entry)
    if not pairing_id or not registry_id or registry_id == pairing_id:
        return
    try:
        from homeassistant.components.intent import async_register_timer_handler
    except ImportError:
        return
    registry = TimerHandlerRegistry()
    bridge = TimerBridge(
        registry_id,
        pairing_id,
        fire=lambda et, data: hass.bus.async_fire(et, data),
    )
    registry.enable(bridge, lambda rid, cb: async_register_timer_handler(hass, rid, cb))
    _TIMER_REGISTRIES[entry.entry_id] = registry


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    registry = _TIMER_REGISTRIES.pop(entry.entry_id, None)
    if registry is not None:
        registry.disable_all()
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
