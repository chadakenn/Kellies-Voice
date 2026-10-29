"""Draft KVPair/v3 commitment barrier; no networking, OAuth or credentials.

The v1/v2 LAN exchange reveals HA's ephemeral ECDH public key before
the other party commits to its own key. A last-mover MITM can search for
a matching human verification code. This separate v3 primitive enforces
a commitment phase BEFORE either peer reveals its ephemeral public key.

A future *reviewed* wire protocol must use this barrier on both peers
and derive a version-separated SAS from the verified v3 transcript.
Do not plug only one side into existing v2 /pair/offer: fail closed.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import os
import re

_DOMAIN = b"KelliesVoice/v3/key-commit\x00"
_SESSION = re.compile(r"[A-Za-z0-9_-]{22}\Z")
_ROLES = frozenset(("tv", "ha"))
_NONCE_BYTES = 32


def _part(data: bytes) -> bytes:
    if len(data) > 65535:
        raise ValueError("Commit part too large")
    return len(data).to_bytes(2, "big") + data


def _session_bytes(session: str) -> bytes:
    if type(session) is not str or not _SESSION.fullmatch(session):
        raise ValueError("Invalid v3 session")
    try:
        raw = base64.urlsafe_b64decode(session + "==")
    except (ValueError, base64.binascii.Error) as exc:
        raise ValueError("Invalid v3 session") from exc
    if (len(raw) != 16 or
            base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii") != session):
        raise ValueError("Noncanonical v3 session")
    return raw


def commitment(role: str, session: str, public_der: bytes, nonce: bytes) -> bytes:
    """Commit to a role, public key, random secret nonce and exact session.

    public_der must subsequently pass strict P-256 validation in the
    authenticated key-agreement layer. This primitive binds its raw bytes,
    without interpreting peer key material.
    """
    if role not in _ROLES or type(role) is not str:
        raise ValueError("Invalid v3 peer role")
    sid = _session_bytes(session)
    if type(public_der) is not bytes or not 70 <= len(public_der) <= 150:
        raise ValueError("Invalid v3 public-key bytes")
    if type(nonce) is not bytes or len(nonce) != _NONCE_BYTES:
        raise ValueError("Invalid v3 nonce")
    digest = hashlib.sha256()
    digest.update(_DOMAIN)
    for value in (role.encode("ascii"), sid, public_der, nonce):
        digest.update(_part(value))
    return digest.digest()


class CommitmentSession:
    """Single-use state machine: commit -> peer commit -> reveal -> verify.

    Neither side is permitted to reveal its ephemeral public key before it
    has received the other's immutable SHA-256 commitment. An invalid
    reveal closes the entire session; it cannot be retried until matched.
    """

    def __init__(self, role: str, session: str, public_der: bytes,
                 nonce: bytes | None = None) -> None:
        actual_nonce = os.urandom(_NONCE_BYTES) if nonce is None else nonce
        self._local_commit = commitment(role, session, public_der, actual_nonce)
        self._role = role
        self._session = session
        self._key = public_der
        self._nonce = bytearray(actual_nonce)
        self._peer_commit: bytes | None = None
        self._peer_key: bytes | None = None
        self._peer_nonce: bytes | None = None
        self._revealed = False
        self._closed = False

    @property
    def public_commit(self) -> bytes:
        return self._local_commit

    def receive_peer_commit(self, value: bytes) -> None:
        if (self._closed or self._peer_commit is not None
                or self._revealed or type(value) is not bytes
                or len(value) != 32
                or hmac.compare_digest(value, self._local_commit)):
            raise ValueError("Unacceptable or replayed v3 commitment")
        self._peer_commit = value

    def reveal(self) -> tuple[bytes, bytes]:
        if self._closed or self._peer_commit is None or self._revealed:
            raise ValueError("A peer commitment must arrive before reveal")
        self._revealed = True
        return self._key, bytes(self._nonce)

    def receive_peer_reveal(self, public_der: bytes, nonce: bytes) -> None:
        if (self._closed or self._peer_commit is None
                or not self._revealed or self._peer_key is not None):
            raise ValueError("Reveal out of sequence or reused")
        peer_role = "ha" if self._role == "tv" else "tv"
        try:
            candidate = commitment(peer_role, self._session, public_der, nonce)
        except (ValueError, TypeError):
            self.close()
            raise ValueError("Invalid v3 peer reveal") from None
        if (not hmac.compare_digest(candidate, self._peer_commit)
                or public_der == self._key):
            self.close()
            raise ValueError("Uncommitted or reflected v3 peer reveal")
        self._peer_key = public_der
        self._peer_nonce = nonce

    @property
    def ready(self) -> bool:
        return not self._closed and self._peer_key is not None

    def verified_transcript(self) -> tuple[str, bytes, bytes, bytes, bytes]:
        """Only exposed after both commits and both reveals were verified."""
        if not self.ready:
            raise ValueError("v3 commitment comparison incomplete")
        return (self._session, self._key, self._peer_key, bytes(self._nonce),
                self._peer_nonce)

    def close(self) -> None:
        self._closed = True
        for index in range(len(self._nonce)):
            self._nonce[index] = 0
        self._peer_nonce = None
        self._peer_key = None
