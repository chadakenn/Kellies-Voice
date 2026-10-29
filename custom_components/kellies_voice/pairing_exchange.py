"""Uncredentialed two-screen pairing preview, never an HA login.

No saved HA user session, bearer token, OAuth code, or config entry is sent
to the TV. Initiation is restricted to a user-confirmed HA config flow.
"""
from __future__ import annotations

import base64
from dataclasses import dataclass
import hmac
import json
import re

from .pairing_probe import PairingCandidate, candidate_from_discovery
from .pairing_verification import approved_proof, calculate, fresh_keypair, public_der
from .validation import clean_tv_device_id

_KEY_RE = re.compile(r"[A-Za-z0-9_-]{110,200}\Z")


@dataclass
class PreviewAttempt:
    candidate: PairingCandidate
    private: object
    ha_der: bytes
    tv_der: bytes
    code: str


@dataclass
class VerifiedTVOK:
    """Result of a cryptographically verified physical TV-OK.

    The TV UUID is bound into the ECDH proof; it is never taken from the
    discovery session nonce.
    """
    tv_uuid: str
    session: str


def _client():
    import aiohttp

    return aiohttp.ClientSession(
        cookie_jar=aiohttp.DummyCookieJar(),
        headers={}, trust_env=False, timeout=aiohttp.ClientTimeout(total=3),
    )


async def _read_small_json(response):
    if response.status != 200:
        raise ValueError("Pairing preview request rejected")
    chunks = []
    size = 0
    while True:
        part = await response.content.read(min(256, 513 - size))
        if not part:
            break
        size += len(part)
        if size > 512:
            raise ValueError("Oversized pairing response")
        chunks.append(part)
    try:
        doc = json.loads(b"".join(chunks))
    except (ValueError, UnicodeDecodeError) as error:
        raise ValueError("Invalid pairing response") from error
    if type(doc) is not dict:
        raise ValueError("Invalid pairing response")
    return doc


async def begin_preview(candidate: PairingCandidate, *, _session_factory=None):
    """Send a public P-256 key only, after the owner opts in on the HA screen."""
    candidate_from_discovery(candidate.address, candidate.port, {
        "mode": "discovery-only", "protocol": "1", "session": candidate.session,
    })
    private = fresh_keypair()
    ha_der = public_der(private)
    factory = _session_factory or _client
    async with factory() as client:
        async with client.post(
            f"http://{candidate.address}:{candidate.port}/v1/pair/offer",
            json={
                "session": candidate.session,
                "ha_key": base64.urlsafe_b64encode(ha_der).rstrip(b"=").decode("ascii"),
            },
            allow_redirects=False,
        ) as response:
            doc = await _read_small_json(response)
    if set(doc) != {"session", "authorized", "tv_key"} or (
        doc["session"] != candidate.session or doc["authorized"] is not False
    ):
        raise ValueError("Pairing preview is not authorized")
    encoded = doc["tv_key"]
    if not isinstance(encoded, str) or not _KEY_RE.fullmatch(encoded):
        raise ValueError("Invalid TV public key")
    try:
        tv_der = base64.urlsafe_b64decode(encoded + "==")
    except (ValueError, base64.binascii.Error) as error:
        raise ValueError("Invalid TV public key") from error
    code = calculate(
        private, local_is_tv=False, session=candidate.session,
        tv_key=tv_der, ha_key=ha_der,
    )
    return PreviewAttempt(candidate, private, ha_der, tv_der, code)


async def confirm_tv_ok(attempt: PreviewAttempt, *, _session_factory=None) -> VerifiedTVOK | None:
    """Verify actual TV-OK ECDH proof and return the bound TV UUID.

    Returns None while waiting. Raises on mismatch. The returned UUID is
    taken from the TV's authenticated status response, validated as an
    RFC4122 v4, and verified to be bound into the proof. It is never the
    discovery session nonce.
    """
    candidate = attempt.candidate
    candidate_from_discovery(candidate.address, candidate.port, {
        "mode": "discovery-only", "protocol": "1", "session": candidate.session,
    })
    factory = _session_factory or _client
    async with factory() as client:
        async with client.get(
            f"http://{candidate.address}:{candidate.port}"
            f"/v1/pair/status?session={candidate.session}",
            allow_redirects=False,
        ) as response:
            doc = await _read_small_json(response)
    if doc == {"approved": False}:
        return None
    if set(doc) != {"approved", "proof", "tv_uuid"} or doc["approved"] is not True:
        raise ValueError("Invalid TV confirmation response")
    proof = doc["proof"]
    if not isinstance(proof, str) or not re.fullmatch(r"[A-Za-z0-9_-]{22}", proof):
        raise ValueError("Invalid TV confirmation proof")
    try:
        tv_uuid = clean_tv_device_id(doc["tv_uuid"])
    except (ValueError, TypeError):
        raise ValueError("Invalid TV UUID in confirmation") from None
    if tv_uuid == candidate.session:
        raise ValueError("Discovery session cannot be used as TV UUID")
    expected = approved_proof(
        attempt.private, local_is_tv=False, session=candidate.session,
        tv_key=attempt.tv_der, ha_key=attempt.ha_der,
        tv_uuid=tv_uuid,
    )
    if not hmac.compare_digest(expected, proof):
        raise ValueError("TV confirmation cryptographic proof mismatch")
    return VerifiedTVOK(tv_uuid=tv_uuid, session=candidate.session)
