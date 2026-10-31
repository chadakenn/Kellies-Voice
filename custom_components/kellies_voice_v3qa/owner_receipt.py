"""OFFLINE v3 encrypted HA-owner receipt. NO token or live network route.

Issuance MUST consume the actual owner-only HA WebSocket claim after verified
physical-TV remote OK. This module deliberately does not register or modify
the installed v3 config flow and cannot authorize HA services.
"""
from __future__ import annotations

import hashlib
import hmac
import math
import time

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .owner_claim import OwnerClaimRegistry
from .pairing_key_commitment import _part, _session_bytes
from .pairing_v3_verified import _derive_secret
from .pairing_v3_wire import VerifiedTv
from .validation import clean_tv_device_id

_KEY_INFO = b"KelliesVoice/v3/owner-receipt-key\x00"
_AAD_INFO = b"KelliesVoice/v3/owner-receipt-aad\x00"
_MESSAGE = b"owner-approved"  # Never a token or generic command
_DIRECTION_HA_TO_TV = b"\x02"
_SEQ = 1
_NONCE = b"\x00" * 4 + _SEQ.to_bytes(8, "big")
_CIPHER_LENGTH = len(_MESSAGE) + 16
_MAX_WAIT = 120.0


class OwnerReceiptRejected(ValueError):
    """Opaque error: never echo session, owner name, or device identity."""


def _verified_key(private_key, verified, tv: VerifiedTv, role: str):
    """Require the real local ECDH key and full verified v3 transcript."""
    if type(tv) is not VerifiedTv or getattr(verified, "role", None) != role:
        raise OwnerReceiptRejected("Unverified owner receipt role")
    try:
        if clean_tv_device_id(tv.tv_uuid) != tv.tv_uuid:
            raise ValueError()
        sid, *_ = verified.verified_transcript()
        if sid != tv.session:
            raise ValueError()
        sid_bytes = _session_bytes(sid)
        ident = tv.tv_uuid.encode("ascii")
        material = bytearray(_derive_secret(private_key, verified))
    except Exception:
        raise OwnerReceiptRejected("Unverified owner receipt session") from None
    try:
        key = bytearray(hmac.new(
            material, _KEY_INFO + _part(sid_bytes) + _part(ident)
            + _DIRECTION_HA_TO_TV + b"\x01",
            hashlib.sha256,
        ).digest())
    finally:
        for i in range(len(material)):
            material[i] = 0
    aad = (_AAD_INFO + _part(sid_bytes) + _part(ident)
           + _DIRECTION_HA_TO_TV + _SEQ.to_bytes(8, "big"))
    return key, aad


async def issue_after_owner_claim(
    *, hass, claims: OwnerClaimRegistry, flow_id: str, verified_tv: VerifiedTv,
    private_key, verified, now: float | None = None,
) -> tuple[int, bytes]:
    """One-time encrypted proof of actual signed-in HA owner approval.

    Caller MUST already have independently verified physical TV remote OK
    and must retain the live verified transcript. No owner is read from a TV
    packet, browser form, mDNS payload or client-supplied owner_id.
    """
    instant = time.monotonic() if now is None else now
    if (type(claims) is not OwnerClaimRegistry
            or type(instant) not in (float, int)
            or not math.isfinite(instant) or instant < 0):
        raise OwnerReceiptRejected("Invalid owner claim context")
    # A preflight before consumption prevents an invalid transcript from
    # burning a valid claim. Ownership is rechecked after consuming it.
    key, aad = _verified_key(private_key, verified, verified_tv, "ha")
    try:
        owner_id = claims.consume(
            flow_id=flow_id, session=verified_tv.session,
            tv_uuid=verified_tv.tv_uuid, now=instant,
        )
        if owner_id is None:
            raise OwnerReceiptRejected("Owner has not approved this TV")
        try:
            owner = await hass.auth.async_get_user(owner_id)
        except Exception:
            owner = None
        if (owner is None or getattr(owner, "id", None) != owner_id
                or getattr(owner, "is_owner", False) is not True
                or getattr(owner, "is_active", False) is not True):
            raise OwnerReceiptRejected("Verified HA owner unavailable")
        # Same nonce and key can never issue twice because the exact registry
        # claim was consumed above. Fresh session keys are unique per pairing.
        return _SEQ, AESGCM(bytes(key)).encrypt(_NONCE, _MESSAGE, aad)
    except OwnerReceiptRejected:
        raise
    except Exception:
        raise OwnerReceiptRejected("Owner receipt issuance failed") from None
    finally:
        for i in range(len(key)):
            key[i] = 0


class TvOwnerReceiptVerifier:
    """TV-side offline verifier. One packet, one use, bounded to v3 identity."""

    def __init__(self, *, private_key, verified, tv: VerifiedTv,
                 now: float | None = None, ttl_seconds: float = 60.0):
        instant = time.monotonic() if now is None else now
        if (type(instant) not in (float, int) or not math.isfinite(instant)
                or instant < 0 or type(ttl_seconds) not in (float, int)
                or not math.isfinite(ttl_seconds)
                or not 0 < ttl_seconds <= _MAX_WAIT):
            raise OwnerReceiptRejected("Invalid owner receipt lifetime")
        self._key, self._aad = _verified_key(private_key, verified, tv, "tv")
        self._expires = instant + ttl_seconds
        self._closed = False

    def accept(self, sequence: int, ciphertext: bytes,
               *, now: float | None = None) -> bool:
        instant = time.monotonic() if now is None else now
        if (self._closed or type(instant) not in (int, float)
                or not math.isfinite(instant) or instant >= self._expires
                or instant < 0 or type(sequence) is not int
                or sequence != _SEQ or type(ciphertext) is not bytes
                or len(ciphertext) != _CIPHER_LENGTH):
            self.close()
            raise OwnerReceiptRejected("Invalid or expired owner receipt")
        try:
            payload = AESGCM(bytes(self._key)).decrypt(
                _NONCE, ciphertext, self._aad)
            if not hmac.compare_digest(payload, _MESSAGE):
                raise OwnerReceiptRejected("Unrecognized owner receipt")
            return True
        except Exception:
            raise OwnerReceiptRejected("Owner receipt invalid") from None
        finally:
            # Even an invalid packet burns this verifier; no second chance.
            self.close()

    def close(self):
        if self._closed:
            return
        self._closed = True
        for i in range(len(self._key)):
            self._key[i] = 0

    @property
    def closed(self):
        return self._closed
