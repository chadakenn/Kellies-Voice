"""Per-TV Frigate web address and camera list text controls."""

from homeassistant.components.text import TextEntity, TextMode
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.restore_state import RestoreEntity

from .const import DOMAIN
from .layout_attrs import layout_attributes
from .frigate_settings import validate_frigate_setting


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    async_add_entities([
        KellieFrigateText(entry, "screensaver_url", "Frigate web URL", "mdi:web"),
        KellieFrigateText(entry, "screensaver_cameras", "Frigate HA camera entities", "mdi:cctv"),
    ])


class KellieFrigateText(TextEntity, RestoreEntity):
    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG
    _attr_mode = TextMode.TEXT
    _attr_native_min = 0
    _attr_native_max = 255

    def __init__(self, entry: ConfigEntry, setting: str, name: str, icon: str):
        self._device_id = entry.data["device_id"]
        self._setting = setting
        self._generation = 0
        self._attr_name = name
        self._attr_icon = icon
        self._attr_unique_id = f"{self._device_id}_{setting}"
        self._attr_native_value = ""
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice", model="Android TV",
        )
        self._attr_extra_state_attributes = self._attributes()

    def _attributes(self) -> dict:
        registry = self.registry_entry.device_id if self.registry_entry is not None else None
        return layout_attributes(self._device_id, self._setting, self._generation, registry)

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        last = await self.async_get_last_state()
        if last is not None and last.state not in ("unknown", "unavailable"):
            try:
                self._attr_native_value = validate_frigate_setting(self._setting, last.state)
                self._generation = max(0, int(last.attributes.get("kellie_generation", 0)))
            except (TypeError, ValueError):
                self._attr_native_value = ""
                self._generation = 0
        self._attr_extra_state_attributes = self._attributes()

    async def async_set_value(self, value: str) -> None:
        checked = validate_frigate_setting(self._setting, value)
        self._attr_native_value = checked
        self._generation += 1
        self._attr_extra_state_attributes = self._attributes()
        self.async_write_ha_state()
