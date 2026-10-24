"""Sidebar page for layout settings. It only writes the existing controls."""

from pathlib import Path

from homeassistant.components import panel_custom
from homeassistant.components.http import StaticPathConfig
from homeassistant.core import HomeAssistant

from .const import DOMAIN

_PANEL_KEY = "layout_panel"
_STATIC = "/kellies_voice_static"


async def async_register_layout_panel(hass: HomeAssistant) -> None:
    """Register the page once. Later reloads must not register it again."""
    data = hass.data.setdefault(DOMAIN, {})
    if data.get(_PANEL_KEY):
        return
    root = Path(__file__).parent / "www"
    await hass.http.async_register_static_paths(
        [StaticPathConfig(_STATIC, str(root), False)]
    )
    await panel_custom.async_register_panel(
        hass,
        frontend_url_path="kellies-voice",
        webcomponent_name="kellies-voice-layout",
        sidebar_title="Kellie's Voice",
        sidebar_icon="mdi:television",
        module_url=f"{_STATIC}/layout-panel.js",
        require_admin=True,
    )
    data[_PANEL_KEY] = True
