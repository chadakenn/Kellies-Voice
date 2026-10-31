"""Production pairing-v3 enrollment primitives, vendored from proven isolated beta.

This module runs only after explicit Home Assistant owner consent. Normal
Kellie's Voice services remain available if this optional subsystem fails.
"""
import asyncio
import logging
from pathlib import Path
from .token_lease_journal import TokenLeaseJournal
from .owner_ws import async_register_owner_claim
from .owner_panel import async_register_pairing_approval_panel

_LOGGER = logging.getLogger(__name__)
_READY = "v3_owner_controls_ready"
_DOMAIN = "kellies_voice"


async def async_register_owner_ui(hass):
    """Set up owner approval once, including on a *first* discovered TV.

    Home Assistant may discover several temporary pairing sessions before the
    first config entry exists. The HTTP server accepts dynamic routes after
    startup (HA Core 2026.10), but registration must be serialized so that
    two discovery flows cannot both register the same WebSocket command or
    static frontend panel. Never mark ready on a partial failure.
    """
    data = hass.data.setdefault(_DOMAIN, {})
    if data.get(_READY) is True:
        return True
    lock = data.setdefault("v3_owner_registration_lock", asyncio.Lock())
    async with lock:
        if data.get(_READY) is True:
            return True
        try:
            async_register_owner_claim(hass)
            await async_register_pairing_approval_panel(hass)
            journal = TokenLeaseJournal(
                hass, Path(hass.config.path(".storage", "kellies_voice_tv_leases.json"))
            )
            await journal.load()
            recovery = await journal.recover_unfinished()
            if any(value == "retry_required" for value in recovery.values()):
                raise RuntimeError("Unresolved TV credential recovery")
            data["v3_token_journal"] = journal
        except Exception:
            _LOGGER.exception("Kellie's Voice owner pairing could not initialize")
            data[_READY] = False
            return False
        data[_READY] = True
        return True

def owner_controls_ready(hass):
    return hass.data.get(_DOMAIN, {}).get(_READY) is True
