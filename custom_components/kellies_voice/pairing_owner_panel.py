"""QA-only authenticated Home Assistant frontend for owner TV approval.

Does not authorize OAuth or transfer credentials. Panel is only available to
an HA admin, and backend WebSocket *independently* requires the actual owner.
"""
from __future__ import annotations

from pathlib import Path

from homeassistant.components import panel_custom
from homeassistant.components.http import StaticPathConfig

from .const import DOMAIN

_PANEL_KEY = "owner_pair_approval_panel_registered"
_PANEL_URL = "kellies-voice-pairing"
_ASSET_PATH = "/api/kellies_voice/pair/approval-frontend.js"


async def async_register_pairing_approval_panel(hass) -> None:
    data = hass.data.setdefault(DOMAIN, {})
    if data.get(_PANEL_KEY):
        return

    file_path = Path(__file__).parent / "frontend" / "kellies-voice-pair-approval-panel.js"
    if not file_path.is_file():
        raise RuntimeError("Kellie's Voice approval panel asset is missing")

    await hass.http.async_register_static_paths([
        StaticPathConfig(_ASSET_PATH, str(file_path), cache_headers=False)
    ])
    await panel_custom.async_register_panel(
        hass,
        webcomponent_name="kellies-voice-pair-approval-panel",
        frontend_url_path=_PANEL_URL,
        sidebar_title="Kellie's Voice Pairing",
        sidebar_icon="mdi:television-check",
        module_url=_ASSET_PATH,
        require_admin=True,
        embed_iframe=False,
        config={},
        config_panel_domain=DOMAIN,
    )
    data[_PANEL_KEY] = True
