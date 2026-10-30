"""Device-page Quick Actions settings, linked to the existing per-TV HA options.

The three controls per slot appear under the Android TV device's native
Configuration section. The Android app only receives slot numbers and labels.
Home Assistant revalidates the actual service and authenticated TV user when
the button is pressed; these controls cannot grant a TV any new permissions.
"""

from homeassistant.components.select import SelectEntity
from homeassistant.components.text import TextEntity, TextMode
from homeassistant.config_entries import ConfigEntry
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.entity import DeviceInfo, EntityCategory

from .const import DOMAIN
from .home_shortcuts import ACTIONS, COUNT, updated_device_option


NO_TARGET = "No action configured"
TARGET_DOMAINS = ("light", "scene", "script")


class QuickActionDeviceSetting:
    """Shared per-TV native-entity settings, never the source of an action."""

    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.CONFIG
    _attr_should_poll = False

    def _setup(self, entry: ConfigEntry, slot: int, field: str, name: str, icon: str):
        self._entry = entry
        self._slot = slot
        self._field = field
        tv_id = entry.data["device_id"]
        self._attr_unique_id = f"{tv_id}_quick_action_{slot}_{field}"
        self._attr_name = f"Quick Action {slot} · {name}"
        self._attr_icon = icon
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, tv_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )

    def _saved(self) -> str:
        default = "Turn on" if self._field == "action" else ""
        return self._entry.options.get(
            f"shortcut_{self._slot}_{self._field}", default
        )

    async def _set(self, value: str) -> None:
        # Native entity services are callable beyond the integration's admin
        # options flow. Match that flow's owner-only edit authority here.
        context = getattr(self, "_context", None)
        user_id = getattr(context, "user_id", None)
        user = await self.hass.auth.async_get_user(user_id) if user_id else None
        if user is None or not user.is_admin:
            raise HomeAssistantError(
                "Only a Home Assistant administrator can edit TV Quick Actions."
            )
        try:
            proposed = updated_device_option(
                self._entry.options, self._slot, self._field, value
            )
        except ValueError as error:
            # Display an actionable HA UI error without exposing private data.
            raise HomeAssistantError(str(error)) from None
        if proposed != self._entry.options:
            # The existing per-TV options update listener reloads the entry
            # and makes the new slot immediately visible to its WebSocket.
            self.hass.config_entries.async_update_entry(
                self._entry, options=proposed
            )


class QuickActionLabel(QuickActionDeviceSetting, TextEntity):
    """User-visible TV button label, not a service or trusted payload."""

    _attr_mode = TextMode.TEXT
    _attr_native_min = 0
    _attr_native_max = 26

    def __init__(self, entry: ConfigEntry, slot: int):
        self._setup(entry, slot, "label", "Button label", "mdi:form-textbox")
        self._attr_native_value = self._saved()

    async def async_set_value(self, value: str) -> None:
        await self._set(value)


class QuickActionTarget(QuickActionDeviceSetting, SelectEntity):
    """Friendly-name entity picker restricted to light / scene / script."""

    _attr_should_poll = True

    def __init__(self, entry: ConfigEntry, slot: int):
        self._setup(entry, slot, "entity", "Target", "mdi:target")
        self._attr_options = [NO_TARGET]
        self._attr_current_option = NO_TARGET
        self._selection_to_entity = {NO_TARGET: ""}

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._refresh()

    async def async_update(self) -> None:
        self._refresh()

    def _refresh(self) -> None:
        options = [NO_TARGET]
        mapping = {NO_TARGET: ""}
        for domain in TARGET_DOMAINS:
            for state in sorted(
                self.hass.states.async_all(domain), key=lambda s: s.entity_id
            ):
                eid = state.entity_id
                friendly = str(
                    state.attributes.get("friendly_name") or state.name or eid
                )
                friendly = " ".join(friendly.split())[:65]
                option = f"{friendly} ({eid})"
                options.append(option)
                mapping[option] = eid

        selected = self._saved()
        if selected and selected not in mapping.values():
            missing = f"Unavailable ({selected})"
            options.append(missing)
            mapping[missing] = selected
        self._attr_options = options
        self._selection_to_entity = mapping
        self._attr_current_option = next(
            (name for name, entity_id in mapping.items() if entity_id == selected),
            NO_TARGET,
        )

    async def async_select_option(self, option: str) -> None:
        self._refresh()
        if option not in self._selection_to_entity:
            raise HomeAssistantError("Select an available light, scene or script.")
        await self._set(self._selection_to_entity[option])


class QuickActionVerb(QuickActionDeviceSetting, SelectEntity):
    """Only Home Assistant's allowlisted service verbs, not arbitrary scripts."""

    def __init__(self, entry: ConfigEntry, slot: int):
        self._setup(entry, slot, "action", "Action", "mdi:gesture-tap-button")
        self._attr_options = list(ACTIONS)
        action = self._saved()
        self._attr_current_option = action if action in ACTIONS else "Turn on"

    async def async_select_option(self, option: str) -> None:
        if option not in ACTIONS:
            raise HomeAssistantError("Choose Turn on, Turn off or Toggle.")
        await self._set(option)


def quick_action_labels(entry: ConfigEntry) -> list[QuickActionLabel]:
    return [QuickActionLabel(entry, slot) for slot in range(1, COUNT + 1)]


def quick_action_selects(entry: ConfigEntry) -> list[SelectEntity]:
    return [
        entity
        for slot in range(1, COUNT + 1)
        for entity in (QuickActionTarget(entry, slot), QuickActionVerb(entry, slot))
    ]
