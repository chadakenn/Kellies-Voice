"""#193: bounded Home Assistant OAuth token lifecycle, NOT wired to pairing.

Auth codes can only be exchanged from a previously owner+TV-approved,
one-shot PKCE session. This class does not create a token, accept a browser
callback, discover a TV, save credentials, or mark Assist connected.

A live HA integration must validate owner identity/consent and TV proof
before this module is invoked. Never serialize Tokens to logs, config
entries, mDNS, callback URLs or an unauthenticated discovery response.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import ipaddress
import json
import re
from urllib.parse import urlsplit

from .pairing_owner_oauth import OwnerAuthorizationAttempt, _origin

_MAX_BODY = 4096
_TOKEN = re.compile(r"[A-Za-z0-9._~+\-/=]{16,4096}\Z")
_MAX_EXPIRES = 86400


class OAuthTokenError(Exception):
    """Deliberately opaque: no tokens, URLs or response bodies in errors."""


@dataclass(frozen=True)
class AccessCredentials:
    access_token: str = field(repr=False)
    expires_in: int
    refresh_token: str | None = field(default=None, repr=False)


def _valid_token(secret):
    return type(secret) is str and _TOKEN.fullmatch(secret) is not None


def _token_body(doc, *, require_refresh: bool) -> AccessCredentials:
    if type(doc) is not dict or doc.get("token_type") != "Bearer":
        raise OAuthTokenError("Invalid Home Assistant authentication response")
    token = doc.get("access_token")
    expiry = doc.get("expires_in")
    refresh = doc.get("refresh_token")
    if (not _valid_token(token) or type(expiry) is not int
            or not 1 <= expiry <= _MAX_EXPIRES
            or (require_refresh and not _valid_token(refresh))
            or (refresh is not None and not _valid_token(refresh))):
        raise OAuthTokenError("Invalid Home Assistant authentication response")
    # Access credentials are in memory only. No persistence or logging.
    return AccessCredentials(token, expiry, refresh)


def _token_transport(instance_origin: str, endpoint_origin: str) -> str:
    """Only same-origin HTTPS or HA-local loopback HTTP can carry secrets.

    A browser's owner-approved client_id remains the HA instance URL. For HA
    running behind a proxy, an integration may POST to local 127.0.0.1 while
    preserving the original approved OAuth client_id. Never send an auth code
    or refresh token in plaintext across LAN, even private RFC1918 networks.
    """
    canonical = _origin(instance_origin)
    chosen = _origin(endpoint_origin)
    split = urlsplit(chosen)
    if split.scheme == "https" and chosen == canonical:
        return chosen + "/auth/token"
    if split.scheme == "http":
        try:
            address = ipaddress.ip_address(split.hostname or "")
        except ValueError:
            pass
        else:
            if address.is_loopback:
                return chosen + "/auth/token"
    raise OAuthTokenError("OAuth token endpoint must be HTTPS or loopback")


async def _read_token_response(response, *, require_refresh):
    if response.status != 200 or response.headers.get("Content-Type", "").split(";")[0].strip().lower() != "application/json":
        raise OAuthTokenError("Home Assistant authentication failed")
    size = 0
    chunks = []
    while True:
        part = await response.content.read(min(256, _MAX_BODY + 1 - size))
        if not part:
            break
        size += len(part)
        if size > _MAX_BODY:
            raise OAuthTokenError("Home Assistant authentication failed")
        chunks.append(part)
    try:
        data = json.loads(b"".join(chunks))
    except (ValueError, UnicodeDecodeError) as exc:
        raise OAuthTokenError("Home Assistant authentication failed") from None
    return _token_body(data, require_refresh=require_refresh)


def _client():
    import aiohttp
    return aiohttp.ClientSession(
        timeout=aiohttp.ClientTimeout(total=4),
        cookie_jar=aiohttp.DummyCookieJar(),
        trust_env=False, headers={},
    )


class HomeAssistantTokenExchange:
    """One private, owner-authorized OAuth token exchange endpoint."""

    def __init__(self, instance_origin: str, endpoint_origin: str, *,
                 _session_factory=None):
        self._client_id = _origin(instance_origin) + "/"
        self._endpoint = _token_transport(instance_origin, endpoint_origin)
        self._session_factory = _session_factory or _client

    async def _post(self, form, *, require_refresh: bool):
        try:
            async with self._session_factory() as session:
                async with session.post(
                    self._endpoint, data=form, allow_redirects=False,
                ) as response:
                    return await _read_token_response(
                        response, require_refresh=require_refresh)
        except OAuthTokenError:
            raise
        except Exception:
            # Suppress network exception strings which may expose URLs or
            # decrypted token contents in poorly configured HTTP clients.
            raise OAuthTokenError("Home Assistant authentication unavailable") from None

    async def exchange_approved_callback(
        self, attempt: OwnerAuthorizationAttempt, *, state: str,
        code: str, tv_session: str, now: float,
    ) -> AccessCredentials:
        if not isinstance(attempt, OwnerAuthorizationAttempt):
            raise OAuthTokenError("Owner authorization required")
        if self._client_id != attempt.client_id:
            attempt.cancel()
            raise OAuthTokenError("Wrong authorized Home Assistant instance")
        # Immediately burns state. A failed request cannot replay the code.
        try:
            consumed = attempt.consume_callback(
                received_state=state, code=code, tv_session=tv_session, now=now)
        except ValueError:
            raise OAuthTokenError("Owner authorization rejected") from None
        return await self.exchange_from_consumed(consumed)

    async def exchange_from_consumed(self, consumed: dict) -> AccessCredentials:
        """Exchange a code that has already been consumed exactly once.

        The caller is responsible for having called consume_callback and for
        discarding the verifier after this returns.
        """
        if not isinstance(consumed, dict):
            raise OAuthTokenError("Consumed authorization required")
        if self._client_id != consumed.get("client_id"):
            raise OAuthTokenError("Wrong authorized Home Assistant instance")
        return await self._post({
            "grant_type": "authorization_code",
            "client_id": self._client_id,
            "code": consumed["code"],
            "code_verifier": consumed["code_verifier"],
            "redirect_uri": consumed["redirect_uri"],
        }, require_refresh=True)

    async def refresh_access(self, refresh_token: str) -> AccessCredentials:
        if not _valid_token(refresh_token):
            raise OAuthTokenError("Invalid refresh credential")
        result = await self._post({
            "grant_type": "refresh_token",
            "client_id": self._client_id,
            "refresh_token": refresh_token,
        }, require_refresh=False)
        if result.refresh_token is not None:
            # No silent rotation: the caller must explicitly persist it.
            return result
        return AccessCredentials(result.access_token, result.expires_in,
                                 refresh_token)

    async def revoke_refresh(self, refresh_token: str) -> None:
        if not _valid_token(refresh_token):
            raise OAuthTokenError("Invalid refresh credential")
        try:
            async with self._session_factory() as session:
                async with session.post(
                    self._endpoint.replace("/auth/token", "/auth/revoke"),
                    data={"token": refresh_token},
                    allow_redirects=False,
                ) as response:
                    # HA deliberately returns 200 regardless of token
                    # validity; do not claim that the server confirmed
                    # the token actually existed.
                    if response.status != 200:
                        raise OAuthTokenError("Home Assistant revocation failed")
        except OAuthTokenError:
            raise
        except Exception:
            raise OAuthTokenError("Home Assistant revocation unavailable") from None
