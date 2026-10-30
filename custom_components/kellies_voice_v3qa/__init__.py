"""Isolated v3 TV verification and owner-only QA. No HA account changes.

The normal Kellie's Voice integration and credentials are never imported.
An HA Zeroconf config flow does not necessarily run async_setup(), so the
owner-only WebSocket and panel are explicitly initialized AFTER TV-OK proof.
"""
from .owner_ws import async_register_owner_claim
from .owner_panel import async_register_pairing_approval_panel

async def async_setup(hass, config):
    return True

async def async_register_qa_owner_controls(hass):
    # No token, no OAuth routes, no additional LAN listener.
    async_register_owner_claim(hass)
    await async_register_pairing_approval_panel(hass)
