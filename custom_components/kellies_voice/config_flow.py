"""Discovery flow. Home Assistant does not connect to the TV."""

import time
from urllib.parse import parse_qs, urlsplit

import voluptuous as vol
from homeassistant.config_entries import ConfigFlow, OptionsFlow
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo

from .const import DOMAIN
from .validation import clean_tv_device_id
from .pairing_probe import candidate_from_discovery
from .pairing_exchange import begin_preview, confirm_tv_ok
from .pairing_owner_oauth import OwnerAuthorizationAttempt
from .pairing_browser_probe import register_probe
from .pairing_owner_claim import owner_claims

_BROWSER_NOTICE = (
    "TEST ONLY: this opens Home Assistant's own browser login page. "
    "It will discard the authorization code and NEVER install a TV login."
)


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
        self._preview_deadline = 0.0
        self._owner_verified_tv = None
        self._owner_proof_expires = 0.0
        self._browser_attempt = None
        self._browser_state = None
        self._browser_owner = None
        self._browser_probe_succeeded = False

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
        """Separate, opt-in, token-free preview from the stable TV discovery."""
        if _text(discovery_info.properties, "preview") == "2":
            try:
                self._preview_candidate = candidate_from_discovery(
                    str(discovery_info.ip_address),
                    discovery_info.port,
                    discovery_info.properties,
                )
            except (ValueError, AttributeError, TypeError):
                return self.async_abort(reason="invalid_pairing_preview")
            # The one-time public random session is NOT the TV's permanent
            # device ID. Never create a permanent config entry from it.
            await self.async_set_unique_id(
                "preview:" + self._preview_candidate.session
            )
            self._abort_if_unique_id_configured()
            self.context["title_placeholders"] = {"name": "Kellie's Voice setup preview"}
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

    async def async_step_pairing_preview(self, user_input=None):
        """Owner explicitly starts a public-key-only preview, not OAuth."""
        if self._preview_candidate is None:
            return self.async_abort(reason="invalid_pairing_preview")
        errors = {}
        if user_input is not None:
            if user_input.get("start") is not True:
                return self.async_abort(reason="preview_cancelled")
            try:
                self._preview_attempt = await begin_preview(self._preview_candidate)
            except Exception:
                errors["base"] = "preview_unavailable"
            else:
                # Shorter than the TV's 120-second listener. A stale callback
                # or old HA UI can never be upgraded to a fresh connection.
                self._preview_deadline = time.monotonic() + 100
                return await self.async_step_pairing_code()
        return self.async_show_form(
            step_id="pairing_preview",
            data_schema=vol.Schema({vol.Required("start"): bool}),
            errors=errors,
        )

    async def async_step_pairing_code(self, user_input=None):
        """Compare code and validate actual TV remote OK, then stop. No entry."""
        attempt = self._preview_attempt
        if attempt is None or time.monotonic() >= self._preview_deadline:
            self._preview_attempt = None
            self._preview_candidate = None
            return self.async_abort(reason="preview_expired")
        errors = {}
        if user_input is not None:
            if user_input.get("numbers_match") is not True:
                self._preview_attempt = None
                return self.async_abort(reason="preview_cancelled")
            # Home Assistant may cache custom integration translations. An
            # untranslated description cannot be treated as proof that the
            # human saw the visual comparison code. Render the server-side
            # code in the form itself as a prefilled required field, and
            # fail CLOSED if it was missing/modified in the submitted form.
            # Never trust a client-supplied alternative code.
            if user_input.get("ha_code") != attempt.code:
                self._preview_attempt = None
                self._preview_candidate = None
                return self.async_abort(reason="preview_code_missing")
            try:
                verified = await confirm_tv_ok(attempt)
            except Exception:
                errors["base"] = "preview_unavailable"
            else:
                if verified is None:
                    errors["base"] = "waiting_for_tv"
                elif verified.session != attempt.candidate.session:
                    errors["base"] = "preview_unavailable"
                else:
                    # Proof checked, including canonical TV UUID and P-256
                    # owner-initiated session. Stop here. No config entry,
                    # OAuth callback, real token, saved identity, or service.
                    # Only a real TV UUID/session proof may create a
                    # one-time owner claim. The actual owner must later
                    # identify themselves through an authenticated HA
                    # WebSocket, never via Zeroconf or a form field.
                    try:
                        owner_claims(self.hass).begin(
                            flow_id=self.flow_id,
                            session=verified.session,
                            tv_uuid=verified.tv_uuid,
                        )
                    except ValueError:
                        self._preview_attempt = None
                        self._preview_candidate = None
                        return self.async_abort(reason="owner_probe_unavailable")
                    self._preview_attempt = None
                    self._preview_candidate = None
                    self._owner_verified_tv = verified
                    # An independently verified TV OK starts its own
                    # strictly bounded owner consent window. It need not
                    # borrow the remaining time from the LAN listener.
                    self._owner_proof_expires = time.monotonic() + 120
                    # The former preview success is now an explicit owner
                    # opt-in gate. It does NOT authorize OAuth automatically.
                    return await self.async_step_owner_browser_consent()
        return self.async_show_form(
            step_id="pairing_code",
            description_placeholders={"code": attempt.code},
            # Unlike a translated description, this default value is carried
            # by Home Assistant's backend data_schema and stays visible
            # even when the frontend shows raw untranslated field keys.
            data_schema=vol.Schema({
                vol.Required("ha_code", default=attempt.code): str,
                vol.Required("numbers_match"): bool,
            }),
            errors=errors,
        )

    def _owner_proof_live(self) -> bool:
        """Owner consent is scoped to the verified physical TV and TTL."""
        return (self._owner_verified_tv is not None
                and bool(getattr(self._owner_verified_tv, "tv_uuid", None))
                and bool(getattr(self._owner_verified_tv, "session", None))
                and 0 < time.monotonic() < self._owner_proof_expires)

    async def async_step_owner_browser_consent(self, user_input=None):
        """After real ECDH TV OK, ask the authenticated HA owner explicitly.

        This is only a browser callback probe. The authorization code is
        intentionally discarded after callback, NOT exchanged for tokens.
        """
        if not self._owner_proof_live():
            return self.async_abort(reason="owner_probe_expired")
        if user_input is None:
            return self.async_show_form(
                step_id="owner_browser_consent",
                # Visible even when a local HA installation fails to load
                # translated titles and descriptions (as in owner QA).
                data_schema=vol.Schema({
                    vol.Required("test_only_notice", default=_BROWSER_NOTICE): str,
                    vol.Required("allow_browser_login"): bool,
                }),
            )
        if user_input.get("allow_browser_login") is not True:
            owner_claims(self.hass).cancel(self.flow_id)
            self._owner_verified_tv = None
            return self.async_abort(reason="owner_probe_cancelled")
        if user_input.get("test_only_notice") != _BROWSER_NOTICE:
            owner_claims(self.hass).cancel(self.flow_id)
            self._owner_verified_tv = None
            return self.async_abort(reason="owner_probe_rejected")

        # The discovery config flow has no reliable authenticated
        # initiating user. Explicitly claim it from a separate HA
        # WebSocket whose connection.user is the actual signed-in owner.
        tv = self._owner_verified_tv
        user_id = owner_claims(self.hass).consume(
            flow_id=self.flow_id, session=tv.session, tv_uuid=tv.tv_uuid,
        )
        if not isinstance(user_id, str) or not user_id:
            return self.async_abort(reason="owner_probe_owner_required")
        try:
            user = await self.hass.auth.async_get_user(user_id)
        except Exception:
            return self.async_abort(reason="owner_probe_owner_required")
        if (user is None or getattr(user, "is_owner", False) is not True
                or getattr(user, "is_active", False) is not True):
            return self.async_abort(reason="owner_probe_owner_required")

        try:
            from homeassistant.helpers.network import get_url
            # Home Assistant Cloud is an HTTPS external URL. The earlier
            # draft accidentally disabled it using allow_cloud=False, which
            # made valid Nabu Casa remote access fail. Select through HA's
            # network helper rather than trusting user input or discovery.
            # Require SSL, forbid plaintext internal fallback, and prefer
            # the managed cloud URL when configured.
            origin = get_url(
                self.hass,
                require_ssl=True,
                allow_internal=False,
                allow_external=True,
                allow_cloud=True,
                prefer_cloud=True,
            )
            # Defensive check in addition to get_url(require_ssl=True).
            # Never send an authorization code to an HTTP callback.
            if urlsplit(origin).scheme != "https":
                return self.async_abort(reason="owner_probe_https_required")
            attempt = OwnerAuthorizationAttempt.begin(
                origin,
                self._owner_verified_tv.session,
                time.time(),
                owner_requested=True,
                tv_ok_verified=True,
            )
            url = attempt.authorization_url()
            values = parse_qs(urlsplit(url).query)
            state = values["state"][0]
            register_probe(
                state=state, flow_id=self.flow_id, owner_id=user_id,
                tv_session=self._owner_verified_tv.session,
            )
        except Exception:
            # No authorization URL should ever be displayed if binding or
            # origin validation fails. Never log secrets or query strings.
            return self.async_abort(reason="owner_probe_unavailable")
        self._browser_attempt = attempt
        self._browser_state = state
        self._browser_owner = user_id
        return self.async_external_step(
            step_id="owner_browser_open", url=url
        )

    async def async_step_owner_browser_open(self, user_input=None):
        """Receive only the exact callback routed via single-use state."""
        if not isinstance(user_input, dict):
            return self.async_abort(reason="owner_probe_cancelled")
        attempt = self._browser_attempt
        if (attempt is None or self._browser_state is None
                or not self._owner_proof_live()
                or user_input.get("probe_state") != self._browser_state
                or user_input.get("probe_owner_id") != self._browser_owner
                or user_input.get("probe_tv_session")
                != getattr(self._owner_verified_tv, "session", None)
                or self._browser_owner is None):
            if attempt is not None:
                attempt.cancel()
            return self.async_abort(reason="owner_probe_rejected")
        try:
            consumed = attempt.consume_callback(
                received_state=user_input["probe_state"],
                code=user_input.get("probe_code"),
                tv_session=self._owner_verified_tv.session,
                now=time.time(),
            )
        except (ValueError, TypeError, KeyError):
            return self.async_abort(reason="owner_probe_rejected")
        # The test is intentionally NOT allowed to call /auth/token;
        # discard PKCE verifier, client ID and authorization code now.
        consumed.clear()
        self._browser_attempt = None
        self._browser_state = None
        self._owner_verified_tv = None
        self._browser_probe_succeeded = True
        return self.async_external_step_done(
            next_step_id="owner_browser_done"
        )

    async def async_step_owner_browser_done(self, user_input=None):
        """Only the actual one-use callback can mark the browser round trip."""
        if self._browser_probe_succeeded is not True:
            return self.async_abort(reason="owner_probe_rejected")
        self._browser_probe_succeeded = False
        return self.async_abort(reason="owner_browser_probe_complete")
