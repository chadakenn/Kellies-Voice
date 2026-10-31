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

from .const import DOMAIN, QUICK_ACTION_CACHE
from .home_shortcuts import ACTIONS, COUNT, updated_device_option
from .quick_action_icons import ICON_CHOICES


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
        # HA sorts device Configuration entities by display name, not by
        # platform creation order. Numerically prefix the fields so each TV
        # button reads Label -> Target -> Action in one contiguous group.
        order = {"label": "1", "entity": "2", "action": "3", "icon": "4"}[field]
        self._attr_name = f"Quick Action {slot} · {order}. {name}"
        self._attr_icon = icon
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, tv_id)},
            name=entry.data.get("name", "Kellie's Voice"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )

    def _saved(self) -> str:
        default = "Turn on" if self._field == "action" else ("Auto" if self._field == "icon" else "")
        # Entity constructors run before hass is assigned. Once mounted,
        # all three editors read the SAME canonical per-TV draft, not three
        # stale ConfigEntry snapshots.
        options = self._entry.options
        hass = getattr(self, "hass", None)
        if hass is not None:
            draft = hass.data.get(DOMAIN, {}).get(QUICK_ACTION_CACHE, {}).get(
                self._entry.entry_id
            )
            if draft is not None:
                options = draft
        return options.get(f"shortcut_{self._slot}_{self._field}", default)

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
        # Never save an old config-entry snapshot. Device controls are
        # concurrent actors, and HA owns the current options in its registry.
        current_entry = self.hass.config_entries.async_get_entry(self._entry.entry_id)
        if current_entry is None:
            raise HomeAssistantError("TV settings are no longer available.")
        # ConfigEntry instances may be copied while entity platforms are
        # updated. Merge edits against ONE canonical draft shared across all
        # label, target and action entities for this exact TV. The code below
        # has no await between reading and committing the draft.
        drafts = self.hass.data.setdefault(DOMAIN, {}).setdefault(
            QUICK_ACTION_CACHE, {}
        )
        current = drafts.get(current_entry.entry_id)
        if current is None:
            current = dict(current_entry.options)
        try:
            proposed = updated_device_option(
                current, self._slot, self._field, value
            )
        except ValueError as error:
            raise HomeAssistantError(str(error)) from None
        if proposed != current:
            drafts[current_entry.entry_id] = proposed
            self.hass.config_entries.async_update_entry(
                current_entry, options=proposed
            )


class QuickActionLabel(QuickActionDeviceSetting, TextEntity):
    """User-visible TV button label, not a service or trusted payload."""

    _attr_should_poll = True
    _attr_mode = TextMode.TEXT
    _attr_native_min = 0
    _attr_native_max = 26

    def __init__(self, entry: ConfigEntry, slot: int):
        self._setup(entry, slot, "label", "Button label", "mdi:form-textbox")
        self._attr_native_value = self._saved()

    async def async_set_value(self, value: str) -> None:
        await self._set(value)
        self._attr_native_value = value.strip()
        self.async_write_ha_state()

    async def async_update(self) -> None:
        # Reflect edits made through Integrations → Configure too.
        self._attr_native_value = self._saved()


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
        self._refresh()
        self.async_write_ha_state()


class QuickActionVerb(QuickActionDeviceSetting, SelectEntity):
    """Only Home Assistant's allowlisted service verbs, not arbitrary scripts."""

    _attr_should_poll = True

    def __init__(self, entry: ConfigEntry, slot: int):
        self._setup(entry, slot, "action", "Action", "mdi:gesture-tap-button")
        self._attr_options = list(ACTIONS)
        action = self._saved()
        self._attr_current_option = action if action in ACTIONS else "Turn on"

    async def async_select_option(self, option: str) -> None:
        if option not in ACTIONS:
            raise HomeAssistantError("Choose Turn on, Turn off or Toggle.")
        await self._set(option)
        self._attr_current_option = option
        self.async_write_ha_state()

    async def async_update(self) -> None:
        value = self._saved()
        self._attr_current_option = value if value in ACTIONS else "Turn on"



class QuickActionIcon(QuickActionDeviceSetting, SelectEntity):
    """Auto HA icon by default; optional safe local-icon override."""

    _attr_should_poll = True

    def __init__(self, entry: ConfigEntry, slot: int):
        self._setup(entry, slot, "icon", "Icon", "mdi:palette")
        self._attr_options = list(ICON_CHOICES)
        value = self._saved()
        self._attr_current_option = value if value in ICON_CHOICES else "Auto"

    async def async_select_option(self, option: str) -> None:
        if option not in ICON_CHOICES:
            raise HomeAssistantError("Choose a supported Quick Action icon.")
        await self._set(option)
        self._attr_current_option = option
        self.async_write_ha_state()

    async def async_update(self) -> None:
        value = self._saved()
        self._attr_current_option = value if value in ICON_CHOICES else "Auto"


def quick_action_labels(entry: ConfigEntry) -> list[QuickActionLabel]:
    return [QuickActionLabel(entry, slot) for slot in range(1, COUNT + 1)]


def quick_action_selects(entry: ConfigEntry) -> list[SelectEntity]:
    return [
        entity
        for slot in range(1, COUNT + 1)
        for entity in (QuickActionTarget(entry, slot), QuickActionVerb(entry, slot), QuickActionIcon(entry, slot))
    ]
