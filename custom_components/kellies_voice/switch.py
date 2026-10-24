"""Auto-listen switch. Changing it does not open the microphone by itself."""

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.restore_state import RestoreEntity

from .const import DOMAIN
from .layout_attrs import layout_attributes


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    async_add_entities([KellieAutoListen(entry)])


class KellieAutoListen(SwitchEntity, RestoreEntity):
    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG
    _attr_icon = "mdi:microphone"

    def __init__(self, entry: ConfigEntry) -> None:
        device_id = entry.data["device_id"]
        self._device_id = device_id
        self._generation = 0
        self._attr_name = "Auto-listen"
        self._attr_unique_id = f"{device_id}_auto_listen"
        self._attr_is_on = True
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, device_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )
        self._attr_extra_state_attributes = self._attributes()

    def _attributes(self) -> dict:
        registry = None
        if self.registry_entry is not None and self.registry_entry.device_id:
            registry = self.registry_entry.device_id
        return layout_attributes(self._device_id, "auto_listen", self._generation, registry)

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        last = await self.async_get_last_state()
        if last is not None:
            self._attr_is_on = last.state == "on"
            try:
                self._generation = int(last.attributes.get("kellie_generation", 0))
            except (TypeError, ValueError):
                self._generation = 0
        self._attr_extra_state_attributes = self._attributes()

    async def async_turn_on(self, **kwargs) -> None:
        self._set(True)

    async def async_turn_off(self, **kwargs) -> None:
        self._set(False)

    def _set(self, value: bool) -> None:
        self._attr_is_on = value
        self._generation += 1
        self._attr_extra_state_attributes = self._attributes()
        self.async_write_ha_state()
