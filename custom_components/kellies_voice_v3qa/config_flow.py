"""Isolated two-screen verification only. Never creates a HA config entry."""
import logging
import time
import voluptuous as vol
from homeassistant.config_entries import ConfigFlow
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .pairing_key_commitment import _session_bytes
from .pairing_v3_wire import HaV3WireAttempt, AiohttpV3LanTransport
from .owner_claim import owner_claims
from . import owner_controls_ready

DOMAIN = "kellies_voice_v3qa"
_LOGGER = logging.getLogger(__name__)
_OWNER_QA_NOTICE = (
    "V3 OWNER TEST ONLY. Keep this pairing dialog open. In a SECOND "
    "Home Assistant browser tab (signed in as the HA owner), open "
    "/kellies-voice-v3qa-approval. Compare the exact eight-digit TV "
    "code, click Approve this TV, then return here and check "
    "owner_approved. This does NOT sign the TV in or issue any token."
)


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
        self._owner_tv = None
        self._owner_code = None
        self._owner_deadline = 0

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
                # The independent v3 ECDH+UUID-bound physical-TV MAC is
                # verified BEFORE an HA owner claim can be registered.
                verified_code = self._code
                self._attempt = None
                self._code = None
                # Never attempt registration of a static HTTP route or
                # frontend panel after Home Assistant has started serving.
                # The QA owner UI must be registered during YAML startup.
                if not owner_controls_ready(self.hass):
                    _LOGGER.warning(
                        "V3 QA owner controls were not initialized at HA startup"
                    )
                    return self.async_abort(reason="owner_setup_missing")
                try:
                    owner_claims(self.hass).begin(
                        flow_id=self.flow_id, session=result.session,
                        tv_uuid=result.tv_uuid, comparison_code=verified_code)
                except ValueError:
                    _LOGGER.exception("V3 QA verified owner claim rejected")
                    owner_claims(self.hass).cancel(self.flow_id)
                    return self.async_abort(reason="owner_binding_rejected")
                except Exception:
                    _LOGGER.exception("V3 QA verified owner claim failed unexpectedly")
                    owner_claims(self.hass).cancel(self.flow_id)
                    return self.async_abort(reason="owner_unavailable")
                self._owner_tv = result
                self._owner_code = verified_code
                self._owner_deadline = time.monotonic() + 120
                return await self.async_step_owner_approval()
        return self.async_show_form(
            step_id="v3_code",
            description_placeholders={"code": self._code},
            data_schema=vol.Schema({
                vol.Required("ha_code", default=self._code): str,
                vol.Required("numbers_match"): bool,
            }),
            errors=errors,
        )

    async def async_step_owner_approval(self, user_input=None):
        """Require an independently authenticated HA owner after TV proof.

        No owner ID is accepted from a config-flow form, TV, or mDNS.
        This form NEVER creates a config entry or sends a token.
        """
        tv = self._owner_tv
        if (tv is None or time.monotonic() >= self._owner_deadline):
            if tv is not None:
                owner_claims(self.hass).cancel(self.flow_id)
            self._owner_tv = None
            self._owner_code = None
            return self.async_abort(reason="owner_expired")
        errors = {}
        if user_input is not None:
            if user_input.get("owner_approved") is not True:
                owner_claims(self.hass).cancel(self.flow_id)
                self._owner_tv = None
                self._owner_code = None
                return self.async_abort(reason="cancelled")
            if user_input.get("qa_instructions") != _OWNER_QA_NOTICE:
                owner_claims(self.hass).cancel(self.flow_id)
                self._owner_tv = None
                self._owner_code = None
                return self.async_abort(reason="owner_denied")
            if user_input.get("ha_code") != self._owner_code:
                owner_claims(self.hass).cancel(self.flow_id)
                self._owner_tv = None
                self._owner_code = None
                return self.async_abort(reason="mismatch")
            owner_id = owner_claims(self.hass).consume(
                flow_id=self.flow_id, session=tv.session, tv_uuid=tv.tv_uuid)
            if owner_id is None:
                errors["base"] = "owner_pending"
            else:
                try:
                    person = await self.hass.auth.async_get_user(owner_id)
                except Exception:
                    person = None
                if (person is None
                        or getattr(person, "id", None) != owner_id
                        or getattr(person, "is_owner", False) is not True
                        or getattr(person, "is_active", False) is not True):
                    self._owner_tv = None
                    return self.async_abort(reason="owner_denied")
                self._owner_tv = None
                self._owner_code = None
                # A real owner approved this exact proof. Still NO LOGIN.
                return self.async_abort(reason="owner_verified_no_login")
        return self.async_show_form(
            step_id="owner_approval",
            description_placeholders={"code": self._owner_code},
            data_schema=vol.Schema({
                vol.Required("qa_instructions", default=_OWNER_QA_NOTICE): str,
                vol.Required("ha_code", default=self._owner_code): str,
                vol.Required("owner_approved"): bool,
            }),
            errors=errors,
        )
