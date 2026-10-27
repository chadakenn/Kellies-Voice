"""Home Assistant services that fire TV events. They do not talk to the TV directly."""

import logging
import time

from .alert_actions import ledger

import voluptuous as vol
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv

from .alert_presence import tracker
from .const import (
    DEFAULT_ANNOUNCE_SECONDS,
    DEFAULT_CAMERA_SECONDS,
    DEFAULT_LIVE_SECONDS,
    DOMAIN,
    EVENT_ANNOUNCEMENT,
    EVENT_CAMERA_POPUP,
    EVENT_LIVE_CAMERA,
    EVENT_HIDE_CAMERA,
    EVENT_FORECAST,
    MAX_DURATION_SECONDS,
    MAX_LIVE_SECONDS,
    MIN_LIVE_SECONDS,
    MIN_DURATION_SECONDS,
    SERVICE_ANNOUNCE,
    SERVICE_SHOW_CAMERA,
    SERVICE_SHOW_LIVE_CAMERA,
    SERVICE_HIDE_CAMERA,
    SERVICE_SHOW_FORECAST,
)
from .validation import (
    clean_camera_entity,
    clean_snapshot_refresh,
    clean_duration,
    clean_text,
    clean_speak,
    clean_actions,
    clean_title,
    clean_forecast_days,
    clean_forecast_duration,
    clean_forecast_place,
    clean_forecast_unit,
    clean_weather_entity,
    forecast_rows,
    target_device_ids,
    targets_with_receiver_feedback,
    clean_timer_name,
    clean_timer_seconds,
    clean_timer_phrase,
)

_LOGGER = logging.getLogger(__name__)
_REGISTERED = "services_registered"

ANNOUNCE_SCHEMA = vol.Schema(
    {
        vol.Required("title"): cv.string,
        vol.Required("text"): cv.string,
        vol.Optional("duration_seconds", default=DEFAULT_ANNOUNCE_SECONDS): vol.All(
            vol.Coerce(int),
            vol.Range(min=MIN_DURATION_SECONDS, max=MAX_DURATION_SECONDS),
        ),
        vol.Optional("speak"): cv.string,
        vol.Optional("actions"): list,
        vol.Optional("device_id"): cv.string,
    }
)

SHOW_CAMERA_SCHEMA = vol.Schema(
    {
        vol.Required("entity_id"): cv.string,
        vol.Optional("title", default="Camera alert"): cv.string,
        vol.Optional("show_header", default=True): cv.boolean,
        vol.Optional("image_fit", default="crop"): vol.In(("crop", "fit")),
        vol.Optional("refresh_seconds", default=0): vol.All(
            vol.Coerce(int), vol.Any(0, vol.Range(min=2, max=30))
        ),
        vol.Optional("duration_seconds", default=DEFAULT_CAMERA_SECONDS): vol.All(
            vol.Coerce(int),
            vol.Range(min=MIN_DURATION_SECONDS, max=MAX_DURATION_SECONDS),
        ),
        vol.Optional("speak"): cv.string,
        vol.Optional("actions"): list,
        vol.Optional("device_id"): cv.string,
    }
)


SHOW_LIVE_SCHEMA = vol.Schema(
    {
        vol.Required("entity_id"): cv.string,
        vol.Optional("title", default="Live camera"): cv.string,
        vol.Optional("show_header", default=True): cv.boolean,
        vol.Optional("image_fit", default="crop"): vol.In(("crop", "fit")),
        vol.Optional("refresh_seconds", default=0): vol.All(
            vol.Coerce(int), vol.Any(0, vol.Range(min=2, max=30))
        ),
        vol.Optional("duration_seconds", default=DEFAULT_LIVE_SECONDS): vol.All(
            vol.Coerce(int), vol.Range(min=MIN_LIVE_SECONDS, max=MAX_LIVE_SECONDS)
        ),
        vol.Optional("speak"): cv.string,
        vol.Optional("actions"): list,
        vol.Optional("device_id"): cv.string,
    }
)


HIDE_CAMERA_SCHEMA = vol.Schema(
    {
        vol.Optional("device_id"): cv.string,
    }
)

SHOW_FORECAST_SCHEMA = vol.Schema(
    {
        vol.Optional("entity_id"): cv.string,
        vol.Optional("duration_seconds"): vol.All(
            vol.Coerce(int), vol.Range(min=8, max=45)
        ),
        vol.Optional("device_id"): cv.string,
    }
)

START_TIMER_SCHEMA = vol.Schema({
    vol.Required("device_id"): cv.string,
    vol.Optional("name", default="Timer"): clean_timer_name,
    vol.Required("duration_seconds"): clean_timer_seconds,
    vol.Optional("speak_alert", default=False): cv.boolean,
    vol.Optional("alert_phrase", default=""): clean_timer_phrase,
})
CANCEL_TIMER_SCHEMA = vol.Schema({
    vol.Required("device_id"): cv.string,
    vol.Optional("name", default="Timer"): clean_timer_name,
})
DISMISS_TIMERS_SCHEMA = vol.Schema({vol.Required("device_id"): cv.string})


def async_register_services(hass: HomeAssistant) -> None:
    """Register TV-event services once; HA layout entities remain untouched."""
    data = hass.data.setdefault(DOMAIN, {})
    if data.get(_REGISTERED):
        return
    data[_REGISTERED] = True
    hass.services.async_register(DOMAIN, "start_timer", _async_start_timer, schema=START_TIMER_SCHEMA)
    hass.services.async_register(DOMAIN, "cancel_timer", _async_cancel_timer, schema=CANCEL_TIMER_SCHEMA)
    hass.services.async_register(DOMAIN, "dismiss_timers", _async_dismiss_timers, schema=DISMISS_TIMERS_SCHEMA)
    hass.services.async_register(
        DOMAIN, SERVICE_ANNOUNCE, _async_announce, schema=ANNOUNCE_SCHEMA
    )
    hass.services.async_register(
        DOMAIN, SERVICE_SHOW_CAMERA, _async_show_camera, schema=SHOW_CAMERA_SCHEMA
    )
    hass.services.async_register(
        DOMAIN, SERVICE_SHOW_LIVE_CAMERA, _async_show_live_camera, schema=SHOW_LIVE_SCHEMA
    )
    hass.services.async_register(
        DOMAIN, SERVICE_HIDE_CAMERA, _async_hide_camera, schema=HIDE_CAMERA_SCHEMA
    )
    hass.services.async_register(
        DOMAIN, SERVICE_SHOW_FORECAST, _async_show_forecast, schema=SHOW_FORECAST_SCHEMA
    )


def configured_device_ids(hass: HomeAssistant) -> list:
    """App device ids from Kellie's Voice config entries. Not Home Assistant tokens."""
    found = []
    for entry in hass.config_entries.async_entries(DOMAIN):
        device_id = entry.data.get("device_id")
        if isinstance(device_id, str):
            found.append(device_id)
    return found



def _event_targets(call, requested):
    """Check targeted delivery against known, live scoped TV subscriptions.

    An online socket does not prove that Android displayed the event.
    Legacy-only accounts keep their existing best-effort semantics.
    """
    present = tracker(call.hass)
    active = [device_id for device_id in present.seen if present.online(device_id)]
    targets = targets_with_receiver_feedback(
        requested, configured_device_ids(call.hass), active, present.seen
    )
    if isinstance(requested, str) and requested.strip() and not present.seen:
        _LOGGER.warning(
            "TV event requested before any scoped receiver was observed; "
            "legacy receiver status is unknown, delivery not confirmed"
        )
    return targets


def _timer_target(call):
    # Timer commands address exactly one paired TV. Never silently broadcast.
    requested = call.data.get("device_id", "").strip()
    if not requested:
        raise ServiceValidationError("Choose one paired TV device id for timers.")
    try:
        return _event_targets(call, requested)[0]
    except ValueError as error:
        raise ServiceValidationError(str(error)) from None


async def _async_start_timer(call: ServiceCall):
    device_id = _timer_target(call)
    try:
        name = clean_timer_name(call.data.get("name", "Timer"))
        seconds = clean_timer_seconds(call.data.get("duration_seconds"))
        phrase = clean_timer_phrase(call.data.get("alert_phrase", ""))
    except ValueError as error:
        raise ServiceValidationError(str(error)) from None
    call.hass.bus.async_fire("kellies_voice_timer", {
        "operation": "start", "device_id": device_id, "name": name,
        "issued": int(time.time()), "ends_at_ms": int(time.time() * 1000) + seconds * 1000,
        "speak_alert": call.data.get("speak_alert", False), "alert_phrase": phrase,
    })


async def _async_cancel_timer(call: ServiceCall):
    device_id = _timer_target(call)
    try:
        name = clean_timer_name(call.data.get("name", "Timer"))
    except ValueError as error:
        raise ServiceValidationError(str(error)) from None
    call.hass.bus.async_fire("kellies_voice_timer", {
        "operation": "cancel", "device_id": device_id, "name": name, "issued": int(time.time()),
    })


async def _async_dismiss_timers(call: ServiceCall):
    call.hass.bus.async_fire("kellies_voice_timer", {
        "operation": "dismiss", "device_id": _timer_target(call), "issued": int(time.time()),
    })


async def _async_announce(call: ServiceCall) -> None:
    try:
        title = clean_title(call.data.get("title"))
        text = clean_text(call.data.get("text"))
        duration = clean_duration(
            call.data.get("duration_seconds", DEFAULT_ANNOUNCE_SECONDS),
            DEFAULT_ANNOUNCE_SECONDS,
        )
        speak = clean_speak(call.data.get("speak"))
        actions = clean_actions(call.data.get("actions"))
        targets = _event_targets(call, call.data.get("device_id"))
    except ValueError as err:
        raise ServiceValidationError(str(err)) from None
    for device_id in targets:
        issued = ledger(call.hass).issue(device_id, actions, duration)
        data = {
            "title": title,
            "text": text,
            "duration_seconds": duration,
            "device_id": device_id,
        }
        if speak:
            data["speak"] = speak
        if issued:
            data.update(actions=actions, announcement_id=issued)
        call.hass.bus.async_fire(EVENT_ANNOUNCEMENT, data)
    _LOGGER.debug("Fired %s for %s configured TV(s)", EVENT_ANNOUNCEMENT, len(targets))


async def _async_show_camera(call: ServiceCall) -> None:
    try:
        entity_id = clean_camera_entity(call.data.get("entity_id"))
        show_header = call.data.get("show_header", True)
        title = (clean_title(call.data.get("title", "Camera alert"))
                 if show_header else "")
        fit = call.data.get("image_fit", "crop")
        if fit not in ("fit", "crop"):
            raise ValueError("Choose image_fit 'fit' or 'crop'.")
        refresh = clean_snapshot_refresh(call.data.get("refresh_seconds", 0))
        duration = clean_duration(
            call.data.get("duration_seconds", DEFAULT_CAMERA_SECONDS),
            DEFAULT_CAMERA_SECONDS,
        )
        speak = clean_speak(call.data.get("speak"))
        actions = clean_actions(call.data.get("actions"))
        targets = _event_targets(call, call.data.get("device_id"))
    except ValueError as err:
        raise ServiceValidationError(str(err)) from None
    if call.hass.states.get(entity_id) is None:
        raise ServiceValidationError("That camera is not in Home Assistant.")
    for device_id in targets:
        issued = ledger(call.hass).issue(device_id, actions, duration)
        call.hass.bus.async_fire(
            EVENT_CAMERA_POPUP,
            {
                "entity_id": entity_id,
                "title": title,
                "show_header": show_header,
                "image_fit": fit,
                "refresh_seconds": refresh,
                "duration_seconds": duration,
                "device_id": device_id,
                **({"speak": speak} if speak else {}),
                **({"actions": actions, "announcement_id": issued} if issued else {}),
            },
        )
    _LOGGER.debug("Fired %s for %s configured TV(s)", EVENT_CAMERA_POPUP, len(targets))



async def _async_show_live_camera(call: ServiceCall) -> None:
    """Request a live HLS session on a configured TV without sharing stream URLs."""
    try:
        entity_id = clean_camera_entity(call.data.get("entity_id"))
        show_header = call.data.get("show_header", True)
        title = (clean_title(call.data.get("title", "Live camera"))
                 if show_header else "")
        fit = call.data.get("image_fit", "crop")
        if fit not in ("crop", "fit"):
            raise ValueError("Choose image_fit 'crop' or 'fit'.")
        refresh = clean_snapshot_refresh(call.data.get("refresh_seconds", 0))
        duration = int(call.data.get("duration_seconds", DEFAULT_LIVE_SECONDS))
        if not MIN_LIVE_SECONDS <= duration <= MAX_LIVE_SECONDS:
            raise ValueError("Live duration must be from 4 to 180 seconds.")
        speak = clean_speak(call.data.get("speak"))
        actions = clean_actions(call.data.get("actions"))
        targets = _event_targets(call, call.data.get("device_id"))
    except (ValueError, TypeError) as err:
        raise ServiceValidationError(str(err)) from None
    camera = call.hass.states.get(entity_id)
    if camera is None:
        raise ServiceValidationError("That camera is not in Home Assistant.")
    # CameraEntityFeature.STREAM == 2; reject still-only camera entities.
    if not int(camera.attributes.get("supported_features", 0)) & 2:
        raise ServiceValidationError("This camera does not support live streams in Home Assistant.")
    for device_id in targets:
        issued = ledger(call.hass).issue(device_id, actions, duration)
        call.hass.bus.async_fire(
            EVENT_LIVE_CAMERA,
            {
                "entity_id": entity_id,
                "title": title,
                "show_header": show_header,
                "image_fit": fit,
                "refresh_seconds": refresh,
                "duration_seconds": duration,
                "device_id": device_id,
                **({"speak": speak} if speak else {}),
                **({"actions": actions, "announcement_id": issued} if issued else {}),
            },
        )
    _LOGGER.debug("Fired %s for %s configured TV(s)", EVENT_LIVE_CAMERA, len(targets))


async def _async_hide_camera(call: ServiceCall) -> None:
    """Close the current camera (snapshot or live) on a configured TV.

    No camera URL, credentials or arbitrary entity ids are accepted here.
    Announcements and the separate voice overlay are unaffected.
    """
    try:
        targets = _event_targets(call, call.data.get("device_id"))
    except ValueError as err:
        raise ServiceValidationError(str(err)) from None
    for device_id in targets:
        ledger(call.hass).clear(device_id)
        call.hass.bus.async_fire(EVENT_HIDE_CAMERA, {"device_id": device_id})
    _LOGGER.debug("Fired %s for %s configured TV(s)", EVENT_HIDE_CAMERA, len(targets))


def _saved_weather_entity(hass: HomeAssistant, device_id: str):
    """The forecast-source select for one TV, if the owner picked a weather entity."""
    for state in hass.states.async_all("select"):
        attributes = state.attributes
        if attributes.get("kellie_setting") != "weather_source":
            continue
        if device_id and attributes.get("kellie_device_id") != device_id:
            continue
        if isinstance(state.state, str) and state.state.startswith("weather."):
            return state.state
    return None


async def _async_show_forecast(call: ServiceCall) -> None:
    """Fetch a daily forecast and send only the cleaned days to the TV.

    Nothing here runs on a schedule. A voice script or the Actions page calls it.
    """
    try:
        requested = call.data.get("entity_id")
        device_filter = call.data.get("device_id")
        targets = _event_targets(call, device_filter)
        if not requested:
            requested = _saved_weather_entity(
                call.hass, targets[0] if len(targets) == 1 else ""
            )
        entity_id = clean_weather_entity(requested)
        duration = (clean_forecast_duration(call.data["duration_seconds"])
                    if "duration_seconds" in call.data else None)
    except ValueError as err:
        raise ServiceValidationError(str(err)) from None
    state = call.hass.states.get(entity_id)
    if state is None:
        raise ServiceValidationError("That weather entity is not in Home Assistant.")
    try:
        features = int(state.attributes.get("supported_features", 0) or 0)
    except (TypeError, ValueError):
        features = 0
    if not features & 1:
        raise ServiceValidationError(
            "This weather entity does not provide a daily forecast."
        )
    try:
        result = await call.hass.services.async_call(
            "weather",
            "get_forecasts",
            {"entity_id": entity_id, "type": "daily"},
            blocking=True,
            return_response=True,
        )
    except Exception:
        raise ServiceValidationError(
            "Home Assistant could not read that daily forecast."
        ) from None
    try:
        days = clean_forecast_days(
            forecast_rows(result, entity_id),
            getattr(call.hass.config, "time_zone", None) or "UTC",
        )
    except ValueError as err:
        raise ServiceValidationError(str(err)) from None
    place = clean_forecast_place(state.attributes.get("friendly_name") or state.name)
    unit = clean_forecast_unit(state.attributes.get("temperature_unit"))
    for device_id in targets:
        payload = {
            "location": place,
            "units": unit,
            "issued": int(time.time()),
            "days": days,
            "device_id": device_id,
        }
        if duration is not None:
            payload["duration_seconds"] = duration
        call.hass.bus.async_fire(EVENT_FORECAST, payload)
    _LOGGER.debug("Fired %s with %s day(s)", EVENT_FORECAST, len(days))
