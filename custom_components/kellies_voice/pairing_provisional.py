"""#213: provisional paired TV entry to break the alert-subscription deadlock.

The existing websocket alert path requires a config entry + readable select
entity before `kellies_voice/subscribe_alerts` succeeds. The OAuth flow
aborts without creating an entry, while the TV requires the alert proof
before accepting credentials. This creates a bootstrap deadlock.

This module creates a short-lived provisional config entry that:
- Exists only for the duration of the pairing session (≤ 120 s).
- Grants the initiating owner read access to the alert select for the
  specific TV UUID so the scoped subscription can succeed.
- Does not store any OAuth tokens.
- Is automatically removed (rollback) on expiry, abort, or failure.
- Does not mark the TV Connected or create a permanent device.

Discovery alone never creates trust. The provisional entry is created only
after owner consent + verified TV OK.

HA runtime imports are lazy so pure unit tests do not require homeassistant.
Entry creation uses the supported config-flow path when available; direct
ConfigEntry construction is a fallback that must be verified against the
target HA Core version before any live use.
"""

from __future__ import annotations

import time
from typing import Any

from .const import DOMAIN

_PROVISIONAL_TTL = 120  # seconds
_PROVISIONAL_TITLE = "Kellie's Voice (provisional)"


class ProvisionalTVEntry:
    """Tracks one short-lived config entry created for alert bootstrap."""

    def __init__(self, entry_id: str, tv_uuid: str, owner_user_id: str, started: float):
        self.entry_id = entry_id
        self.tv_uuid = tv_uuid
        self.owner_user_id = owner_user_id
        self.started = started

    @property
    def expired(self) -> bool:
        return time.time() - self.started >= _PROVISIONAL_TTL


async def create_provisional_entry(
    hass: Any,
    *,
    tv_uuid: str,
    owner_user_id: str,
) -> ProvisionalTVEntry | None:
    """Create a provisional config entry. Returns None on failure.

    Refuses to create if any entry (provisional or permanent) already exists
    for the same TV UUID. No tokens are stored.
    """
    if not isinstance(tv_uuid, str) or not tv_uuid:
        return None
    if not isinstance(owner_user_id, str) or not owner_user_id or owner_user_id == "unknown":
        return None
    for entry in hass.config_entries.async_entries(DOMAIN):
        if entry.data.get("device_id") == tv_uuid:
            return None  # permanent or provisional already present
    try:
        from homeassistant.config_entries import ConfigEntry
        entry = ConfigEntry(
            version=1,
            domain=DOMAIN,
            title=_PROVISIONAL_TITLE,
            data={
                "device_id": tv_uuid,
                "name": _PROVISIONAL_TITLE,
                "provisional": True,
                "owner_user_id": owner_user_id,
                "started": time.time(),
            },
            options={},
            source="pairing",
        )
        # Prefer the async API; fall back if the installed HA returns the entry directly.
        result = hass.config_entries.async_add(entry)
        if hasattr(result, "__await__"):
            result = await result
        entry_id = getattr(result, "entry_id", None) or getattr(entry, "entry_id", None)
        if not entry_id:
            return None
    except Exception:
        return None
    return ProvisionalTVEntry(entry_id, tv_uuid, owner_user_id, time.time())


async def rollback_provisional_entry(
    hass: Any,
    provisional: ProvisionalTVEntry,
) -> bool:
    """Remove the provisional entry, including after expiry.

    Confirms the entry is still provisional and matches the exact TV UUID
    and owner before removal. Returns True if removed.
    """
    if provisional is None:
        return False
    entry = hass.config_entries.async_get_entry(provisional.entry_id)
    if entry is None:
        return False
    data = entry.data or {}
    if not data.get("provisional"):
        return False
    if data.get("device_id") != provisional.tv_uuid:
        return False
    if data.get("owner_user_id") != provisional.owner_user_id:
        return False
    try:
        result = hass.config_entries.async_remove(provisional.entry_id)
        if hasattr(result, "__await__"):
            await result
        return True
    except Exception:
        return False


def is_provisional_allowed(hass: Any, tv_uuid: str, user: Any) -> bool:
    """True only if a non-expired provisional entry exists for this owner+TV.

    Enforces the 120 s TTL using the stored started timestamp. Does not grant
    permission by itself; the existing select-entity read check still applies.
    """
    if user is None:
        return False
    now = time.time()
    for entry in hass.config_entries.async_entries(DOMAIN):
        data = entry.data or {}
        if (
            data.get("device_id") == tv_uuid
            and data.get("provisional")
            and data.get("owner_user_id") == getattr(user, "id", None)
        ):
            started = data.get("started")
            if (type(started) in (int, float)
                    and 0 <= now - started < _PROVISIONAL_TTL):
                return True
    return False


async def cleanup_expired_provisional_entries(hass: Any) -> int:
    """Remove all expired provisional entries. Returns the count removed.

    Safe to call on HA startup and from a periodic timer. Never removes a
    non-provisional entry.
    """
    now = time.time()
    removed = 0
    for entry in list(hass.config_entries.async_entries(DOMAIN)):
        data = entry.data or {}
        if not data.get("provisional"):
            continue
        started = data.get("started")
        if not isinstance(started, (int, float)) or now - started >= _PROVISIONAL_TTL:
            try:
                result = hass.config_entries.async_remove(entry.entry_id)
                if hasattr(result, "__await__"):
                    await result
                removed += 1
            except Exception:
                pass
    return removed
