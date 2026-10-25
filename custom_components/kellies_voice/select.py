"""Layout selects. A generation of 0 means the TV has not adopted the entity yet."""

from homeassistant.components.select import SelectEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.restore_state import RestoreEntity

from .const import ALERT_SELECTS, DOMAIN, FOLLOW_SELECTS, NOISE_SELECTS, SELECTS, WEATHER_SELECTS
from .layout_attrs import layout_attributes


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    entities = [
        KellieSelect(entry, setting, name, options, icon)
        for setting, name, options, icon in SELECTS
    ]
    entities.extend(
        KellieSelect(entry, setting, name, options, icon, initial)
        for setting, name, options, icon, initial in ALERT_SELECTS
    )
    entities.extend(
        KellieSelect(entry, setting, name, options, icon, initial)
        for setting, name, options, icon, initial in NOISE_SELECTS
    )
    entities.extend(
        KellieSelect(entry, setting, name, options, icon, initial)
        for setting, name, options, icon, initial in FOLLOW_SELECTS
    )
    entities.extend(
        KellieSelect(entry, setting, name, options, icon, initial)
        for setting, name, options, icon, initial in WEATHER_SELECTS
    )
    entities.append(WeatherSourceSelect(entry))
    async_add_entities(entities)


class KellieSelect(SelectEntity, RestoreEntity):
    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG

    def __init__(self, entry: ConfigEntry, setting: str, name: str, options: list, icon: str,
                 initial=None):
        self._setting = setting
        self._device_id = entry.data["device_id"]
        self._generation = 0
        self._attr_name = name
        self._attr_unique_id = f"{self._device_id}_{setting}"
        self._attr_options = list(options)
        self._attr_current_option = initial if initial in self._attr_options else self._attr_options[0]
        self._attr_icon = icon
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )
        self._attr_extra_state_attributes = self._attributes()

    def _attributes(self) -> dict:
        registry = None
        if self.registry_entry is not None and self.registry_entry.device_id:
            registry = self.registry_entry.device_id
        return layout_attributes(self._device_id, self._setting, self._generation, registry)

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        last = await self.async_get_last_state()
        if last is not None:
            if last.state in self._attr_options:
                self._attr_current_option = last.state
            try:
                self._generation = int(last.attributes.get("kellie_generation", 0))
            except (TypeError, ValueError):
                self._generation = 0
        # Registry entry becomes available after the entity has been added.
        self._attr_extra_state_attributes = self._attributes()

    async def async_select_option(self, option: str) -> None:
        if option not in self._attr_options:
            return
        self._attr_current_option = option
        self._generation += 1
        self._attr_extra_state_attributes = self._attributes()
        self.async_write_ha_state()


class WeatherSourceSelect(SelectEntity, RestoreEntity):
    """Which Home Assistant weather entity feeds the on-demand forecast.

    This stays in Home Assistant. It does not start the microphone or move a popup.
    """

    _attr_should_poll = True
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG
    _attr_icon = "mdi:weather-partly-cloudy"
    _attr_name = "Forecast source"

    def __init__(self, entry: ConfigEntry):
        self._device_id = entry.data["device_id"]
        self._attr_unique_id = f"{self._device_id}_weather_source"
        self._attr_options = ["Not set"]
        self._attr_current_option = "Not set"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )
        self._attr_extra_state_attributes = {
            "kellie_device_id": self._device_id,
            "kellie_setting": "weather_source",
        }

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._refresh_options()
        last = await self.async_get_last_state()
        if last is not None and last.state in self._attr_options:
            self._attr_current_option = last.state

    async def async_update(self) -> None:
        self._refresh_options()

    def _refresh_options(self) -> None:
        found = sorted(
            state.entity_id
            for state in self.hass.states.async_all("weather")
        )
        options = found or ["Not set"]
        self._attr_options = options
        if self._attr_current_option not in options:
            self._attr_current_option = "Not set" if "Not set" in options else options[0]

    async def async_select_option(self, option: str) -> None:
        self._refresh_options()
        if option not in self._attr_options or option == "Not set":
            return
        self._attr_current_option = option
        self.async_write_ha_state()
