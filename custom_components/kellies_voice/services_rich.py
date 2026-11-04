"""Rich card service registration. Imported from services.py."""

import logging

from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import ServiceValidationError
import voluptuous as vol
from homeassistant.helpers import config_validation as cv

from .alert_actions import ledger
from .const import DOMAIN, EVENT_RICH_CARD, SERVICE_SHOW_RICH_CARD
from .rich_cards import clean_rows, clean_card_type
from .validation import clean_title, clean_duration, clean_actions
from .services import _event_targets  # shared helper

_LOGGER = logging.getLogger(__name__)

SHOW_RICH_CARD_SCHEMA = vol.Schema(
    {
        vol.Required("title"): cv.string,
        vol.Required("type"): vol.In(("list", "card")),
        vol.Required("rows"): list,
        vol.Optional("subtitle", default=""): cv.string,
        vol.Optional("image_entity", default=""): cv.string,
        vol.Optional("duration_seconds", default=20): vol.All(
            vol.Coerce(int), vol.Range(min=8, max=45)
        ),
        vol.Optional("actions"): list,
        vol.Optional("device_id"): cv.string,
    }
)


async def _async_show_rich_card(call: ServiceCall) -> None:
    """Show a temporary rich card on the TV overlay."""
    try:
        title = clean_title(call.data.get("title"))
        card_type = clean_card_type(call.data.get("type"))
        rows = clean_rows(call.data.get("rows"))
        subtitle = (call.data.get("subtitle") or "")[:80]
        image_entity = call.data.get("image_entity") or ""
        if image_entity and not image_entity.startswith(("camera.", "media_player.")):
            raise ValueError("image_entity must be a camera or media_player entity.")
        duration = clean_duration(call.data.get("duration_seconds", 20), 20)
        actions = clean_actions(call.data.get("actions"))
        targets = _event_targets(call, call.data.get("device_id"))
    except ValueError as err:
        raise ServiceValidationError(str(err)) from None
    for device_id in targets:
        issued = ledger(call.hass).issue(device_id, actions, duration)
        data = {
            "title": title,
            "subtitle": subtitle,
            "type": card_type,
            "rows": rows,
            "duration_seconds": duration,
            "device_id": device_id,
        }
        if image_entity:
            data["image_entity"] = image_entity
        if issued:
            data.update(actions=actions, announcement_id=issued)
        call.hass.bus.async_fire(EVENT_RICH_CARD, data)
    _LOGGER.debug("Fired %s for %s configured TV(s)", EVENT_RICH_CARD, len(targets))


def register_rich_card(hass: HomeAssistant) -> None:
    hass.services.async_register(
        DOMAIN, SERVICE_SHOW_RICH_CARD, _async_show_rich_card, schema=SHOW_RICH_CARD_SCHEMA
    )
