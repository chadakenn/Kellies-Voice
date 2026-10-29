"""Isolated two-screen verification only. Never creates a HA config entry."""
import voluptuous as vol
from homeassistant.config_entries import ConfigFlow
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .pairing_key_commitment import _session_bytes
from .pairing_v3_wire import HaV3WireAttempt, AiohttpV3LanTransport

DOMAIN = "kellies_voice_v3qa"


def _text(properties: dict, key: str) -> str:
    value = properties.get(key)
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace").strip()
    if value is None:
        return ""
    return str(value).strip()


class KelliesVoiceV3QaConfigFlow(ConfigFlow, domain=DOMAIN):
    """Ephemeral v3-only mDNS code and physical-remote proof, no login."""

    def __init__(self):
        self._session = None
        self._transport = None
        self._attempt = None
        self._code = None

    async def async_step_user(self, user_input=None):
        # A manual Add Integration cannot establish a trusted ephemeral TV.
        return self.async_abort(reason="discovery_only")

    async def async_step_zeroconf(self, discovery_info: ZeroconfServiceInfo):
        props = discovery_info.properties
        if (_text(props, "protocol") != "3"
                or _text(props, "mode") != "verified-code-only"):
            return self.async_abort(reason="invalid_discovery")
        try:
            session = _text(props, "session")
            _session_bytes(session)
            # Bind transport to the discovered literal private TV address/port.
            transport = AiohttpV3LanTransport(
                str(discovery_info.ip_address), discovery_info.port
            )
        except (TypeError, AttributeError, ValueError):
            return self.async_abort(reason="invalid_discovery")
        self._session = session
        self._transport = transport
        await self.async_set_unique_id("v3-qa:" + session)
        self._abort_if_unique_id_configured()
        self.context["title_placeholders"] = {
            "name": "Kellie's Voice v3 SAFE TEST"
        }
        return await self.async_step_v3_preview()

    async def async_step_v3_preview(self, user_input=None):
        if self._session is None or self._transport is None:
            return self.async_abort(reason="invalid_discovery")
        errors = {}
        if user_input is not None:
            if user_input.get("start_test") is not True:
                return self.async_abort(reason="cancelled")
            attempt = HaV3WireAttempt(self._session)
            try:
                code = await attempt.start(self._transport)
            except Exception:
                attempt.close()
                errors["base"] = "unavailable"
            else:
                self._attempt = attempt
                self._code = code
                return await self.async_step_v3_code()
        return self.async_show_form(
            step_id="v3_preview",
            data_schema=vol.Schema({vol.Required("start_test"): bool}),
            errors=errors,
        )

    async def async_step_v3_code(self, user_input=None):
        if self._attempt is None or self._code is None:
            return self.async_abort(reason="expired")
        errors = {}
        if user_input is not None:
            if user_input.get("numbers_match") is not True:
                self._attempt.close()
                self._attempt = None
                self._code = None
                return self.async_abort(reason="cancelled")
            if user_input.get("ha_code") != self._code:
                self._attempt.close()
                self._attempt = None
                self._code = None
                return self.async_abort(reason="mismatch")
            try:
                result = await self._attempt.confirmed_by_tv_remote(self._transport)
            except Exception:
                self._attempt = None
                self._code = None
                return self.async_abort(reason="expired")
            if result is None:
                errors["base"] = "waiting_for_tv"
            else:
                self._attempt = None
                self._code = None
                # Verified MAC proves remote OK, NOT Home Assistant login.
                # Intentionally no async_create_entry, OAuth, storage or tokens.
                return self.async_abort(reason="verified_no_login")
        return self.async_show_form(
            step_id="v3_code",
            description_placeholders={"code": self._code},
            data_schema=vol.Schema({
                vol.Required("ha_code", default=self._code): str,
                vol.Required("numbers_match"): bool,
            }),
            errors=errors,
        )
