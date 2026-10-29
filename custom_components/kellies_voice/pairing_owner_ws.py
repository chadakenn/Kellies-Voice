"""Authenticated HA WebSocket claim after physical TV-code verification.

This command is for a forthcoming frontend integration UI. It does not
issue credentials, fetch tokens, forward a credential, or activate a TV.
"""
from __future__ import annotations

import probatio
from homeassistant.components import websocket_api
from homeassistant.core import HomeAssistant, callback

from .const import DOMAIN
from .pairing_owner_claim import owner_claims

_REGISTERED = "owner_pair_claim_ws_registered"
_COMMAND = "kellies_voice/pair/claim_verified_owner"


@websocket_api.websocket_command({
    probatio.Required("type"): _COMMAND,
    probatio.Required("flow_id"): str,
    probatio.Required("tv_session"): str,
})
@callback
def websocket_claim_verified_owner(hass: HomeAssistant,
                                  connection: websocket_api.ActiveConnection,
                                  msg: dict) -> None:
    """Bind only the currently signed-in *owner* of this WebSocket.

    Never use msg.user_id or a form-supplied user id. The authenticated
    WebSocket connection is the only authority for owner identity.
    """
    user = connection.user
    if (user is None or getattr(user, "is_owner", False) is not True
            or getattr(user, "is_active", False) is not True):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "An active Home Assistant owner is required")
        return
    if not owner_claims(hass).claim(
        flow_id=msg["flow_id"], session=msg["tv_session"], user=user,
    ):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "Pairing proof missing, claimed, or expired")
        return
    connection.send_result(msg["id"], {"owner_verified": True})


@websocket_api.websocket_command({
    probatio.Required("type"): "kellies_voice/pair/list_verified",
})
@callback
def websocket_list_verified(hass: HomeAssistant,
                            connection: websocket_api.ActiveConnection,
                            msg: dict) -> None:
    """Read-only pending list from this already authenticated HA session."""
    user = connection.user
    if (user is None or getattr(user, "is_owner", False) is not True
            or getattr(user, "is_active", False) is not True):
        connection.send_error(msg["id"], websocket_api.ERR_UNAUTHORIZED,
                              "An active Home Assistant owner is required")
        return
    connection.send_result(msg["id"], {
        "pending": owner_claims(hass).pending(user=user),
        "preview_only": True,
    })


@callback
def async_register_owner_claim(hass: HomeAssistant) -> None:
    data = hass.data.setdefault(DOMAIN, {})
    if data.get(_REGISTERED):
        return
    websocket_api.async_register_command(hass, websocket_claim_verified_owner)
    websocket_api.async_register_command(hass, websocket_list_verified)
    data[_REGISTERED] = True
