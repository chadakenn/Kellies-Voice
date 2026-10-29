"""Token-free owner OAuth browser round-trip probe for #193.

This callback is NOT a token exchange: it never calls /auth/token,
persists credentials, changes config entries or marks a TV Connected.
The unauthenticated browser callback may resume only the exact
initiating config flow via one-use 256-bit state, which is bound to a
previously cryptographically confirmed TV session and an HA owner ID.

This module must not be installed by itself on a public HA server.
"""
from __future__ import annotations

from dataclasses import dataclass
import hmac
import logging
import re
import time
from typing import Any

_LOGGER = logging.getLogger(__name__)
_TTL = 120.0
_STATE = re.compile(r"[A-Za-z0-9_-]{43}\Z")
_CODE = re.compile(r"[A-Za-z0-9._~-]{8,512}\Z")


@dataclass(frozen=True)
class PendingBrowserProbe:
    flow_id: str
    owner_id: str
    tv_session: str
    registered_monotonic: float


_pending: dict[str, PendingBrowserProbe] = {}


def clear_probes() -> None:
    """For tests and on component unload only."""
    _pending.clear()


def _prune(now: float) -> None:
    for state, entry in list(_pending.items()):
        if not entry.registered_monotonic <= now < entry.registered_monotonic + _TTL:
            _pending.pop(state, None)


def register_probe(*, state: str, flow_id: str, owner_id: str,
                   tv_session: str, now: float | None = None) -> None:
    """Register a single-use browser state for an owner+TV-verified flow."""
    from .pairing_verification import _session_bytes
    actual = time.monotonic() if now is None else now
    if (type(state) is not str or not _STATE.fullmatch(state)
            or type(flow_id) is not str or not flow_id
            or type(owner_id) is not str or not owner_id or owner_id == "unknown"
            or type(tv_session) is not str
            or type(actual) not in (float, int) or actual < 0):
        raise ValueError("Invalid owner browser probe")
    _session_bytes(tv_session)
    _prune(actual)
    if state in _pending:
        raise ValueError("Reused owner authorization state")
    _pending[state] = PendingBrowserProbe(flow_id, owner_id, tv_session, actual)


def consume_probe(state: str, *, now: float | None = None) -> PendingBrowserProbe | None:
    """Consume even on malformed/late callbacks. No retry after failure."""
    actual = time.monotonic() if now is None else now
    if type(state) is not str or not _STATE.fullmatch(state):
        return None
    record = _pending.pop(state, None)
    _prune(actual)
    if (record is None or type(actual) not in (int, float)
            or not record.registered_monotonic <= actual
            < record.registered_monotonic + _TTL):
        return None
    return record


class OAuthBrowserProbeView:
    """HA callback receives only a short-lived authorization code, then drops it."""

    url = "/api/kellies_voice/pair/oauth_callback"
    name = "api:kellies_voice:pair:oauth_callback"
    requires_auth = False

    def __init__(self, hass: Any) -> None:
        self.hass = hass

    async def get(self, request: Any) -> Any:
        from aiohttp import web

        state = request.query.get("state")
        code = request.query.get("code")
        error = request.query.get("error")
        # Consuming state on error means consent denial cannot be replayed.
        binding = consume_probe(state)
        if (binding is None or error is not None or type(code) is not str
                or not _CODE.fullmatch(code)):
            return web.Response(
                text="Authorization cancelled or expired. Return to Home Assistant.",
                content_type="text/plain", status=400,
                headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"},
            )

        # Only a one-shot flow resume, NOT a /auth/token request. Do not log
        # the code or render it back in the browser's HTML response.
        try:
            await self.hass.config_entries.flow.async_configure(
                binding.flow_id,
                user_input={
                    "probe_state": state,
                    "probe_code": code,
                    "probe_owner_id": binding.owner_id,
                    "probe_tv_session": binding.tv_session,
                },
            )
        except Exception:
            _LOGGER.debug("Owner browser probe flow could not be resumed")
            return web.Response(
                text="Unable to finish browser preview. Return to Home Assistant.",
                content_type="text/plain", status=400,
                headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"},
            )

        return web.Response(
            text="<html><body><script>window.close()</script>Browser authorization preview received. Return to Home Assistant.</body></html>",
            content_type="text/html", headers={
                "Cache-Control": "no-store", "Referrer-Policy": "no-referrer",
                "Content-Security-Policy": "default-src 'none'; script-src 'unsafe-inline'",
            },
        )


async def async_register_browser_probe(hass: Any) -> None:
    from homeassistant.components.http import HomeAssistantView

    class RegisteredOAuthBrowserProbe(HomeAssistantView, OAuthBrowserProbeView):
        pass

    hass.http.register_view(RegisteredOAuthBrowserProbe(hass))
