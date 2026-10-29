"""#193 owner-auth authorization URL and callback guard (offline primitives only).

This module NEVER calls /auth/token, receives a refresh token, publishes a
web endpoint, or authorizes a TV. The actual user-initiated OAuth config
flow and protected callback must be reviewed before connecting this code.

HA's OAuth client identification is based on the app URL, NOT on a generic
OAuth device grant. For a browser-hosted callback without a phone app, this
experimental design uses the HA instance's own canonical origin as client_id
and a same-origin callback. Validate this on real HA before using it in a
public release; never impersonate the official HA companion app client ID.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import ipaddress
import re
import secrets
from urllib.parse import urlencode, urlsplit

from .pairing_verification import _session_bytes

_CALLBACK = "/api/kellies_voice/pair/oauth_callback"
_RFC1918 = (
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
)
_LOCALHOST = ipaddress.ip_network("127.0.0.0/8")
_ULA = ipaddress.ip_network("fc00::/7")
_SESSION_SECONDS = 120
_CODE = re.compile(r"[A-Za-z0-9._~-]{8,512}\Z")


def _origin(value: str) -> str:
    """Reject public plaintext HTTP, user-info, redirects and path confusion."""
    if not isinstance(value, str) or len(value) > 256 or "\\" in value:
        raise ValueError("Invalid Home Assistant instance URL")
    try:
        url = urlsplit(value)
        port = url.port
    except ValueError as error:
        raise ValueError("Invalid Home Assistant instance URL") from error
    if (
        url.scheme not in ("http", "https") or not url.hostname or
        url.username is not None or url.password is not None or
        url.path not in ("", "/") or url.query or url.fragment or
        any(ch.isspace() for ch in value)
    ):
        raise ValueError("Invalid Home Assistant instance URL")
    host = url.hostname
    if "%" in host or not re.fullmatch(r"[a-zA-Z0-9_.:\[\]-]+", host):
        raise ValueError("Invalid Home Assistant host")
    if url.scheme == "http":
        try:
            ip = ipaddress.ip_address(host)
        except ValueError:
            if host.lower() != "localhost" and not host.lower().endswith(
                (".local", ".home.arpa")
            ):
                raise ValueError("Public plaintext HTTP is prohibited")
        else:
            if not (
                any(ip in net for net in _RFC1918) or
                ip in _LOCALHOST or ip in _ULA or ip.is_loopback
            ):
                raise ValueError("Public plaintext HTTP is prohibited")
    host = host.lower()
    bracketed_host = f"[{host}]" if ":" in host else host
    return f"{url.scheme}://{bracketed_host}" + (f":{port}" if port else "")


def _random_secret(random_bytes) -> str:
    value = random_bytes(32)
    if type(value) is not bytes or len(value) != 32:
        raise ValueError("Owner authorization requires a secure 256-bit nonce")
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


class OwnerAuthorizationAttempt:
    """One-time, time-bounded authorization state, bound to a verified TV."""

    def __init__(self, *, origin, tv_session, started, state, verifier):
        self.origin = origin
        self.tv_session = tv_session
        self.started = started
        self._state = state
        self._verifier = verifier
        self._used = False

    @classmethod
    def begin(
        cls, instance_url: str, tv_session: str, now: float, *,
        owner_requested: bool, tv_ok_verified: bool, random_bytes=None,
    ):
        if type(owner_requested) is not bool or type(tv_ok_verified) is not bool:
            raise ValueError("Consent requirements missing")
        if not owner_requested or not tv_ok_verified or now < 0:
            raise ValueError("Both owner and TV confirmation are required")
        _session_bytes(tv_session)
        origin = _origin(instance_url)
        # Only secrets.token_bytes (or a fixed test source) creates state.
        factory = random_bytes or secrets.token_bytes
        state = _random_secret(factory)
        verifier = _random_secret(factory)
        if hmac.compare_digest(state, verifier):
            raise ValueError("Non-independent OAuth secrets")
        return cls(origin=origin, tv_session=tv_session, started=now,
                   state=state, verifier=verifier)

    @property
    def client_id(self):
        # Same HA-origin for an HA-served, same-origin callback; this is a
        # proposed design, not currently a registered HTTP callback.
        return self.origin + "/"

    @property
    def redirect_uri(self):
        return self.origin + _CALLBACK

    @property
    def code_challenge(self):
        return base64.urlsafe_b64encode(
            hashlib.sha256(self._verifier.encode("ascii")).digest()
        ).rstrip(b"=").decode("ascii")

    def authorization_url(self):
        if self._used:
            raise ValueError("Owner authorization session consumed")
        params = urlencode({
            "client_id": self.client_id,
            "redirect_uri": self.redirect_uri,
            "response_type": "code",
            "state": self._state,
            "code_challenge": self.code_challenge,
            "code_challenge_method": "S256",
        })
        return f"{self.origin}/auth/authorize?{params}"

    def consume_callback(self, *, received_state, code, tv_session, now):
        """Reject invalid/late callbacks and burn the attempt on every result.

        The caller MUST verify its own authenticated HA owner session and
        actual TV-OK proof before calling this helper. No token exchange here.
        """
        if self._used:
            raise ValueError("Owner authorization replayed")
        self._used = True
        if (
            now < self.started or now - self.started >= _SESSION_SECONDS or
            not isinstance(received_state, str) or
            not hmac.compare_digest(self._state, received_state) or
            tv_session != self.tv_session or
            not isinstance(code, str) or not _CODE.fullmatch(code)
        ):
            self._verifier = ""
            raise ValueError("Owner authorization callback rejected")
        verifier = self._verifier
        self._verifier = ""
        return {"code": code, "code_verifier": verifier,
                "client_id": self.client_id, "redirect_uri": self.redirect_uri}

    def cancel(self):
        self._used = True
        self._verifier = ""

