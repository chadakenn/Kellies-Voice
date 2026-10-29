"""#214 H1/H4: offline review gate for an individual, non-admin TV credential.

This is deliberately NOT wired to a flow, auth exchange, mDNS endpoint or TV.
It never creates credentials. Callers must first prove physical TV verification
AND authenticated owner consent. No user identity may originate from TV input.

HA AuthManager API contract checked against homeassistant/auth/__init__.py:
  async_validate_access_token(access_token) -> RefreshToken | None
  async_get_refresh_token_by_token(refresh_token) -> RefreshToken | None
  async_get_refresh_token(refresh_token_id) -> RefreshToken | None
  async_remove_refresh_token(refresh_token: RefreshToken) -> None

Exact API behavior must be checked with the target Home Assistant Core build
before enabling this in a production credential lifecycle.
"""
from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID
from urllib.parse import urlsplit


class TvCredentialRejected(ValueError):
    """Intentionally generic. Never put secrets in exception text."""


@dataclass(frozen=True)
class DeviceTokenHandle:
    """Safe-to-persist identity handle. No access/refresh secrets."""

    tv_uuid: str
    user_id: str
    token_id: str
    client_id: str


def _canonical_tv_id(tv_uuid: str) -> str:
    if type(tv_uuid) is not str:
        raise TvCredentialRejected("TV identity invalid")
    try:
        parsed = UUID(tv_uuid)
    except (ValueError, AttributeError):
        raise TvCredentialRejected("TV identity invalid") from None
    if parsed.version != 4 or parsed.variant != "specified in RFC 4122":
        raise TvCredentialRejected("TV identity invalid")
    if str(parsed) != tv_uuid:
        raise TvCredentialRejected("TV identity invalid")
    return tv_uuid


def proposed_tv_client_id(origin: str, tv_uuid: str) -> str:
    """Candidate per-TV IndieAuth client_id, NOT verified by live HA OAuth.

    The application must confirm Home Assistant accepts this exact URI and
    redirects before any actual pairing code or token is exchanged.
    """
    tv_uuid = _canonical_tv_id(tv_uuid)
    if type(origin) is not str:
        raise TvCredentialRejected("Home Assistant origin invalid")
    parts = urlsplit(origin)
    if (parts.scheme not in ("http", "https") or not parts.hostname
            or parts.username or parts.password or parts.query or parts.fragment
            or parts.path not in ("", "/") or any(ch.isspace() for ch in origin)):
        raise TvCredentialRejected("Home Assistant origin invalid")
    # The base origin must come from HA's trusted server-side network config,
    # not the TV, mDNS, DNS search or the URL field in an unauthenticated form.
    return origin.rstrip("/") + "/kellies-voice/tv/" + tv_uuid


def _matches_record(record, handle: DeviceTokenHandle) -> bool:
    return (record is not None
            and getattr(record, "id", None) == handle.token_id
            and getattr(record, "client_id", None) == handle.client_id
            and getattr(getattr(record, "user", None), "id", None) == handle.user_id)


def verify_nonadmin_tv_candidate(
    hass, *, access_token: str, refresh_token: str, tv_uuid: str,
    owner_user_id: str, tv_user_id: str, expected_client_id: str,
) -> DeviceTokenHandle:
    """Fail closed if OAuth tokens belong to owner/admin/other user or TV.

    An authenticated HA owner's approval grants *permission to start setup*;
    it must never authorize placing that owner's own login token on a TV.

    This guard does not prove Assist permissions are truly TV-scoped:
    caller must independently validate non-admin Assist + alert access.
    """
    tv_uuid = _canonical_tv_id(tv_uuid)
    if (not all(type(v) is str and v for v in (
            access_token, refresh_token, owner_user_id, tv_user_id,
            expected_client_id))
            or tv_user_id == owner_user_id):
        raise TvCredentialRejected("TV credential identity invalid")
    if not expected_client_id.endswith("/kellies-voice/tv/" + tv_uuid):
        raise TvCredentialRejected("TV client identity invalid")

    auth = hass.auth
    try:
        access = auth.async_validate_access_token(access_token)
        refresh = auth.async_get_refresh_token_by_token(refresh_token)
    except Exception:
        raise TvCredentialRejected("TV credential validation unavailable") from None
    if (access is None or refresh is None
            or not getattr(access, "id", None)
            or getattr(access, "id", None) != getattr(refresh, "id", None)):
        raise TvCredentialRejected("TV credential pair invalid")

    user = getattr(refresh, "user", None)
    if (user is None or type(getattr(user, "id", None)) is not str
            or user.id != tv_user_id
            or getattr(user, "is_active", False) is not True
            or getattr(user, "is_admin", True) is not False
            or getattr(user, "is_owner", True) is not False
            or getattr(user, "system_generated", True) is not False
            or getattr(refresh, "token_type", None) != "normal"
            or refresh.client_id != expected_client_id
            or not getattr(refresh, "id", None)):
        raise TvCredentialRejected("TV credential privileges invalid")

    return DeviceTokenHandle(
        tv_uuid=tv_uuid, user_id=tv_user_id,
        token_id=refresh.id, client_id=expected_client_id,
    )


def revoke_verified_device_handle(hass, handle: DeviceTokenHandle) -> bool:
    """Revoke a specific previously persisted TV token ID while TV is offline.

    Returns False if already revoked or identity mismatch. Never accepts a
    bare user ID / TV UUID / credential secret as revoke authority. The
    trusted caller must load handle from an HA-owned durable store, NOT from
    a network message. A revoked user must still be recoverable by token ID.
    """
    if type(handle) is not DeviceTokenHandle:
        raise TvCredentialRejected("Invalid saved device handle")
    _canonical_tv_id(handle.tv_uuid)
    try:
        token = hass.auth.async_get_refresh_token(handle.token_id)
    except Exception:
        raise TvCredentialRejected("TV credential lookup unavailable") from None
    if not _matches_record(token, handle):
        return False
    try:
        hass.auth.async_remove_refresh_token(token)
    except Exception:
        raise TvCredentialRejected("TV credential revocation unavailable") from None
    return True
