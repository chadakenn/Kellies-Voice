"""#193 encrypted provisioning payload, no networking, no OAuth callback.

Encodes a single, bounded, canonical candidate for a *verified* pairing
channel. The caller must already have independently verified owner consent,
the correct TV OK and the full ECDH transcript. This does NOT grant consent.

NEVER send the plaintext from this module through mDNS, HTTP discovery,
status endpoints, logging, an HA config entry or an error message.
"""
from __future__ import annotations

import json
import re
from uuid import UUID

from .pairing_owner_oauth import _origin
from .pairing_verification import _session_bytes

_KIND = "kellies_voice/pair/credentials/v1"
_KEYS = frozenset((
    "kind", "tv_id", "session", "server_url", "client_id",
    "access_token", "refresh_token", "expires_at_ms",
))
_SECRET = re.compile(r"[A-Za-z0-9._~+/=\-]{16,2048}\Z")
_MAX = 2048
_MAX_DURATION = 86_400_000


def _tv_id(value: str) -> str:
    try:
        parsed = UUID(value)
    except (ValueError, AttributeError, TypeError):
        raise ValueError("Invalid provisioned TV") from None
    if parsed.version != 4 or parsed.variant != "specified in RFC 4122" or str(parsed) != value:
        raise ValueError("Invalid provisioned TV")
    return str(parsed)


def encode_credentials(
    *, tv_id: str, session: str, server_url: str, client_id: str,
    access_token: str, refresh_token: str, expires_at_ms: int,
    issued_at_ms: int,
) -> bytes:
    """Create plaintext only for immediate PairingSealedChannel.seal() use.

    The actual authenticated private pairing transport is a separate
    integration gate; do not persist or emit this plaintext in HTTP.
    """
    _tv_id(tv_id)
    _session_bytes(session)
    origin = _origin(server_url)
    if server_url not in (origin, origin + "/"):
        raise ValueError("Invalid credential origin")
    if client_id != origin + "/":
        raise ValueError("Invalid OAuth client binding")
    if not _SECRET.fullmatch(access_token) or not _SECRET.fullmatch(refresh_token):
        raise ValueError("Invalid credential format")
    if (type(expires_at_ms) is not int or type(issued_at_ms) is not int
            or issued_at_ms < 1 or not 0 < expires_at_ms - issued_at_ms <= _MAX_DURATION):
        raise ValueError("Invalid credential expiry")
    record = {
        "kind": _KIND,
        "tv_id": tv_id,
        "session": session,
        "server_url": origin,
        "client_id": client_id,
        "access_token": access_token,
        "refresh_token": refresh_token,
        "expires_at_ms": expires_at_ms,
    }
    clear = json.dumps(
        record, sort_keys=True, separators=(",", ":"), ensure_ascii=True
    ).encode("ascii")
    if len(clear) > _MAX:
        raise ValueError("Credential envelope too large")
    return clear


def seal_credentials(channel, **kwargs) -> bytes:
    """Immediately AEAD seal; never return plaintext from this convenience path."""
    plaintext = encode_credentials(**kwargs)
    # Python bytes cannot be wiped in place; do not retain longer than needed.
    return channel.seal(plaintext)


def validate_payload_shape(clear: bytes, *, expected_tv_id: str,
                           expected_session: str, now_ms: int) -> dict:
    """Offline verification for cross-platform parity tests only.

    This is NOT a production receive path: the TV MUST first authenticate
    and decrypt the AEAD packet with its own PairingSealedChannel.
    """
    if type(clear) is not bytes or not 1 <= len(clear) <= _MAX:
        raise ValueError("Invalid credential envelope")
    try:
        data = json.loads(clear)
    except (UnicodeDecodeError, ValueError):
        raise ValueError("Invalid credential envelope") from None
    if type(data) is not dict or set(data) != _KEYS or data.get("kind") != _KIND:
        raise ValueError("Invalid credential fields")
    _tv_id(expected_tv_id)
    _session_bytes(expected_session)
    if data["tv_id"] != expected_tv_id or data["session"] != expected_session:
        raise ValueError("Wrong TV or session")
    if type(now_ms) is not int or now_ms < 1:
        raise ValueError("Invalid local clock")
    encode_credentials(
        tv_id=data["tv_id"], session=data["session"],
        server_url=data["server_url"], client_id=data["client_id"],
        access_token=data["access_token"], refresh_token=data["refresh_token"],
        expires_at_ms=data["expires_at_ms"], issued_at_ms=now_ms,
    )
    return data
