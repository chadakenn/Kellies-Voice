"""Offline transcript-bound AES-GCM QA ping ONLY. No HA login or endpoints.

A matching P-256 commitment transcript and TV proof is not an HA owner
authorization. The real owner approval must be separately enforced by a
trusted server before any live action can ever use this protocol. These
functions only encrypt two fixed test messages; never credentials.
"""
from __future__ import annotations
import hashlib
import hmac
import math
import time
from uuid import UUID
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .pairing_key_commitment import _part, _session_bytes
from .pairing_v3_verified import _derive_secret

_KEY_DOMAIN = b"KelliesVoice/v3/qa-aead/key\x00"
_AAD_DOMAIN = b"KelliesVoice/v3/qa-aead/aad\x00"
_DIRECTIONS = {"tv": (1, 2), "ha": (2, 1)}
_ALLOWED = frozenset((b"qa-ping", b"qa-pong"))
_MAX_SEQ = (1 << 64) - 1


class V3QaChannelRejected(ValueError):
    """No untrusted packet, device, or secret is exposed through errors."""


def _identity(verified, tv_uuid):
    try:
        parsed = UUID(tv_uuid)
        if (type(tv_uuid) is not str or parsed.version != 4
                or parsed.variant != "specified in RFC 4122"
                or str(parsed) != tv_uuid):
            raise ValueError()
        sid, *_ = verified.verified_transcript()
        return _session_bytes(sid), tv_uuid.encode("ascii")
    except (ValueError, AttributeError, TypeError, UnicodeError):
        raise V3QaChannelRejected("Invalid verified QA identity") from None


def _subkey(prk, sid, tv_id, direction):
    info = (_KEY_DOMAIN + _part(sid) + _part(tv_id)
            + _part(bytes((direction,))) + b"\x01")
    return bytearray(hmac.new(prk, info, hashlib.sha256).digest())


class V3QaPingChannel:
    """In-memory QA channel. Keys are ephemeral and never persisted."""

    def __init__(self, *, private_key, verified, tv_uuid,
                 ttl_seconds=60.0, now=None):
        instant = time.monotonic() if now is None else now
        if (type(ttl_seconds) not in (int, float)
                or not math.isfinite(ttl_seconds)
                or not 0 < ttl_seconds <= 120
                or type(instant) not in (int, float)
                or not math.isfinite(instant) or instant < 0):
            raise V3QaChannelRejected("Invalid QA lifetime")
        sid, tv_id = _identity(verified, tv_uuid)
        role = getattr(verified, "role", None)
        if role not in _DIRECTIONS:
            raise V3QaChannelRejected("Invalid QA role")
        try:
            prk = bytearray(_derive_secret(private_key, verified))
        except Exception:
            raise V3QaChannelRejected("Unverified QA key agreement") from None
        try:
            send_dir, recv_dir = _DIRECTIONS[role]
            self._send_key = _subkey(prk, sid, tv_id, send_dir)
            self._recv_key = _subkey(prk, sid, tv_id, recv_dir)
        finally:
            for i in range(len(prk)):
                prk[i] = 0
        self._sid = sid
        self._tv_id = tv_id
        self._send_dir = send_dir
        self._recv_dir = recv_dir
        self._expires_at = instant + ttl_seconds
        self._send_sequence = 0
        self._recv_sequence = 0
        self._closed = False

    def _check(self, now):
        instant = time.monotonic() if now is None else now
        if (type(instant) not in (int, float)
                or not math.isfinite(instant)
                or instant < 0 or self._closed or instant >= self._expires_at):
            self.close()
            raise V3QaChannelRejected("QA channel expired or closed")

    def _aad(self, direction, sequence):
        return (_AAD_DOMAIN + _part(self._sid) + _part(self._tv_id)
                + bytes((direction,)) + sequence.to_bytes(8, "big"))

    @staticmethod
    def _nonce(sequence):
        # Directional keys prevent cross-direction nonce reuse.
        return bytes(4) + sequence.to_bytes(8, "big")

    def seal(self, payload: bytes, *, now=None):
        self._check(now)
        if type(payload) is not bytes or payload not in _ALLOWED:
            raise V3QaChannelRejected("Only QA ping/pong allowed")
        if self._send_sequence >= _MAX_SEQ:
            self.close()
            raise V3QaChannelRejected("QA sequence exhausted")
        seq = self._send_sequence + 1
        try:
            ct = AESGCM(bytes(self._send_key)).encrypt(
                self._nonce(seq), payload, self._aad(self._send_dir, seq))
        except Exception:
            self.close()
            raise V3QaChannelRejected("QA encryption failed") from None
        self._send_sequence = seq
        return seq, ct

    def open(self, sequence: int, ciphertext: bytes, *, now=None):
        self._check(now)
        if (type(sequence) is not int
                or sequence != self._recv_sequence + 1
                or sequence > _MAX_SEQ or type(ciphertext) is not bytes
                or len(ciphertext) != 23):
            self.close()
            raise V3QaChannelRejected("Invalid QA packet")
        try:
            payload = AESGCM(bytes(self._recv_key)).decrypt(
                self._nonce(sequence), ciphertext,
                self._aad(self._recv_dir, sequence))
        except Exception:
            self.close()
            raise V3QaChannelRejected("QA authentication failed") from None
        if payload not in _ALLOWED:
            self.close()
            raise V3QaChannelRejected("Unexpected decrypted QA payload")
        self._recv_sequence = sequence
        return payload

    def close(self):
        if self._closed:
            return
        self._closed = True
        for key in (self._send_key, self._recv_key):
            for i in range(len(key)):
                key[i] = 0

    @property
    def closed(self):
        return self._closed
