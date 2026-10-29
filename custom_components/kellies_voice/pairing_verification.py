"""#193 independent transcript-bound visual comparison, no tokens or network.

This crypto helper deliberately has NO effect on Home Assistant config flows.
After separate authenticated owner authorization, the TV and Home Assistant
must display the SAME derived 8-digit code, and the owner must compare them
and press OK on the TV. ECDH by itself authenticates nobody: a malicious LAN
peer can create a valid keypair. Never auto-approve from this code alone.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import re
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from cryptography.hazmat.primitives.asymmetric.ec import EllipticCurvePrivateKey

_DOMAIN = b"KVPair/v1/transcript"
_INFO = b"KelliesVoice/v1/visual-compare"
_SESSION = re.compile(r"[A-Za-z0-9_-]{22}\Z")


def fresh_keypair() -> "EllipticCurvePrivateKey":
    from cryptography.hazmat.primitives.asymmetric import ec
    return ec.generate_private_key(ec.SECP256R1())


def public_der(private_key: "EllipticCurvePrivateKey") -> bytes:
    from cryptography.hazmat.primitives import serialization
    return private_key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )


def _session_bytes(session: str) -> bytes:
    if not isinstance(session, str) or not _SESSION.fullmatch(session):
        raise ValueError("Invalid pairing session")
    try:
        decoded = base64.urlsafe_b64decode(session + "==")
    except (ValueError, base64.binascii.Error) as error:
        raise ValueError("Invalid pairing session") from error
    if (len(decoded) != 16 or
        base64.urlsafe_b64encode(decoded).rstrip(b"=").decode("ascii") != session):
        raise ValueError("Noncanonical pairing session")
    return decoded


def _load_p256(encoded: bytes):
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.serialization import load_der_public_key
    if not isinstance(encoded, bytes) or not 70 <= len(encoded) <= 150:
        raise ValueError("Invalid pairing public key")
    try:
        key = load_der_public_key(encoded)
    except (ValueError, TypeError) as error:
        raise ValueError("Invalid pairing public key") from error
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(
        key.curve, ec.SECP256R1
    ):
        raise ValueError("Only P-256 pairing keys are supported")
    from cryptography.hazmat.primitives import serialization
    canonical = key.public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    if not hmac.compare_digest(canonical, encoded):
        raise ValueError("Noncanonical pairing public key")
    return key


def _part(data: bytes) -> bytes:
    if len(data) > 65535:
        raise ValueError("Oversized pairing transcript field")
    return len(data).to_bytes(2, "big") + data


def calculate(
    own_private: "EllipticCurvePrivateKey",
    *,
    local_is_tv: bool,
    session: str,
    tv_key: bytes,
    ha_key: bytes,
) -> str:
    """Produce an ordered TV/HA, session-bound code; do not authenticate by itself."""
    from cryptography.hazmat.primitives.asymmetric import ec
    if not isinstance(own_private, ec.EllipticCurvePrivateKey) or not isinstance(
        own_private.curve, ec.SECP256R1
    ) or type(local_is_tv) is not bool:
        raise ValueError("Invalid ephemeral pairing role or key")
    sid = _session_bytes(session)
    tv = _load_p256(tv_key)
    ha = _load_p256(ha_key)
    if hmac.compare_digest(tv_key, ha_key):
        raise ValueError("TV and Home Assistant need separate ephemeral keys")
    own_pub = public_der(own_private)
    expected_own = tv_key if local_is_tv else ha_key
    if not hmac.compare_digest(own_pub, expected_own):
        raise ValueError("Pairing peer role/key mismatch")
    other_pub = ha if local_is_tv else tv
    shared = own_private.exchange(ec.ECDH(), other_pub)

    transcript = _DOMAIN + _part(sid) + _part(tv_key) + _part(ha_key)
    salt = hashlib.sha256(transcript).digest()
    prk = hmac.new(salt, shared, hashlib.sha256).digest()  # HKDF extract
    code_bytes = hmac.new(prk, _INFO + b"\x01", hashlib.sha256).digest()  # expand
    return f"{int.from_bytes(code_bytes[:4], 'big') % 100_000_000:08d}"


def approved_proof(
    own_private: "EllipticCurvePrivateKey", *,
    local_is_tv: bool, session: str, tv_key: bytes, ha_key: bytes,
) -> str:
    """Proof of ECDH key possession for a *separately confirmed* TV OK event.

    Only the TV approval gate may emit this proof over LAN; HA recomputes it
    locally. This is NOT permission to provision credentials by itself.
    """
    from cryptography.hazmat.primitives.asymmetric import ec
    if not isinstance(own_private, ec.EllipticCurvePrivateKey) or not isinstance(
        own_private.curve, ec.SECP256R1
    ) or type(local_is_tv) is not bool:
        raise ValueError("Invalid ephemeral pairing role or key")
    sid = _session_bytes(session)
    tv, ha = _load_p256(tv_key), _load_p256(ha_key)
    if hmac.compare_digest(tv_key, ha_key) or not hmac.compare_digest(
        public_der(own_private), tv_key if local_is_tv else ha_key
    ):
        raise ValueError("Peer identity mismatch")
    shared = own_private.exchange(ec.ECDH(), ha if local_is_tv else tv)
    transcript = _DOMAIN + _part(sid) + _part(tv_key) + _part(ha_key)
    prk = hmac.new(hashlib.sha256(transcript).digest(), shared, hashlib.sha256).digest()
    proof = hmac.new(
        prk, b"KelliesVoice/v1/tv-approved" + b"\x01", hashlib.sha256
    ).digest()[:16]
    return base64.urlsafe_b64encode(proof).rstrip(b"=").decode("ascii")
