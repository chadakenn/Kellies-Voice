"""Layout selects. A generation of 0 means the TV has not adopted the entity yet."""

from homeassistant.components.select import SelectEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import EVENT_STATE_CHANGED, HomeAssistant, callback
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.restore_state import RestoreEntity
from homeassistant.exceptions import HomeAssistantError

from .camera_picker import NONE, SLOTS, get_camera_picker

from .const import ALERT_SELECTS, DOMAIN, FOLLOW_SELECTS, FRIGATE_SELECTS, NOISE_SELECTS, SELECTS, WEATHER_SELECTS
from .layout_attrs import layout_attributes
from .quick_action_device import quick_action_selects


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
    entities.extend(KellieSelect(entry, setting, name, options, icon, initial)
                    for setting, name, options, icon, initial in FRIGATE_SELECTS)
    entities.append(WeatherSourceSelect(entry))
    entities.extend(FrigateCameraSelect(entry, slot) for slot in range(SLOTS))
    entities.extend(quick_action_selects(entry))
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


class FrigateCameraSelect(SelectEntity):
    """List the HA cameras available to this instance, one dropdown per slot.

    Android still receives the canonical screensaver_cameras text control.
    Changing a dropdown calls that text entity, so its generation increments
    and existing TV sync works unchanged. Does not affect Frigate web URL mode.
    """

    _attr_should_poll = True
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG
    _attr_icon = "mdi:cctv"

    def __init__(self, entry: ConfigEntry, slot: int):
        self._device_id = entry.data["device_id"]
        self._entry_id = entry.entry_id
        self._slot = slot
        self._selected_entity = ""
        self._attr_name = f"Frigate snapshot camera {slot + 1}"
        self._attr_unique_id = f"{self._device_id}_screensaver_camera_slot_{slot + 1}"
        self._attr_options = [NONE]
        self._attr_current_option = NONE
        self._labels_to_entity = {NONE: ""}
        self._ready = False
        self._picker_listener = self._apply_picker_value
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )
        self._attr_extra_state_attributes = {
            "help": "Choose an available HA camera. Four dropdowns select up to four snapshots. Clear the Frigate web URL to use snapshots.",
        }

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._ready = True
        picker = get_camera_picker(self.hass, self._entry_id)
        picker.subscribe(self._slot, self._picker_listener)
        self.async_on_remove(lambda: picker.unsubscribe(self._slot, self._picker_listener))
        self.async_on_remove(self.hass.bus.async_listen(
            EVENT_STATE_CHANGED, self._camera_state_changed
        ))
        self._refresh_options()
        self.async_write_ha_state()

    @callback
    def _camera_state_changed(self, event) -> None:
        # The Home Assistant state registry is the source of available
        # cameras; users never need to type IDs into a camera slot.
        if str(event.data.get("entity_id", "")).startswith("camera."):
            if self._refresh_options():
                self.async_write_ha_state()

    def _apply_picker_value(self, entity_id: str) -> None:
        self._selected_entity = entity_id
        if self._ready:
            self._refresh_options()
            self.async_write_ha_state()

    def _refresh_options(self) -> bool:
        options = [NONE]
        mapping = {NONE: ""}
        found_ids = set()
        for state in sorted(self.hass.states.async_all("camera"), key=lambda state: state.entity_id):
            entity_id = state.entity_id
            found_ids.add(entity_id)
            name = str(state.attributes.get("friendly_name") or state.name or entity_id)
            name = " ".join(name.split())[:70]
            label = f"{name} ({entity_id})"
            options.append(label)
            mapping[label] = entity_id

        if self._selected_entity and self._selected_entity not in found_ids:
            label = f"Unavailable ({self._selected_entity})"
            options.append(label)
            mapping[label] = self._selected_entity

        current = next((label for label, entity_id in mapping.items()
                        if entity_id == self._selected_entity), NONE)
        changed = options != self._attr_options or current != self._attr_current_option
        self._attr_options = options
        self._labels_to_entity = mapping
        self._attr_current_option = current
        return changed

    async def async_update(self) -> None:
        self._refresh_options()

    async def async_select_option(self, option: str) -> None:
        self._refresh_options()
        if option not in self._labels_to_entity:
            raise HomeAssistantError("Choose a camera from the available camera list.")
        picker = get_camera_picker(self.hass, self._entry_id)
        text_entity = picker.text_entity
        if text_entity is None:
            raise HomeAssistantError("Camera list is still loading. Reopen this control.")
        try:
            proposed = picker.propose(self._slot, self._labels_to_entity[option])
        except ValueError as err:
            raise HomeAssistantError(str(err)) from None
        await text_entity.async_set_value(proposed)
