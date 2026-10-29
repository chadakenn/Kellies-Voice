"""#213: same-origin Home Assistant OAuth callback view.

This view is intentionally unauthenticated (requires_auth=False) because
Home Assistant's /auth/authorize redirect arrives without the frontend
session cookie. Security is enforced exclusively by a server-side,
single-use, CSPRNG state binding created only after explicit owner
consent + verified TV OK.

No tokens, codes, or secrets are ever returned in the response body or logs.
"""

from __future__ import annotations

import logging
from typing import Any

from aiohttp import web
from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant

from .const import DOMAIN

_LOGGER = logging.getLogger(__name__)

# In-memory store of pending authorization attempts keyed by the random state.
# Each entry is bound to a specific flow_id, owner user_id, TV session,
# and the OwnerAuthorizationAttempt instance. Single-use and time-bounded
# by the attempt itself.
_PENDING: dict[str, dict[str, Any]] = {}


def register_pending_authorization(
    state: str,
    *,
    flow_id: str,
    owner_user_id: str,
    tv_session: str,
    attempt: Any,
) -> None:
    """Bind a freshly generated CSPRNG state to the initiating context.

    Must be called only after the HA owner has explicitly requested pairing
    and the TV OK proof has been verified. The state value itself is never
    derived from the flow_id.
    """
    if not isinstance(state, str) or not state:
        raise ValueError("Invalid state")
    _PENDING[state] = {
        "flow_id": flow_id,
        "owner_user_id": owner_user_id,
        "tv_session": tv_session,
        "attempt": attempt,
    }


def consume_pending_authorization(state: str) -> dict[str, Any] | None:
    """Atomically remove and return the binding, or None if unknown/expired."""
    return _PENDING.pop(state, None)


class OAuthCallbackView(HomeAssistantView):
    """Public callback that receives the HA /auth/authorize redirect."""

    url = "/api/kellies_voice/pair/oauth_callback"
    name = "api:kellies_voice:pair:oauth_callback"
    requires_auth = False

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass

    async def get(self, request: web.Request) -> web.Response:
        """Validate state, then resume the exact config flow."""
        state = request.query.get("state")
        code = request.query.get("code")
        error = request.query.get("error")

        if error or not state or not code:
            _LOGGER.debug("OAuth callback rejected: missing or error response")
            return web.Response(
                text="<html><body>Authorization rejected or cancelled.</body></html>",
                content_type="text/html",
                status=400,
            )

        binding = consume_pending_authorization(state)
        if binding is None:
            _LOGGER.debug("OAuth callback rejected: unknown or already-consumed state")
            return web.Response(
                text="<html><body>Authorization session not found or expired.</body></html>",
                content_type="text/html",
                status=400,
            )

        # Resume the specific flow with only the validated code and state.
        # The flow itself will re-validate owner/TV binding and perform the
        # token exchange. We never put secrets in this response.
        try:
            await self.hass.config_entries.flow.async_configure(
                binding["flow_id"],
                user_input={
                    "oauth_state": state,
                    "oauth_code": code,
                    "expected_owner": binding["owner_user_id"],
                    "expected_tv": binding["tv_session"],
                },
            )
        except Exception:
            _LOGGER.debug("OAuth callback failed to resume flow", exc_info=False)
            return web.Response(
                text="<html><body>Authorization could not be completed.</body></html>",
                content_type="text/html",
                status=500,
            )

        return web.Response(
            text="<html><body><script>window.close();</script>Authorization complete. You may close this window.</body></html>",
            content_type="text/html",
        )


def async_setup_oauth_callback(hass: HomeAssistant) -> None:
    """Register the callback view. Call from async_setup."""
    hass.http.register_view(OAuthCallbackView(hass))
