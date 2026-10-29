"""Authenticated, per-TV Home Assistant shortcuts for the full-screen app only.

Clients send only slot numbers. They cannot pick entities or services.
Entity permissions are checked against the authenticated HA user on every
fetch and every press, then actions inherit the same user context.
"""

import time

import probatio
from homeassistant.auth.permissions.const import POLICY_CONTROL
from homeassistant.components import websocket_api
from homeassistant.core import Context, HomeAssistant, callback

from .const import DOMAIN
from .home_shortcuts import entries_from_options, selection
from .validation import clean_tv_device_id
from .websocket import _tv_can_read

_REGISTERED = "home_shortcuts_ws_registered"
_REQUESTS = "home_shortcut_last_pressed"


def _entry_for_tv(hass, tv_id):
    return next(
        (entry for entry in hass.config_entries.async_entries(DOMAIN)
         if entry.data.get("device_id") == tv_id), None
    )


def _authorized_slot(user, shortcut):
    return user is not None and (
        user.is_admin or user.permissions.check_entity(
            shortcut["entity_id"], POLICY_CONTROL
        )
    )


@callback
def async_register_home_shortcuts(hass: HomeAssistant):
    state = hass.data.setdefault(DOMAIN, {})
    if state.get(_REGISTERED):
        return
    websocket_api.async_register_command(hass, websocket_get_home_shortcuts)
    websocket_api.async_register_command(hass, websocket_press_home_shortcut)
    state[_REGISTERED] = True


@websocket_api.websocket_command({
    probatio.Required("type"): "kellies_voice/get_home_shortcuts",
    probatio.Required("device_id"): str,
})
@callback
def websocket_get_home_shortcuts(hass, connection, msg):
    """Expose labels and slot IDs only to an authorized paired TV user."""
    try:
        tv_id = clean_tv_device_id(msg["device_id"])
    except ValueError:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT, "Invalid device")
        return
    if not _tv_can_read(hass, connection.user, tv_id):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED, "TV access denied")
        return
    entry = _entry_for_tv(hass, tv_id)
    if entry is None:
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED, "TV not paired")
        return
    slots = [
        {"slot": item["slot"], "label": item["label"]}
        for item in entries_from_options(entry.options)
        if _authorized_slot(connection.user, item)
    ]
    connection.send_result(msg["id"], {"shortcuts": slots})


@websocket_api.websocket_command({
    probatio.Required("type"): "kellies_voice/press_home_shortcut",
    probatio.Required("device_id"): str,
    probatio.Required("slot"): int,
})
@websocket_api.async_response
async def websocket_press_home_shortcut(hass, connection, msg):
    """Run only a server-configured allowlisted action on explicit TV press."""
    try:
        tv_id = clean_tv_device_id(msg["device_id"])
    except ValueError:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT, "Invalid device")
        return
    if not _tv_can_read(hass, connection.user, tv_id):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED, "TV access denied")
        return
    entry = _entry_for_tv(hass, tv_id)
    shortcut = selection(entry.options, msg["slot"]) if entry else None
    if shortcut is None:
        connection.send_error(msg["id"], websocket_api.ERR_INVALID_FORMAT,
                              "Shortcut no longer configured")
        return
    if not _authorized_slot(connection.user, shortcut):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "Entity control not permitted")
        return

    # Extra guard against repeated remote OK events. Neither HA nor a
    # security-sensitive client is permitted to select the target service.
    rate = hass.data.setdefault(DOMAIN, {}).setdefault(_REQUESTS, {})
    key = (connection.user.id, tv_id, msg["slot"])
    now = time.monotonic()
    if now - rate.get(key, -1000) < 0.8:
        connection.send_error(msg["id"], "shortcut_busy", "Repeated press")
        return
    rate[key] = now
    try:
        await hass.services.async_call(
            shortcut["domain"], shortcut["service"],
            {"entity_id": shortcut["entity_id"]},
            blocking=True, context=Context(user_id=connection.user.id),
        )
    except Exception:
        # No target IDs, potentially sensitive HA details or exception text
        # are exposed to the TV or written to logs.
        connection.send_error(msg["id"], "shortcut_failed",
                              "Action failed or is unavailable")
        return
    connection.send_result(msg["id"], {"slot": shortcut["slot"], "ok": True})
