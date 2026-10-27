"""Per-TV diagnostics from authenticated, opt-in alert WebSocket reports.

No claim that a popup was visible on HDMI or a live camera decoded a frame.
"""
from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .alert_diagnostics import diagnostics
from .alert_presence import signal, tracker
from .const import DOMAIN


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry,
                            async_add_entities: AddEntitiesCallback) -> None:
    async_add_entities([TvAppVersion(entry), TvLastPopupResult(entry)])


class _TvDiagnostics(SensorEntity):
    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    def __init__(self, entry: ConfigEntry, key: str, name: str, icon: str):
        self._tv_id = entry.data["device_id"]
        self._attr_name = name
        self._attr_icon = icon
        self._attr_unique_id = f"{self._tv_id}_{key}"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._tv_id)},
            name=entry.data.get("name", "Kellie's Voice TV"),
            manufacturer="Kellie's Voice", model="Android TV")

    @property
    def connected(self):
        return tracker(self.hass).online(self._tv_id)

    @property
    def status(self):
        return diagnostics(self.hass).snapshot(self._tv_id)

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self.async_on_remove(async_dispatcher_connect(
            self.hass, signal(self._tv_id), self._refresh))

    @callback
    def _refresh(self) -> None:
        self.async_write_ha_state()


class TvAppVersion(_TvDiagnostics):
    def __init__(self, entry):
        super().__init__(entry, "reported_app_version", "TV app version (reported)", "mdi:cellphone-information")

    @property
    def native_value(self):
        # Unknown (not zero) until an authenticated modern TV reports itself.
        return self.status.get("app_version", "Unknown")

    @property
    def extra_state_attributes(self):
        row = self.status
        return {
            "app_version_code": row.get("app_code"),
            "last_reported_since_ha_start": row.get("reported_at") or "never",
            "alert_receiver_connected": self.connected,
            "source": "authenticated_scoped_tv_websocket",
        }


class TvLastPopupResult(_TvDiagnostics):
    def __init__(self, entry):
        super().__init__(entry, "last_popup_receipt", "TV last popup receipt", "mdi:television-check")

    @property
    def native_value(self):
        # Popup created != drawn pixels, audio played, or a guaranteed delivery.
        return self.status.get("last_result", "Never confirmed")

    @property
    def extra_state_attributes(self):
        row = self.status
        return {
            "last_kind": row.get("last_kind") or "unknown",
            "last_received_since_ha_start": row.get("last_received_at") or "never",
            "last_popup_created_since_ha_start": row.get("last_popup_created_at") or "never",
            "alert_receiver_connected": self.connected,
            "scope": "this_tv_only",
            "not_proof_of_visible_pixels": True,
        }
