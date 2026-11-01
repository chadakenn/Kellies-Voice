"""Offline v3 transcript-bound encrypted scoped HA token delivery.

Not registered as an endpoint. This does NOT bypass the required owner
approval, per-TV least-privilege account or real capability verification.
Nothing here creates a token or activates a TV login.
"""
from __future__ import annotations
import hashlib
import hmac
import json
import math
import time
from dataclasses import dataclass
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from .pairing_key_commitment import _part, _session_bytes
from .pairing_v3_verified import _derive_secret
from .pairing_v3_wire import VerifiedTv
from .scoped_login_issuer import IssuedTvCredential

_KEY = b"KelliesVoice/v3/scoped-token-v1/key\x00"
_AAD = b"KelliesVoice/v3/scoped-token-v1/aad\x00"
_MESSAGES = (b"staged", b"commit-ready", b"activated")
_MAX_PACKET = 12288


class TokenHandoffDenied(ValueError):
    """Opaque. No token fields, URL or TV identifiers exposed."""


@dataclass(frozen=True)
class TvTokenGrant:
    tv_uuid: str
    client_id: str
    token_id: str
    refresh_token: str
    access_token: str
    expires_in: int


def _key(private_key, verified, tv: VerifiedTv, role, direction):
    if (type(tv) is not VerifiedTv or getattr(verified, "role", None) != role
            or direction not in (1, 2)):
        raise TokenHandoffDenied("Invalid v3 TV channel identity")
    try:
        session, *_ = verified.verified_transcript()
        if session != tv.session:
            raise ValueError()
        sid = _session_bytes(session)
        uuid = tv.tv_uuid.encode("ascii")
        # Canonical UUID validation: no TV-provided aliases.
        from uuid import UUID
        if str(UUID(tv.tv_uuid)) != tv.tv_uuid or UUID(tv.tv_uuid).version != 4:
            raise ValueError()
        secret = bytearray(_derive_secret(private_key, verified))
    except Exception:
        raise TokenHandoffDenied("Unverified v3 token transport") from None
    try:
        derived = bytearray(hmac.new(
            secret, _KEY + _part(sid) + _part(uuid)
            + _part(bytes((direction,))) + b"\x01",
            hashlib.sha256).digest())
    finally:
        for i in range(len(secret)):
            secret[i] = 0
    return derived, sid, uuid


def _crypt_aad(sid, uuid, direction, sequence):
    return (_AAD + _part(sid) + _part(uuid)
            + bytes((direction,)) + sequence.to_bytes(8, "big"))


def _seal(key, sid, uuid, direction, seq, data):
    return AESGCM(bytes(key)).encrypt(
        bytes(4) + seq.to_bytes(8, "big"), data,
        _crypt_aad(sid, uuid, direction, seq))


def _open(key, sid, uuid, direction, seq, encrypted):
    return AESGCM(bytes(key)).decrypt(
        bytes(4) + seq.to_bytes(8, "big"), encrypted,
        _crypt_aad(sid, uuid, direction, seq))


class _Base:
    def __init__(self, *, private_key, verified, tv, role,
                 now=None, ttl_seconds=60):
        instant = time.monotonic() if now is None else now
        if (type(instant) not in (int, float)
                or not math.isfinite(instant) or instant < 0
                or type(ttl_seconds) not in (int, float)
                or not 0 < ttl_seconds <= 120
                or not math.isfinite(ttl_seconds)):
            raise TokenHandoffDenied("Unbounded TV credential handoff")
        self._send_direction, self._receive_direction = (
            (2, 1) if role == "ha" else (1, 2))
        self._send, self._sid, self._uuid = _key(
            private_key, verified, tv, role, self._send_direction)
        self._receive, _, _ = _key(
            private_key, verified, tv, role, self._receive_direction)
        self._expiry = instant + ttl_seconds
        self._phase = 0
        self._closed = False
        self.tv_uuid = tv.tv_uuid

    def _check(self, phase, instant):
        now = time.monotonic() if instant is None else instant
        if (self._closed or self._phase != phase
                or type(now) not in (int, float)
                or not math.isfinite(now) or now < 0
                or now >= self._expiry):
            self.close()
            raise TokenHandoffDenied("TV enrollment state unavailable")

    def _enc(self, seq, data):
        return _seal(self._send, self._sid, self._uuid,
                     self._send_direction, seq, data)

    def _dec(self, seq, data):
        if (type(data) is not bytes or len(data) < 16
                or len(data) > _MAX_PACKET):
            self.close()
            raise TokenHandoffDenied("Invalid encrypted TV enrollment")
        try:
            return _open(self._receive, self._sid, self._uuid,
                         self._receive_direction, seq, data)
        except Exception:
            self.close()
            raise TokenHandoffDenied("TV enrollment signature mismatch") from None

    def close(self):
        if self._closed:
            return
        self._closed = True
        for key in (self._send, self._receive):
            for i in range(len(key)):
                key[i] = 0


class HaTokenHandoff(_Base):
    """HA stages, validates signed stage ACK, and verifies TV activation."""

    def __init__(self, **kw):
        super().__init__(role="ha", **kw)

    def seal_recorded_provisional(self, issued, recorded_lease, *, now=None):
        self._check(0, now)
        if (type(issued) is not IssuedTvCredential
                or recorded_lease.state != "issued"
                or recorded_lease.tv_uuid != self.tv_uuid
                or recorded_lease.owner_id != issued.lease.owner_id
                or recorded_lease.tv_user_id != issued.lease.tv_user_id
                or recorded_lease.client_id != issued.lease.client_id
                or recorded_lease.nonce != issued.lease.nonce
                or not recorded_lease.token_id
                or type(issued.refresh_token) is not str
                or type(issued.access_token) is not str
                or not issued.refresh_token or not issued.access_token
                or not 30 <= issued.access_token_seconds <= 86400):
            self.close()
            raise TokenHandoffDenied("TV grant not durably recorded")
        payload = json.dumps(
            {"v": 1, "tv": self.tv_uuid,
             "client": recorded_lease.client_id, "id": recorded_lease.token_id,
             "refresh": issued.refresh_token, "access": issued.access_token,
             "expires": issued.access_token_seconds},
            separators=(",", ":"), sort_keys=True,
        ).encode()
        if len(payload) > _MAX_PACKET - 16:
            self.close()
            raise TokenHandoffDenied("TV grant too large")
        packet = self._enc(1, payload)
        self._phase = 1
        return 1, packet

    def accept_stage_ack(self, sequence, packet, *, now=None):
        self._check(1, now)
        if type(sequence) is not int or sequence != 1 or self._dec(1, packet) != b"staged":
            self.close()
            raise TokenHandoffDenied("TV stage ACK denied")
        self._phase = 2

    def seal_commit_ready(self, *, now=None):
        self._check(2, now)
        self._phase = 3
        return 2, self._enc(2, b"commit-ready")

    def accept_activation_ack(self, sequence, packet, *, now=None):
        self._check(3, now)
        if type(sequence) is not int or sequence != 2 or self._dec(2, packet) != b"activated":
            self.close()
            raise TokenHandoffDenied("TV activation ACK denied")
        self._phase = 4
        self.close()
        return True


class TvTokenHandoff(_Base):
    """One-use TV receipt. Its grant must first be staged into Keystore."""

    def __init__(self, *, expected_ha_origin: str, **kw):
        from .scoped_login_issuer import _trusted_origin
        self._trusted_origin = _trusted_origin(expected_ha_origin)
        super().__init__(role="tv", **kw)

    def open_provisional(self, sequence, encrypted, *, now=None):
        self._check(0, now)
        if type(sequence) is not int or sequence != 1:
            self.close()
            raise TokenHandoffDenied("Bad provisional sequence")
        try:
            doc = json.loads(self._dec(1, encrypted))
            if (type(doc) is not dict
                    or set(doc) != {"v", "tv", "client", "id", "refresh", "access", "expires"}
                    or doc["v"] != 1 or doc["tv"] != self.tv_uuid
                    or type(doc["client"]) is not str
                    or not doc["client"].startswith(
                        self._trusted_origin + "/kellies-voice/tv/"
                        + self.tv_uuid + "/attempt/")
                    or len(doc["client"].rsplit("/", 1)[-1]) != 32
                    or not all(ch in "0123456789abcdef"
                               for ch in doc["client"].rsplit("/", 1)[-1])
                    or type(doc["id"]) is not str or not doc["id"]
                    or len(doc["id"]) > 128
                    or not all(ch.isalnum() or ch in "-_" for ch in doc["id"])
                    or not all(type(doc[x]) is str and doc[x]
                               and len(doc[x]) <= 8192
                               for x in ("refresh", "access"))
                    or type(doc["expires"]) is not int
                    or not 30 <= doc["expires"] <= 86400):
                raise ValueError()
            grant = TvTokenGrant(
                doc["tv"], doc["client"], doc["id"],
                doc["refresh"], doc["access"], doc["expires"])
        except Exception:
            self.close()
            raise TokenHandoffDenied("Invalid scoped TV grant") from None
        self._phase = 1
        return grant

    def stage_ack(self, *, now=None):
        self._check(1, now)
        self._phase = 2
        return 1, self._enc(1, b"staged")

    def accept_commit_ready(self, sequence, encrypted, *, now=None):
        self._check(2, now)
        if type(sequence) is not int or sequence != 2 or self._dec(2, encrypted) != b"commit-ready":
            self.close()
            raise TokenHandoffDenied("Missing authenticated commit-ready")
        self._phase = 3
        return True

    def activation_ack(self, *, now=None):
        self._check(3, now)
        self._phase = 4
        packet = self._enc(2, b"activated")
        self.close()
        return 2, packet
