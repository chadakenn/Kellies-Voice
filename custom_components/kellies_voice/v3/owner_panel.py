"""Authenticated Home Assistant owner approval for secure TV pairing.

The panel never transfers credentials. It only supplies an owner claim to
an HA admin, and backend WebSocket *independently* requires the actual owner.
"""
from __future__ import annotations

from pathlib import Path
from hashlib import sha256

from homeassistant.components import panel_custom
from homeassistant.components.http import StaticPathConfig

from .owner_claim import DOMAIN

_PANEL_KEY = "owner_pair_approval_panel_registered"
_PANEL_URL = "kellies-voice-approval"
_ASSET_PATH = "/api/kellies_voice/owner-panel.js"


async def async_register_pairing_approval_panel(hass) -> None:
    data = hass.data.setdefault(DOMAIN, {})
    if data.get(_PANEL_KEY):
        return

    file_path = Path(__file__).parent / "frontend" / "kellies-voice-owner-panel.js"
    if not await hass.async_add_executor_job(file_path.is_file):
        raise RuntimeError("Kellie's Voice approval panel asset is missing")

    # HA's frontend can cache ES modules across ordinary panel navigation.
    # Use a new module URL whenever the on-disk script changes.
    # Static path registration stays fixed; HTTP handles the query string.
    panel_version = await hass.async_add_executor_job(
        lambda: sha256(file_path.read_bytes()).hexdigest()[:12]
    )

    await hass.http.async_register_static_paths([
        StaticPathConfig(_ASSET_PATH, str(file_path), cache_headers=False)
    ])
    await panel_custom.async_register_panel(
        hass,
        webcomponent_name="kellies-voice-owner-panel",
        frontend_url_path=_PANEL_URL,
        sidebar_title="Kellie's Voice Pairing",
        sidebar_icon="mdi:television-check",
        module_url=f"{_ASSET_PATH}?v={panel_version}",
        require_admin=True,
        embed_iframe=False,
        config={},
        config_panel_domain=DOMAIN,
    )
    data[_PANEL_KEY] = True
