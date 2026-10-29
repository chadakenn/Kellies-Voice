"""Isolated v3 TV cryptographic verification QA, no real Kellie's Voice login."""
from homeassistant.core import HomeAssistant

async def async_setup(hass: HomeAssistant, config: dict) -> bool:
    return True

async def async_setup_entry(hass: HomeAssistant, entry) -> bool:
    # The QA config flow deliberately never creates any entries.
    # Refuse unexpected manual entries rather than silently claim a login.
    return False
