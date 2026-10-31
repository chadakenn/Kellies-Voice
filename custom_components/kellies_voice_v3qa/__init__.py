"""Isolated v3 identity and HA owner proof, with no credentials.

The approval panel and authenticated WebSocket commands must be registered
during Home Assistant startup, not from a live Zeroconf config-flow.
To opt into this developer QA integration, set `kellies_voice_v3qa:`
in configuration.yaml and restart HA. Stable Kellie's Voice is untouched.
"""
import logging
from pathlib import Path
from homeassistant.helpers import config_validation as cv
from .token_lease_journal import TokenLeaseJournal

from .owner_ws import async_register_owner_claim
from .owner_panel import async_register_pairing_approval_panel

_LOGGER = logging.getLogger(__name__)
DOMAIN = "kellies_voice_v3qa"
# This optional, isolated QA integration is explicitly enabled via an empty YAML key.
CONFIG_SCHEMA = cv.empty_config_schema(DOMAIN)
_READY = "owner_controls_ready"


async def async_setup(hass, config):
    """Install only the token-free owner approval UI during HA startup."""
    try:
        async_register_owner_claim(hass)
        await async_register_pairing_approval_panel(hass)
        journal = TokenLeaseJournal(
            hass, Path(hass.config.path(".storage",
                                      "kellies_voice_v3qa_tv_leases.json")))
        await journal.load()
        recovery = await journal.recover_unfinished()
        if any(v == "retry_required" for v in recovery.values()):
            raise RuntimeError("TV token recovery must be resolved before pairing")
        hass.data.setdefault(DOMAIN, {})["token_journal"] = journal
    except Exception:
        # This was previously silently discarded as owner_unavailable.
        # Do not include pairing session, UUID, codes or secrets in log text.
        _LOGGER.exception("Kellie's Voice v3 QA owner-panel startup failed")
        hass.data.setdefault(DOMAIN, {})[_READY] = False
        return False
    hass.data.setdefault(DOMAIN, {})[_READY] = True
    return True


def owner_controls_ready(hass):
    """The pairing flow must not modify an already running HTTP router."""
    return hass.data.get(DOMAIN, {}).get(_READY) is True
