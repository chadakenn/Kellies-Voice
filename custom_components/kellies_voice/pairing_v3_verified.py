"""Offline v3 SAS and TV remote-OK proof from *verified* key commitments.

Not registered with HA's v2 discovery endpoints, OAuth, token exchange, TV
provisioning or config flow. Both peers MUST exchange immutable commitments
before either public key/nonce is revealed. This module rejects incomplete
commitment sessions and derives no secrets from unverified input.

Protocol names and byte framing are frozen for cross-language Android tests;
until those tests pass, v3 is not a credential transport authorization.
"""
from __future__ import annotations

import base64
import hashlib
import hmac

from .pairing_key_commitment import CommitmentSession, commitment, _part, _session_bytes
from .pairing_verification import _load_p256, public_der
from .validation import clean_tv_device_id

_SAS_DOMAIN = b"KelliesVoice/v3/sas-transcript\x00"
_SAS_INFO = b"KelliesVoice/v3/eight-digit-code\x00"
_TV_PROOF_INFO = b"KelliesVoice/v3/remote-ok-uuid\x00"


def _derive_secret(private, verified: CommitmentSession) -> bytes:
    from cryptography.hazmat.primitives.asymmetric import ec

    if type(verified) is not CommitmentSession or not verified.ready:
        raise ValueError("Unverified v3 pairing transcript")
    sid, own_der, peer_der, own_nonce, peer_nonce = verified.verified_transcript()
    role = verified.role
    if (role not in ("tv", "ha")
            or not isinstance(private, ec.EllipticCurvePrivateKey)
            or not isinstance(private.curve, ec.SECP256R1)
            or not hmac.compare_digest(public_der(private), own_der)):
        raise ValueError("V3 local key/role mismatch")
    _load_p256(own_der)
    other_key = _load_p256(peer_der)
    if hmac.compare_digest(own_der, peer_der):
        raise ValueError("V3 reflected pairing keys")

    if role == "tv":
        tv_der, tv_nonce = own_der, own_nonce
        ha_der, ha_nonce = peer_der, peer_nonce
    else:
        tv_der, tv_nonce = peer_der, peer_nonce
        ha_der, ha_nonce = own_der, own_nonce

    # Exact same canonical ordering is used on HA and TV, independent of
    # which side performs the derivation. Neither value can be chosen after
    # the previously exchanged 32-byte role-bound commitment is verified.
    tv_commit = commitment("tv", sid, tv_der, tv_nonce)
    ha_commit = commitment("ha", sid, ha_der, ha_nonce)
    transcript = _SAS_DOMAIN
    for field in (_session_bytes(sid), tv_commit, ha_commit,
                  tv_der, ha_der, tv_nonce, ha_nonce):
        transcript += _part(field)
    shared = private.exchange(ec.ECDH(), other_key)
    # HKDF-Extract with SHA256(transcript), using ECDH P-256 as IKM.
    return hmac.new(hashlib.sha256(transcript).digest(), shared,
                    hashlib.sha256).digest()


def comparison_code(private, verified: CommitmentSession) -> str:
    """8 ASCII digits only after both v3 commitments and reveals are verified."""
    prk = _derive_secret(private, verified)
    sas = hmac.new(prk, _SAS_INFO + b"\x01", hashlib.sha256).digest()
    return f"{int.from_bytes(sas[:4], 'big') % 100_000_000:08d}"


def physical_tv_ok_proof(private, verified: CommitmentSession, *,
                         tv_uuid: str) -> str:
    """Exact-TV v3 proof. Only call after physical TV remote OK is observed.

    This is NOT approval to mint or release a bearer refresh token. It proves
    verified key possession and binds the resulting proof to a TV UUID.
    """
    if type(tv_uuid) is not str:
        raise ValueError("Invalid v3 TV UUID")
    uuid = clean_tv_device_id(tv_uuid)
    if tv_uuid != uuid:
        raise ValueError("Noncanonical v3 TV UUID")
    prk = _derive_secret(private, verified)
    mac = hmac.new(
        prk, _TV_PROOF_INFO + _part(uuid.encode("ascii")) + b"\x01",
        hashlib.sha256,
    ).digest()[:16]
    return base64.urlsafe_b64encode(mac).rstrip(b"=").decode("ascii")
