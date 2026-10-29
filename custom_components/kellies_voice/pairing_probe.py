"""Strict, read-only discovery probe for a future owner-initiated #193 setup.

This file does NOT provision credentials or register a config entry.
Do not call a probing function automatically from arbitrary zeroconf events:
discovery is untrusted and this probe is intended for an explicit HA user action.
"""

from __future__ import annotations

from dataclasses import dataclass
import ipaddress
import json
import re
from typing import Any

_RFC1918 = tuple(
    ipaddress.ip_network(block)
    for block in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
)
_SESSION = re.compile(r"[A-Za-z0-9_-]{22}\Z")
_PATH = "/v1/pair/capabilities"
_MAX_BODY = 512


@dataclass(frozen=True)
class PairingCandidate:
    address: str
    port: int
    session: str

    @property
    def url(self) -> str:
        # All parts validated before constructing the URL, never from TXT URL.
        return f"http://{self.address}:{self.port}{_PATH}"


def candidate_from_discovery(
    address: str, port: int, properties: dict[str, Any]
) -> PairingCandidate:
    """Reject hostnames, local services, non-private networks and bad metadata."""
    try:
        ip = ipaddress.ip_address(address)
    except ValueError as err:
        raise ValueError("Untrusted pairing discovery address") from err
    if not isinstance(ip, ipaddress.IPv4Address) or not any(
        ip in network for network in _RFC1918
    ):
        raise ValueError("Untrusted pairing discovery address")
    if type(port) is not int or not 1024 <= port <= 65535:
        raise ValueError("Invalid pairing port")
    if not isinstance(properties, dict):
        raise ValueError("Invalid pairing advertisement")

    def text(key: str) -> str:
        value = properties.get(key)
        if isinstance(value, bytes):
            try:
                return value.decode("ascii")
            except UnicodeDecodeError:
                return ""
        return value if isinstance(value, str) else ""

    nonce = text("session")
    if (
        text("mode") != "discovery-only"
        or text("protocol") != "1"
        or not _SESSION.fullmatch(nonce)
    ):
        raise ValueError("Unsupported pairing protocol")
    return PairingCandidate(str(ip), port, nonce)


async def probe_candidate(candidate: PairingCandidate, *, _session_factory=None) -> bool:
    """Check public capabilities with an isolated, no-cookies/no-auth client.

    A future owner-approved HA flow may call this after explicit selection;
    never trigger it automatically from arbitrary mDNS packets.
    _session_factory is test injection only, not for callers in HA production.
    """
    if candidate_from_discovery(
        candidate.address,
        candidate.port,
        {"mode": "discovery-only", "protocol": "1", "session": candidate.session},
    ) != candidate:
        raise ValueError("Invalid pairing candidate")

    if _session_factory is None:
        import aiohttp

        def _session_factory():
            # Never inherit the user's or HA's active HTTP session, cookies,
            # bearer tokens, environment proxy, or Basic authentication.
            return aiohttp.ClientSession(
                cookie_jar=aiohttp.DummyCookieJar(),
                headers={},
                trust_env=False,
                timeout=aiohttp.ClientTimeout(total=3),
            )

    async with _session_factory() as http_session:
        async with http_session.get(
            candidate.url, allow_redirects=False
        ) as response:
            if response.status != 200:
                return False
            raw = await response.content.read(_MAX_BODY + 1)
            if len(raw) > _MAX_BODY:
                return False
            try:
                doc = json.loads(raw)
            except (ValueError, UnicodeDecodeError):
                return False

    return (
        type(doc) is dict
        and doc == {
            "protocol": 1,
            "mode": "discovery-only",
            "authorized": False,
            "session": candidate.session,
        }
    )
