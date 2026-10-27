"""A per-TV indicator for authenticated scoped alert receiver connectivity.

It intentionally does not report "TV online" or "message delivered".
Legacy v0.16.4 receivers will show disconnected until upgraded.
"""

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .alert_presence import signal, tracker
from .const import DOMAIN


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry,
                            async_add_entities: AddEntitiesCallback) -> None:
    async_add_entities([AlertReceiverConnected(entry)])


class AlertReceiverConnected(BinarySensorEntity):
    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.DIAGNOSTIC
    _attr_device_class = BinarySensorDeviceClass.CONNECTIVITY
    _attr_name = "TV alert receiver connected"
    _attr_icon = "mdi:television-check"

    def __init__(self, entry: ConfigEntry):
        self._tv_id = entry.data["device_id"]
        self._attr_unique_id = f"{self._tv_id}_scoped_alert_connection"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._tv_id)},
            name=entry.data.get("name", "Kellie's Voice TV"),
            manufacturer="Kellie's Voice",
            model="Android TV",
        )

    @property
    def is_on(self):
        return tracker(self.hass).online(self._tv_id)

    @property
    def extra_state_attributes(self):
        return {
            "last_seen_since_ha_start": tracker(self.hass).last_seen(self._tv_id)
                    or "never",
            "connection_type": "scoped_alert_receiver",
            "message_delivery_confirmed": False,
        }

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self.async_on_remove(async_dispatcher_connect(
            self.hass, signal(self._tv_id), self._refresh
        ))

    @callback
    def _refresh(self) -> None:
        self.async_write_ha_state()
