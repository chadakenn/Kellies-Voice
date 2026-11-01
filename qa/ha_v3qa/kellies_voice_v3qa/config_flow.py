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
            attempt = HaV3WireAttempt(
                self._session, retain_owner_receipt=True)
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
                self._code = None
                # Keep the verified ECDH state ONLY for the bounded owner
                # acknowledgement path, never for any HA credential.
                # Never attempt registration of a static HTTP route or
                # frontend panel after Home Assistant has started serving.
                # The QA owner UI must be registered during YAML startup.
                if not owner_controls_ready(self.hass):
                    self._attempt.close()
                    self._attempt = None
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
                    self._attempt.close()
                    self._attempt = None
                    return self.async_abort(reason="owner_binding_rejected")
                except Exception:
                    _LOGGER.exception("V3 QA verified owner claim failed unexpectedly")
                    owner_claims(self.hass).cancel(self.flow_id)
                    self._attempt.close()
                    self._attempt = None
                    return self.async_abort(reason="owner_unavailable")
                self._owner_tv = result
                self._owner_code = verified_code
                # The original TV listening window, not a new 120s after
                # physical OK, is authoritative for encryption-key lifetime.
                self._owner_deadline = self._attempt._expires_at
                return await self.async_step_owner_approval()
        return self.async_show_form(
            step_id="v3_code",
            description_placeholders={"code": self._code},
            data_schema=vol.Schema({
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
            if self._attempt is not None:
                self._attempt.close()
                self._attempt = None
            self._owner_tv = None
            self._owner_code = None
            return self.async_abort(reason="owner_expired")
        errors = {}
        if user_input is not None:
            registry = owner_claims(self.hass)
            if not registry.approved_for(
                    flow_id=self.flow_id, session=tv.session,
                    tv_uuid=tv.tv_uuid):
                errors["base"] = "owner_pending"
            else:
                attempt = self._attempt
                self._attempt = None
                self._owner_tv = None
                self._owner_code = None
                if attempt is None:
                    registry.cancel(self.flow_id)
                    return self.async_abort(reason="owner_receipt_failed")
                # Explicit HA-owner opt-in only. No blanket TV account for
                # users who approved the existing token-free test.
                owner_id = registry.approved_owner_login(
                    flow_id=self.flow_id, session=tv.session,
                    tv_uuid=tv.tv_uuid)
                origin = None
                remote_login = False
                if owner_id is not None:
                    from .ha_https import resolve_tv_login_origin
                    try:
                        origin, remote_login = resolve_tv_login_origin(
                            self.hass)
                    except Exception:
                        # No usable HTTPS origin means no renewable token.
                        registry.cancel(self.flow_id)
                        attempt.close()
                        return self.async_abort(reason="https_required")
                try:
                    await attempt.finish_owner_receipt(
                        self._transport, hass=self.hass,
                        claims=registry, flow_id=self.flow_id,
                        **({"retain_for_login": True} if owner_id else {}))
                    if owner_id:
                        journal = self.hass.data[DOMAIN]["token_journal"]
                        await attempt.finish_renewable_login(
                            self._transport, hass=self.hass, journal=journal,
                            owner_id=owner_id, ha_origin=origin,
                            remote_login=remote_login)
                except Exception:
                    registry.cancel(self.flow_id)
                    attempt.close()
                    _LOGGER.exception(
                        "V3 owner approval or TV login failed during %s",
                        "renewable credential activation" if owner_id
                        else "owner receipt acknowledgement",
                    )
                    return self.async_abort(reason="owner_login_failed"
                                            if owner_id else "owner_receipt_failed")
                return self.async_abort(
                    reason="owner_login_connected" if owner_id
                    else "owner_receipt_verified_no_login")
        return self.async_show_form(
            step_id="owner_approval",
            description_placeholders={"code": self._owner_code},
            # A click on Continue never authorizes anything by itself.
            # The actual approving HA owner must already have claimed this
            # exact verified session through the authenticated WS panel.
            # Avoid a second code field and redundant approval checkbox.
            data_schema=vol.Schema({}),
            errors=errors,
        )
