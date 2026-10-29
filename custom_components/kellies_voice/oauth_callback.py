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
    derived from the flow_id. Rejects missing or unknown owner identity.
    """
    if not isinstance(state, str) or not state:
        raise ValueError("Invalid state")
    if not isinstance(owner_user_id, str) or not owner_user_id or owner_user_id == "unknown":
        raise ValueError("Verified initiating owner required")
    _PENDING[state] = {
        "flow_id": flow_id,
        "owner_user_id": owner_user_id,
        "tv_session": tv_session,
        "attempt": attempt,
    }


def consume_pending_authorization(state: str) -> dict[str, Any] | None:
    """Atomically remove and return the binding, or None if unknown/expired."""
    return _PENDING.pop(state, None)


def clear_pending_authorizations() -> None:
    """Test helper and cancel/error cleanup."""
    _PENDING.clear()


class OAuthCallbackView:
    """Public callback that receives the HA /auth/authorize redirect.

    HomeAssistantView and aiohttp are imported lazily so pure unit tests
    of the state registry do not require the full HA runtime.
    """

    url = "/api/kellies_voice/pair/oauth_callback"
    name = "api:kellies_voice:pair:oauth_callback"
    requires_auth = False

    def __init__(self, hass: Any) -> None:
        self.hass = hass

    async def get(self, request: Any) -> Any:
        """Validate state, then resume the exact config flow."""
        from aiohttp import web

        state = request.query.get("state")
        code = request.query.get("code")
        error = request.query.get("error")

        if error or not state or not code:
            _LOGGER.debug("OAuth callback rejected: missing or error response")
            # Clean up any leftover binding for this state if present.
            if state:
                consume_pending_authorization(state)
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

        # Resume via a dedicated callback step so the external-step form is
        # not re-entered. The flow will call attempt.consume_callback.
        try:
            await self.hass.config_entries.flow.async_configure(
                binding["flow_id"],
                user_input={
                    "oauth_state": state,
                    "oauth_code": code,
                    "bound_owner": binding["owner_user_id"],
                    "bound_tv": binding["tv_session"],
                    "attempt": binding["attempt"],
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
            text="<html><body><script>window.close();</script>Authorization received. Return to Home Assistant to finish setup.</body></html>",
            content_type="text/html",
        )


async def async_setup_oauth_callback(hass: Any) -> None:
    """Register the callback view. Genuinely async so HA setup can await it."""
    from homeassistant.components.http import HomeAssistantView

    class _RegisteredView(HomeAssistantView, OAuthCallbackView):
        pass

    hass.http.register_view(_RegisteredView(hass))
