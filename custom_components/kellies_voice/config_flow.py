"""Discovery flow. Home Assistant does not connect to the TV."""

import voluptuous as vol
from homeassistant.config_entries import ConfigFlow, OptionsFlow
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .const import DOMAIN, QUICK_ACTION_CACHE
from .validation import clean_tv_device_id
from .home_shortcuts import COUNT, ACTIONS, validate_slot
from .pairing_v3_wire import HaV3WireAttempt, AiohttpV3LanTransport
from .pairing_key_commitment import _session_bytes


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
        """Configure this TV's opt-in main-screen shortcuts in HA.

        Never accept client-supplied service calls. Empty slots show no tile.
        An invalid slot rejects the whole save, preserving previous options.
        """
        current = dict(self.config_entry.options)
        errors = {}
        if user_input is not None:
            updated = dict(current)
            updated["native_timer_intents"] = bool(user_input["native_timer_intents"])
            for slot in range(1, COUNT + 1):
                label = user_input.get(f"shortcut_{slot}_label",
                                       current.get(f"shortcut_{slot}_label", ""))
                entity = user_input.get(f"shortcut_{slot}_entity",
                                        current.get(f"shortcut_{slot}_entity", ""))
                action = user_input.get(f"shortcut_{slot}_action",
                                        current.get(f"shortcut_{slot}_action", "Turn on"))
                try:
                    validate_slot(label, entity, action)
                except ValueError:
                    errors["base"] = "invalid_shortcut"
                    break
                updated.update({
                    f"shortcut_{slot}_label": label.strip(),
                    f"shortcut_{slot}_entity": entity.strip(),
                    f"shortcut_{slot}_action": action,
                })
            if not errors:
                # The native device-page fields share this same per-TV draft.
                # An explicit Options form submission replaces it atomically.
                hass = getattr(self, "hass", None)
                if hass is not None:
                    hass.data.setdefault(DOMAIN, {}).setdefault(
                        QUICK_ACTION_CACHE, {}
                    )[self.config_entry.entry_id] = dict(updated)
                return self.async_create_entry(title="", data=updated)

        fields = {
            vol.Optional(
                "native_timer_intents",
                default=current.get("native_timer_intents", False)
            ): bool,
        }
        for slot in range(1, COUNT + 1):
            for field, default, value_type in (
                ("label", "", str), ("entity", "", str),
                ("action", "Turn on", vol.In(ACTIONS)),
            ):
                key = f"shortcut_{slot}_{field}"
                fields[vol.Optional(
                    key, default=current.get(key, default)
                )] = value_type
        return self.async_show_form(
            step_id="init", data_schema=vol.Schema(fields), errors=errors,
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
        # v3 ephemeral pairing is deliberately separate from the stable
        # HA TV config entry and existing manual-token path.
        self._v3_session = None
        self._v3_transport = None
        self._v3_attempt = None
        self._v3_code = None

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
                    # Manual pairing cannot verify that a TV exists.
                    # Display the full ID and verify a freshly typed TV suffix.
                    # This is a typo guard, not authentication of the TV.
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
        """Warn when a typed pairing ID cannot be verified by discovery.

        Offline/VLAN users may still confirm. A live socket cannot be required
        because the TV might not be registered in Home Assistant yet.
        """
        if not self._manual_device_id:
            return await self.async_step_user()
        errors = {}
        if user_input is not None:
            # Copy this suffix from the pairing ID shown *on the TV*, not
            # from the previously submitted HA text field. It catches the
            # common accidental confirmation click without claiming that HA
            # has authenticated or contacted an offline/VLAN-isolated TV.
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
        # Experimental v3 ephemeral discovery advertises protocol=3 and
        # NO permanent TV UUID, HA URL, bearer token or owner identity.
        # It cannot fall back to legacy v1/v2 or create a config entry.
        if _text(discovery_info.properties, "protocol") == "3":
            if _text(discovery_info.properties, "mode") != "verified-code-only":
                return self.async_abort(reason="v3_invalid_discovery")
            try:
                session = _text(discovery_info.properties, "session")
                _session_bytes(session)
                transport = AiohttpV3LanTransport(
                    str(discovery_info.ip_address), discovery_info.port
                )
            except (TypeError, AttributeError, ValueError):
                return self.async_abort(reason="v3_invalid_discovery")
            self._v3_session = session
            self._v3_transport = transport
            await self.async_set_unique_id("v3-preview:" + session)
            self._abort_if_unique_id_configured()
            self.context["title_placeholders"] = {
                "name": "Kellie's Voice secure pairing v3 TEST"
            }
            return await self.async_step_v3_preview()

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

    async def async_step_v3_preview(self, user_input=None):
        """Run real v3 LAN key exchange only after a human opts in.

        No tokens, account authorization, persisted device, or HA entry.
        """
        if self._v3_session is None or self._v3_transport is None:
            return self.async_abort(reason="v3_invalid_discovery")
        errors = {}
        if user_input is not None:
            if user_input.get("start_v3_test") is not True:
                return self.async_abort(reason="v3_cancelled")
            attempt = HaV3WireAttempt(self._v3_session)
            try:
                code = await attempt.start(self._v3_transport)
            except Exception:
                attempt.close()
                errors["base"] = "v3_unavailable"
            else:
                self._v3_attempt = attempt
                self._v3_code = code
                return await self.async_step_v3_code()
        return self.async_show_form(
            step_id="v3_preview",
            data_schema=vol.Schema({vol.Required("start_v3_test"): bool}),
            errors=errors,
        )

    async def async_step_v3_code(self, user_input=None):
        """Visible code and physical remote-proof check, preview ONLY."""
        if self._v3_attempt is None or self._v3_code is None:
            return self.async_abort(reason="v3_expired")
        errors = {}
        if user_input is not None:
            if user_input.get("numbers_match") is not True:
                self._v3_attempt.close()
                self._v3_attempt = None
                return self.async_abort(reason="v3_cancelled")
            if user_input.get("ha_code") != self._v3_code:
                self._v3_attempt.close()
                self._v3_attempt = None
                return self.async_abort(reason="v3_code_mismatch")
            try:
                result = await self._v3_attempt.confirmed_by_tv_remote(
                    self._v3_transport)
            except Exception:
                self._v3_attempt = None
                return self.async_abort(reason="v3_expired")
            if result is None:
                errors["base"] = "v3_waiting_for_tv"
            else:
                self._v3_attempt = None
                self._v3_code = None
                # No auth tokens, user IDs, credential handoff or config
                # entry. A validated code/TV-OK proof is NOT a login.
                return self.async_abort(reason="v3_preview_verified")
        return self.async_show_form(
            step_id="v3_code",
            description_placeholders={"code": self._v3_code},
            data_schema=vol.Schema({
                vol.Required("ha_code", default=self._v3_code): str,
                vol.Required("numbers_match"): bool,
            }),
            errors=errors,
        )
