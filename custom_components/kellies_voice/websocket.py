"""Read-scoped Home Assistant WebSocket alert delivery for non-admin TV users.

The command uses the existing HA-authenticated socket. A user must have read
access to this configured TV's HA layout entity. No permanent message store,
additional TV secret or elevated HA token is needed.
"""

import probatio

from homeassistant.auth.permissions.const import POLICY_READ
from homeassistant.components import websocket_api
from homeassistant.core import HomeAssistant, callback

from .alert_transport import EVENT_FIELDS, sanitize_event
from .alert_presence import receiver_connected, receiver_disconnected
from .alert_actions import ledger
from .const import DOMAIN, EVENT_ACTION_PRESSED
from .validation import clean_tv_device_id

_REGISTERED = "alert_ws_registered"
_WS_TYPE = "kellies_voice/subscribe_alerts"


@callback
def async_register_alert_subscription(hass: HomeAssistant) -> None:
    data = hass.data.setdefault(DOMAIN, {})
    if data.get(_REGISTERED):
        return
    websocket_api.async_register_command(hass, websocket_subscribe_alerts)
    websocket_api.async_register_command(hass, websocket_press_alert_action)
    data[_REGISTERED] = True


def _tv_access_state(hass: HomeAssistant, user, tv_id: str) -> str:
    """Separate a TV still being set up from a genuine authorization failure."""
    paired = any(entry.data.get("device_id") == tv_id
                 for entry in hass.config_entries.async_entries(DOMAIN))
    if not paired or user is None:
        return "denied"
    # A paired UUID is never enough to authorize event delivery.
    permissions = user.permissions
    found_controls = False
    for state in hass.states.async_all("select"):
        attrs = state.attributes
        if attrs.get("kellie_device_id") != tv_id or attrs.get("kellie_setting") != "alert_position":
            continue
        found_controls = True
        if user.is_admin or permissions.access_all_entities(POLICY_READ):
            return "allowed"
        if permissions.check_entity(state.entity_id, POLICY_READ):
            return "allowed"
    # After an HA restart the config entry can exist before its select state.
    # A retry must still re-check the read permission once the select exists.
    return "denied" if found_controls else "not_ready"


def _tv_can_read(hass: HomeAssistant, user, tv_id: str) -> bool:
    """Require a paired TV and read access, including for each forwarded event."""
    return _tv_access_state(hass, user, tv_id) == "allowed"


@websocket_api.websocket_command({
    probatio.Required("type"): _WS_TYPE,
    probatio.Required("device_id"): str,
})
@callback
def websocket_subscribe_alerts(hass: HomeAssistant,
                                connection: websocket_api.ActiveConnection,
                                msg: dict) -> None:
    """Subscribe only to alerts for a TV this authenticated user may read."""
    try:
        device_id = clean_tv_device_id(msg["device_id"])
    except ValueError:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT,
                              "Invalid TV pairing ID")
        return
    access = _tv_access_state(hass, connection.user, device_id)
    if access == "not_ready":
        # HA can authenticate the TV while the paired select is still loading.
        # This never creates a subscription or grants permission; Android
        # retries and we enforce normal read authorization on every attempt.
        connection.send_error(msg["id"], "tv_not_ready",
                              "Paired TV alert controls are still loading")
        return
    if access != "allowed":
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "No read permission for this paired TV")
        return

    unsubscribers = []
    connection_key = object()
    @callback
    def forward(event) -> None:
        # Check permissions for every event, including after an HA role change.
        if not _tv_can_read(hass, connection.user, device_id):
            # Entity permission may have been revoked since subscription.
            # Stop the delivery channel and clear the connected diagnostic.
            # The TV must resubscribe after access is restored.
            connection.subscriptions.pop(msg["id"], None)
            unsubscribe()
            return
        payload = sanitize_event(event.event_type, event.data, device_id)
        if payload is None:
            return
        connection.send_message(websocket_api.event_message(msg["id"], {
            "event_type": event.event_type, "data": payload
        }))

    for event_type in EVENT_FIELDS:
        unsubscribers.append(hass.bus.async_listen(event_type, forward))

    @callback
    def unsubscribe() -> None:
        for remove in unsubscribers:
            remove()
        receiver_disconnected(hass, device_id, connection_key)

    connection.subscriptions[msg["id"]] = unsubscribe
    connection.send_result(msg["id"])
    # Only mark connected after successfully registering and acknowledging.
    receiver_connected(hass, device_id, connection_key)

@websocket_api.websocket_command({
    probatio.Required("type"): "kellies_voice/press_alert_action",
    probatio.Required("device_id"): str,
    probatio.Required("announcement_id"): str,
    probatio.Required("action"): str,
})
@callback
def websocket_press_alert_action(hass: HomeAssistant,
                                 connection: websocket_api.ActiveConnection,
                                 msg: dict) -> None:
    """A TV may acknowledge only one issued, unexpired button once."""
    try:
        device_id = clean_tv_device_id(msg["device_id"])
    except ValueError:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT,
                              "Invalid TV pairing ID")
        return
    if not _tv_can_read(hass, connection.user, device_id):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "No read permission for this paired TV")
        return
    if not ledger(hass).claim(device_id, msg["announcement_id"], msg["action"]):
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT,
                              "No matching active TV button")
        return
    hass.bus.async_fire(EVENT_ACTION_PRESSED, {
        "device_id": device_id,
        "announcement_id": msg["announcement_id"],
        "action": msg["action"],
    })
    connection.send_result(msg["id"])
