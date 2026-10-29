"""Discovery flow. Home Assistant does not connect to the TV."""

import logging
import time

import voluptuous as vol
from homeassistant.config_entries import ConfigFlow, OptionsFlow
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .const import DOMAIN
from .validation import clean_tv_device_id
from .pairing_probe import candidate_from_discovery
from .pairing_exchange import begin_preview, confirm_tv_ok
from .pairing_owner_oauth import OwnerAuthorizationAttempt
from .oauth_callback import register_pending_authorization, consume_pending_authorization
from .pairing_oauth_pending import PendingOAuthTransaction

_LOGGER = logging.getLogger(__name__)
_PROVISIONAL_TTL = 120


def _text(properties: dict, key: str) -> str:
    value = properties.get(key)
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace").strip()
    if value is None:
        return ""
    return str(value).strip()


class KellieVoiceOptionsFlow(OptionsFlow):
    """Explicit per-TV opt-in; HA reloads the integration on change."""

    async def async_step_init(self, user_input=None):
        if user_input is not None:
            return self.async_create_entry(
                title="", data={"native_timer_intents": bool(
                    user_input["native_timer_intents"]
                )}
            )
        return self.async_show_form(
            step_id="init",
            data_schema=vol.Schema({
                vol.Optional(
                    "native_timer_intents",
                    default=self.config_entry.options.get("native_timer_intents", False)
                ): bool,
            }),
        )


class KellieVoiceConfigFlow(ConfigFlow, domain=DOMAIN):
    """Add a TV that is already advertising from its Settings switch."""

    @staticmethod
    def async_get_options_flow(config_entry):
        return KellieVoiceOptionsFlow()

    def __init__(self) -> None:
        self._device_id = ""
        self._name = "Kellie's Voice"
        self._manual_device_id = ""
        self._manual_name = ""
        self._preview_candidate = None
        self._preview_attempt = None
        self._oauth_attempt = None
        self._oauth_state = None
        self._oauth_pending = None
        self._provisional_tv_uuid = None
        self._provisional_owner = None

    async def async_step_user(self, user_input=None):
        """Manual fallback when the TV cannot advertise across mDNS/VLANs."""
        errors = {}
        if user_input is not None:
            try:
                device_id = clean_tv_device_id(user_input.get("device_id"))
            except ValueError:
                errors["device_id"] = "invalid_device_id"
            else:
                name = str(user_input.get("name") or "Kellie's Voice TV").strip()
                if not name or len(name) > 64:
                    errors["name"] = "invalid_name"
                else:
                    self._manual_device_id = device_id
                    self._manual_name = name
                    return await self.async_step_manual_confirm()
        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(
                {
                    vol.Required("device_id"): str,
                    vol.Optional("name", default="Kellie's Voice TV"): str,
                }
            ),
            errors=errors,
        )

    async def async_step_manual_confirm(self, user_input=None):
        if not self._manual_device_id:
            return await self.async_step_user()
        errors = {}
        if user_input is not None:
            suffix = str(user_input.get("confirmation_suffix") or "").strip().lower()
            if suffix != self._manual_device_id[-4:]:
                errors["confirmation_suffix"] = "pairing_suffix_mismatch"
            else:
                await self.async_set_unique_id(self._manual_device_id)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(
                    title=self._manual_name,
                    data={"device_id": self._manual_device_id, "name": self._manual_name},
                )
        return self.async_show_form(
            step_id="manual_confirm",
            data_schema=vol.Schema({vol.Required("confirmation_suffix"): str}),
            description_placeholders={
                "name": self._manual_name,
                "pairing_id": self._manual_device_id,
            },
            errors=errors,
        )

    async def async_step_zeroconf(self, discovery_info: ZeroconfServiceInfo):
        if _text(discovery_info.properties, "preview") == "2":
            try:
                self._preview_candidate = candidate_from_discovery(
                    str(discovery_info.ip_address), discovery_info.port,
                    discovery_info.properties,
                )
            except (ValueError, AttributeError, TypeError):
                return self.async_abort(reason="invalid_pairing_preview")
            await self.async_set_unique_id(
                "preview:" + self._preview_candidate.session
            )
            self._abort_if_unique_id_configured()
            self.context["title_placeholders"] = {"name": "Kellie's Voice setup"}
            return await self.async_step_pairing_preview()

        device_id = _text(discovery_info.properties, "id")
        if not device_id:
            return self.async_abort(reason="no_id")
        await self.async_set_unique_id(device_id)
        self._abort_if_unique_id_configured()
        name = _text(discovery_info.properties, "name") or "Kellie's Voice"
        self._device_id = device_id
        self._name = name
        self.context["title_placeholders"] = {"name": name}
        return await self.async_step_confirm()

    async def async_step_pairing_preview(self, user_input=None):
        if self._preview_candidate is None:
            return self.async_abort(reason="invalid_pairing_preview")
        errors = {}
        if user_input is not None:
            if user_input.get("start") is True:
                try:
                    self._preview_attempt = await begin_preview(self._preview_candidate)
                except Exception:
                    errors["base"] = "preview_unavailable"
                else:
                    return await self.async_step_pairing_code()
        return self.async_show_form(
            step_id="pairing_preview",
            data_schema=vol.Schema({vol.Required("start"): bool}),
            errors=errors,
        )

    async def async_step_pairing_code(self, user_input=None):
        attempt = self._preview_attempt
        if attempt is None:
            return self.async_abort(reason="invalid_pairing_preview")
        errors = {}
        if user_input is not None:
            if user_input.get("numbers_match") is not True:
                return self.async_abort(reason="preview_cancelled")
            try:
                approved = await confirm_tv_ok(attempt)
            except Exception:
                errors["base"] = "preview_unavailable"
            else:
                if approved:
                    self._preview_attempt = None
                    return await self.async_step_oauth_authorize()
                errors["base"] = "waiting_for_tv"
        return self.async_show_form(
            step_id="pairing_code",
            description_placeholders={"code": attempt.code},
            data_schema=vol.Schema({vol.Required("numbers_match"): bool}),
            errors=errors,
        )

    async def async_step_oauth_authorize(self, user_input=None):
        """Start owner-approved OAuth after TV OK proof (#213).

        First call shows the consent form. Second call (from the form) creates
        the CSPRNG state and returns an external step. The callback later
        resumes via async_step_oauth_resume.
        """
        if user_input is None:
            return self.async_show_form(
                step_id="oauth_authorize",
                data_schema=vol.Schema({
                    vol.Required("confirm_authorize"): bool,
                }),
                description_placeholders={
                    "name": self._name or "Kellie's Voice",
                },
            )

        # Callback resume arrives with oauth_state / oauth_code keys.
        if "oauth_state" in user_input or "oauth_code" in user_input:
            return await self.async_step_oauth_resume(user_input)

        if user_input.get("confirm_authorize") is not True:
            return self.async_abort(reason="oauth_cancelled")

        owner_user_id = self.context.get("user_id")
        if not isinstance(owner_user_id, str) or not owner_user_id:
            return self.async_abort(reason="oauth_owner_required")

        try:
            base_url = getattr(self.hass.config.api, "base_url", None)
            if not isinstance(base_url, str) or not base_url:
                return self.async_abort(reason="oauth_origin_unavailable")
            tv_session = getattr(self._preview_candidate, "session", None)
            if not isinstance(tv_session, str) or not tv_session:
                return self.async_abort(reason="oauth_tv_session_required")
            attempt = OwnerAuthorizationAttempt.begin(
                base_url,
                tv_session,
                time.time(),
                owner_requested=True,
                tv_ok_verified=True,
            )
        except Exception:
            _LOGGER.debug("Failed to begin owner authorization", exc_info=False)
            return self.async_abort(reason="oauth_unavailable")

        self._oauth_attempt = attempt
        auth_url = attempt.authorization_url()
        self._oauth_state = auth_url.split("state=")[-1].split("&")[0]

        register_pending_authorization(
            self._oauth_state,
            flow_id=self.flow_id,
            owner_user_id=owner_user_id,
            tv_session=tv_session,
            attempt=attempt,
        )

        return self.async_external_step(
            step_id="oauth_authorize",
            url=auth_url,
        )

    async def async_step_oauth_resume(self, user_input=None):
        """Resume after callback. Single consume, then hand to pending txn.

        Token exchange is prepared but the flow still aborts without creating
        a config entry or sending credentials to the TV.
        """
        if not user_input:
            return self.async_abort(reason="oauth_incomplete")

        attempt = user_input.get("attempt") or self._oauth_attempt
        if attempt is None:
            return self.async_abort(reason="oauth_incomplete")

        bound_owner = user_input.get("bound_owner")
        bound_tv = user_input.get("bound_tv")
        actual_owner = self.context.get("user_id")
        actual_tv = getattr(self._preview_candidate, "session", None)

        if (
            not isinstance(actual_owner, str) or not actual_owner
            or bound_owner != actual_owner
            or bound_tv != actual_tv
        ):
            if hasattr(attempt, "cancel"):
                attempt.cancel()
            return self.async_abort(reason="oauth_owner_mismatch")

        try:
            consumed = attempt.consume_callback(
                received_state=user_input.get("oauth_state"),
                code=user_input.get("oauth_code"),
                tv_session=bound_tv,
                now=time.time(),
            )
        except Exception:
            return self.async_abort(reason="oauth_callback_rejected")

        # Single consume done. Hold in a pending transaction that will be
        # aborted (and any future refresh revoked) until AEAD provisioning
        # is ready. Do not create an entry or mark Connected.
        try:
            self._oauth_pending = PendingOAuthTransaction(
                owner_user_id=actual_owner,
                tv_session=bound_tv,
                consumed=consumed,
                started=time.time(),
            )
        except Exception:
            return self.async_abort(reason="oauth_pending_failed")

        return self.async_external_step_done(next_step_id="oauth_exchange_pending")

    async def async_step_oauth_exchange_pending(self, user_input=None):
        """Placeholder until AEAD provisioning + Assist proof are wired.

        Aborts cleanly so no incomplete credential or config entry is created.
        Any pending transaction is cleared.
        """
        if self._oauth_pending is not None:
            await self._oauth_pending.abort_and_revoke(None)
            self._oauth_pending = None
        return self.async_abort(reason="oauth_exchange_pending")

    async def async_step_provisional_create(self, user_input=None):
        """Supported config-flow path for a short-lived provisional entry.

        Owner and TV identity come from server-side flow state, never from
        user_input. Requires an authenticated owner and a previously verified
        TV session on this flow instance.
        """
        owner_user_id = self.context.get("user_id")
        if not isinstance(owner_user_id, str) or not owner_user_id or owner_user_id == "unknown":
            return self.async_abort(reason="provisional_owner_required")
        tv_uuid = getattr(self._preview_candidate, "session", None) or self._provisional_tv_uuid
        if not isinstance(tv_uuid, str) or not tv_uuid:
            return self.async_abort(reason="provisional_tv_required")
        # Refuse if any entry already exists for this TV.
        for entry in self.hass.config_entries.async_entries(DOMAIN):
            if entry.data.get("device_id") == tv_uuid:
                return self.async_abort(reason="provisional_exists")
        self._provisional_owner = owner_user_id
        self._provisional_tv_uuid = tv_uuid
        return self.async_create_entry(
            title="Kellie's Voice (provisional)",
            data={
                "device_id": tv_uuid,
                "name": "Kellie's Voice (provisional)",
                "provisional": True,
                "owner_user_id": owner_user_id,
                "started": time.time(),
            },
        )

    async def async_step_confirm(self, user_input=None):
        if user_input is not None:
            return self.async_create_entry(
                title=self._name,
                data={"device_id": self._device_id, "name": self._name},
            )
        return self.async_show_form(
            step_id="confirm",
            description_placeholders={"name": self._name},
        )
