"""Standalone, v3-only TV code verification, independent of production.

This integration NEVER installs an HA TV user, grants an owner credential,
issues tokens, changes other integrations or creates a persistent entry.
It is exclusively an attended, two-screen, token-free v3 QA flow.
"""
from __future__ import annotations

import logging

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .const import DOMAIN
from .pairing_key_commitment import _session_bytes
from .pairing_v3_wire import AiohttpV3LanTransport, HaV3WireAttempt

_LOGGER = logging.getLogger(__name__)


def _txt(props: dict, name: str) -> str | None:
    value = props.get(name)
    if isinstance(value, bytes):
        try:
            value = value.decode("ascii")
        except UnicodeDecodeError:
            return None
    return value if type(value) is str else None


class KelliesVoiceV3QaConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """QA only. Never create a config entry or install login credentials."""

    VERSION = 1

    def __init__(self):
        self._session: str | None = None
        self._transport: AiohttpV3LanTransport | None = None
        self._attempt: HaV3WireAttempt | None = None
        self._code: str | None = None

    async def async_step_user(self, user_input=None):
        """No manual IP, username, URL, or token input is ever accepted."""
        return self.async_abort(reason="start_on_tv")

    async def async_step_zeroconf(self, discovery_info: ZeroconfServiceInfo):
        """Accept only the short-lived token-free TV protocol v3 service."""
        props = discovery_info.properties or {}
        if (_txt(props, "protocol") != "3"
                or _txt(props, "mode") != "verified-code-only"):
            return self.async_abort(reason="invalid_tv")
        try:
            session = _txt(props, "session")
            _session_bytes(session)
            transport = AiohttpV3LanTransport(
                str(discovery_info.ip_address),
                discovery_info.port,
            )
        except (ValueError, TypeError, AttributeError):
            return self.async_abort(reason="invalid_tv")

        self._session = session
        self._transport = transport
        await self.async_set_unique_id("kv-v3-qa:" + session)
        self._abort_if_unique_id_configured()
        self.context["title_placeholders"] = {
            "name": "Kellie's Voice v3 code test",
        }
        return await self.async_step_compare_start()

    async def async_step_compare_start(self, user_input=None):
        if self._transport is None or self._session is None:
            return self.async_abort(reason="invalid_tv")
        errors = {}
        if user_input is not None:
            if user_input.get("start") is not True:
                return self.async_abort(reason="cancelled")
            attempt = HaV3WireAttempt(self._session)
            try:
                code = await attempt.start(self._transport)
            except Exception:
                attempt.close()
                _LOGGER.warning("TV v3 QA key exchange failed; no login attempted")
                errors["base"] = "unavailable"
            else:
                self._attempt = attempt
                self._code = code
                return await self.async_step_compare_code()
        return self.async_show_form(
            step_id="compare_start",
            data_schema=vol.Schema({vol.Required("start"): bool}),
            errors=errors,
        )

    async def async_step_compare_code(self, user_input=None):
        if self._attempt is None or self._code is None or self._transport is None:
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
                checked = await self._attempt.confirmed_by_tv_remote(
                    self._transport,
                )
            except Exception:
                self._attempt = None
                self._code = None
                return self.async_abort(reason="expired")
            if checked is None:
                errors["base"] = "waiting_for_remote"
            else:
                self._attempt = None
                self._code = None
                return self.async_abort(reason="verified_test_only")
        return self.async_show_form(
            step_id="compare_code",
            description_placeholders={"code": self._code},
            data_schema=vol.Schema({
                vol.Required("ha_code", default=self._code): str,
                vol.Required("numbers_match"): bool,
            }),
            errors=errors,
        )
