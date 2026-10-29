"""Short-lived authenticated HA-owner claim for a cryptographically verified TV.

An auto-created Zeroconf config flow is *not* an authenticated owner session.
This registry bridges the TV proof to a separate authenticated Home Assistant
WebSocket request. Crucially, the owner identity comes from connection.user,
never from a user ID supplied by the TV, mDNS TXT, form or OAuth callback.

No credentials, authorization codes, or permanent config entries are stored.
A frontend pairing approval control still needs to invoke the WebSocket
command on the owner's existing HA session. This is an isolated QA boundary.
"""
from __future__ import annotations

from dataclasses import dataclass
import re
import time
from typing import Any

from .const import DOMAIN
from .pairing_verification import _session_bytes
from .validation import clean_tv_device_id

_TTL = 120.0
_LIMIT = 32
_FLOW_ID = re.compile(r"[a-zA-Z0-9_-]{8,128}\Z")
_OWNER_KEY = "verified_pairing_owner_claims"


@dataclass
class _Pairing:
    session: str
    tv_uuid: str
    comparison_code: str
    started: float
    owner_id: str | None = None


class OwnerClaimRegistry:
    def __init__(self) -> None:
        self._pending: dict[str, _Pairing] = {}

    def _prune(self, now: float) -> None:
        for key, item in list(self._pending.items()):
            if not item.started <= now < item.started + _TTL:
                del self._pending[key]

    def begin(self, *, flow_id: str, session: str, tv_uuid: str,
              comparison_code: str, now: float | None = None) -> None:
        """Accept only a previously verified flow and canonical TV identity."""
        instant = time.monotonic() if now is None else now
        if (not isinstance(flow_id, str) or not _FLOW_ID.fullmatch(flow_id)
                or type(instant) not in (int, float) or instant < 0):
            raise ValueError("Invalid verified pairing flow")
        _session_bytes(session)
        # The backend-derived code is only shown to an authenticated owner.
        # A client-supplied code never becomes the authority for a claim.
        if (type(comparison_code) is not str or len(comparison_code) != 8
                or not comparison_code.isascii()
                or not comparison_code.isdigit()):
            raise ValueError("Invalid verified comparison code")
        normalized = clean_tv_device_id(tv_uuid)
        if tv_uuid != normalized:
            raise ValueError("TV identity must be canonical")
        self._prune(instant)
        if flow_id in self._pending or len(self._pending) >= _LIMIT:
            raise ValueError("Pairing flow already pending or capacity reached")
        self._pending[flow_id] = _Pairing(
            session, normalized, comparison_code, instant)

    def claim(self, *, flow_id: str, session: str, user: Any,
              now: float | None = None) -> bool:
        """Only the actual authenticated active HA owner may claim the flow."""
        instant = time.monotonic() if now is None else now
        self._prune(instant)
        entry = self._pending.get(flow_id)
        owner_id = getattr(user, "id", None)
        if (entry is None or not isinstance(session, str)
                or entry.session != session
                or entry.owner_id is not None
                or type(owner_id) is not str or not owner_id or owner_id == "unknown"
                or getattr(user, "is_owner", False) is not True
                or getattr(user, "is_active", False) is not True):
            return False
        entry.owner_id = owner_id
        return True

    def consume(self, *, flow_id: str, session: str, tv_uuid: str,
                now: float | None = None) -> str | None:
        """One shot: caller must verify the bound owner still exists/active."""
        instant = time.monotonic() if now is None else now
        self._prune(instant)
        entry = self._pending.pop(flow_id, None)
        if (entry is None or entry.owner_id is None
                or entry.session != session or entry.tv_uuid != tv_uuid):
            return None
        return entry.owner_id

    def pending(self, *, user: Any, now: float | None = None) -> list[dict]:
        """Expose only unclaimed, physical-TV-verified sessions to a HA owner.

        This is not an OAuth credential, public discovery listing, or login.
        All rows are already bound to a confirmed TV UUID + session.
        """
        if (user is None or getattr(user, "is_owner", False) is not True
                or getattr(user, "is_active", False) is not True):
            return []
        instant = time.monotonic() if now is None else now
        self._prune(instant)
        return [
            {"flow_id": key, "tv_session": entry.session,
             "tv_uuid": entry.tv_uuid,
             "comparison_code": entry.comparison_code,
             "seconds_remaining": max(0, int(entry.started + _TTL - instant))}
            for key, entry in self._pending.items()
            if entry.owner_id is None
        ]

    def cancel(self, flow_id: str) -> None:
        self._pending.pop(flow_id, None)


def owner_claims(hass: Any) -> OwnerClaimRegistry:
    """Process-local to HA instance; never a global cross-HASS authority."""
    data = hass.data.setdefault(DOMAIN, {})
    if _OWNER_KEY not in data:
        data[_OWNER_KEY] = OwnerClaimRegistry()
    return data[_OWNER_KEY]
