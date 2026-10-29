"""#193 proposed canonical OAuth candidate envelope for a sealed TV handoff.

NO active OAuth exchange, HTTP endpoint, token delivery or storage in this
module. A candidate may ONLY travel inside the previously owner-/TV-approved
P-256/AES-GCM channel. This codec and its local SHA-256 equality handle do
not authenticate owner consent, or make private-LAN plaintext HTTP safe.

Exact binary layout matches the Android PairingCredentialCandidateCodec in
draft #225: magic; six length-prefixed UTF-8 fields; two big-endian int64
epoch-millisecond timestamps. Rejects candidates exceeding the existing
1024-byte HA offer ceiling. Larger tokens require a reviewed new protocol,
not truncation.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import struct

from .pairing_owner_oauth import _origin
from .pairing_token_exchange import _valid_token
from .pairing_verification import _session_bytes
from .validation import clean_tv_device_id

MAGIC = b"KVPair/credential-candidate/v1\x00"
_BIND = b"KVPair/v1/exact-candidate-binding\x00"
MAX_BYTES = 1024


@dataclass(frozen=True)
class CredentialCandidate:
    origin: str
    client_id: str
    access_token: str = field(repr=False)
    refresh_token: str = field(repr=False)
    session: str
    tv_uuid: str
    access_expires_at_ms: int
    staged_at_ms: int


def _validate(c: CredentialCandidate) -> None:
    if not isinstance(c, CredentialCandidate):
        raise ValueError("Invalid candidate type")
    if (not isinstance(c.origin, str) or c.origin != _origin(c.origin)
            or c.client_id != c.origin + "/"):
        raise ValueError("Noncanonical Home Assistant client origin")
    if not _valid_token(c.access_token) or not _valid_token(c.refresh_token):
        raise ValueError("Invalid candidate credentials")
    _session_bytes(c.session)
    if c.tv_uuid != clean_tv_device_id(c.tv_uuid):
        raise ValueError("Noncanonical TV identity")
    if (type(c.access_expires_at_ms) is not int
            or type(c.staged_at_ms) is not int
            or c.staged_at_ms < 1
            or not c.staged_at_ms < c.access_expires_at_ms
            or c.access_expires_at_ms - c.staged_at_ms > 86_400_000):
        raise ValueError("Invalid candidate lifetime")


def _field(text: str) -> bytes:
    if not isinstance(text, str):
        raise ValueError("Missing candidate field")
    encoded = text.encode("utf-8", errors="strict")
    if not 1 <= len(encoded) <= 2048:
        raise ValueError("Candidate field exceeds bounds")
    return struct.pack(">H", len(encoded)) + encoded


def encode_candidate(candidate: CredentialCandidate) -> bytes:
    _validate(candidate)
    data = (MAGIC
            + b"".join(_field(value) for value in (
                candidate.origin, candidate.client_id, candidate.access_token,
                candidate.refresh_token, candidate.session, candidate.tv_uuid))
            + struct.pack(">qq", candidate.access_expires_at_ms,
                          candidate.staged_at_ms))
    if len(data) > MAX_BYTES:
        raise ValueError("Candidate exceeds encrypted offer ceiling")
    return data


def decode_candidate(data: bytes) -> CredentialCandidate:
    if type(data) is not bytes or len(data) < len(MAGIC) + 28 or len(data) > MAX_BYTES:
        raise ValueError("Invalid candidate bytes")
    if not data.startswith(MAGIC):
        raise ValueError("Unsupported candidate version")
    offset = len(MAGIC)
    values: list[str] = []
    for _ in range(6):
        if offset + 2 > len(data):
            raise ValueError("Truncated candidate field length")
        length = struct.unpack_from(">H", data, offset)[0]
        offset += 2
        if not 1 <= length <= 2048 or offset + length > len(data) - 16:
            raise ValueError("Invalid candidate field size")
        value = data[offset:offset + length]
        offset += length
        try:
            text = value.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            raise ValueError("Invalid candidate encoding") from None
        if text.encode("utf-8") != value:
            raise ValueError("Noncanonical candidate encoding")
        values.append(text)
    if len(data) - offset != 16:
        raise ValueError("Unexpected trailing candidate bytes")
    expires, started = struct.unpack_from(">qq", data, offset)
    candidate = CredentialCandidate(*values, expires, started)
    if encode_candidate(candidate) != data:
        raise ValueError("Candidate not canonical")
    return candidate


def fingerprint(exact_candidate_bytes: bytes) -> bytes:
    """Local, domain-separated SHA256 equality handle, never a credential."""
    if (type(exact_candidate_bytes) is not bytes
            or not 1 <= len(exact_candidate_bytes) <= MAX_BYTES):
        raise ValueError("Invalid exact candidate size")
    return hashlib.sha256(
        _BIND + struct.pack(">H", len(exact_candidate_bytes))
        + exact_candidate_bytes
    ).digest()
