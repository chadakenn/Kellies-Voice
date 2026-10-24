"""Screensaver camera list. Editing it does not open the microphone."""

from homeassistant.components.text import TextEntity, TextMode
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.restore_state import RestoreEntity

from .const import DOMAIN, SCREENSAVER_CAMERAS, SCREENSAVER_CAMERAS_EMPTY
from .validation import clean_screensaver_cameras


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    async_add_entities([ScreensaverCamerasText(entry)])


class ScreensaverCamerasText(TextEntity, RestoreEntity):
    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG
    _attr_icon = "mdi:cctv"
    _attr_mode = TextMode.TEXT
    _attr_native_max = 180
    _attr_name = "Screensaver cameras"

    def __init__(self, entry: ConfigEntry) -> None:
        device_id = entry.data["device_id"]
        self._device_id = device_id
        self._generation = 0
        self._value = SCREENSAVER_CAMERAS_EMPTY
        self._attr_unique_id = f"{device_id}_{SCREENSAVER_CAMERAS}"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, device_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )
        self._attr_extra_state_attributes = self._attributes()

    def _attributes(self) -> dict:
        return {
            "kellie_device_id": self._device_id,
            "kellie_setting": SCREENSAVER_CAMERAS,
            "kellie_generation": self._generation,
        }

    @property
    def native_value(self) -> str:
        return self._value

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        last = await self.async_get_last_state()
        if last is None or last.state in ("unknown", "unavailable", ""):
            return
        try:
            self._value = clean_screensaver_cameras(last.state)
        except ValueError:
            self._value = SCREENSAVER_CAMERAS_EMPTY
        try:
            self._generation = int(last.attributes.get("kellie_generation", 0))
        except (TypeError, ValueError):
            self._generation = 0
        self._attr_extra_state_attributes = self._attributes()

    async def async_set_value(self, value: str) -> None:
        self._value = clean_screensaver_cameras(value)
        self._generation += 1
        self._attr_extra_state_attributes = self._attributes()
        self.async_write_ha_state()
