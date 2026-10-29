"""#193 experimental ECDH-bound AES-GCM envelope, intentionally unused by HA.

NO credentials, URLs or authorization codes cross this channel in this PR.
Physical TV OK + verified HA-owner authorization MUST gate any later usage.
It provides a unit-tested wire format, not an authenticated device identity.
"""

from __future__ import annotations

import hashlib
import hmac
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.asymmetric import ec

from .pairing_verification import _session_bytes, _load_p256, _part, public_der, calculate

_TRANSCRIPT = b"KVPair/v1/transcript"
_AAD = b"KelliesVoice/v1/channel-aad"
_HA_KEY = b"KelliesVoice/v1/channel/ha-to-tv"
_TV_KEY = b"KelliesVoice/v1/channel/tv-to-ha"
_HEADER = 22
_MAX = 2048


class PairingSealedChannel:
    """One-session, in-order authenticated encryption. No network effects."""

    def __init__(self, local_private, *, local_is_tv, session, tv_key, ha_key):
        # calculate validates P-256, strict ordered roles and canonical session.
        calculate(local_private, local_is_tv=local_is_tv,
                  session=session, tv_key=tv_key, ha_key=ha_key)
        sid = _session_bytes(session)
        self._hash = hashlib.sha256(
            _TRANSCRIPT + _part(sid) + _part(tv_key) + _part(ha_key)).digest()
        peer = _load_p256(ha_key if local_is_tv else tv_key)
        secret = local_private.exchange(ec.ECDH(), peer)
        prk = hmac.new(self._hash, secret, hashlib.sha256).digest()

        def expand(label):
            return hmac.new(prk, label + b"\x01", hashlib.sha256).digest()

        self._send_direction = 1 if local_is_tv else 0
        self._send_key = expand(_TV_KEY if local_is_tv else _HA_KEY)
        self._receive_key = expand(_HA_KEY if local_is_tv else _TV_KEY)
        self._next_send = 0
        self._next_receive = 0
        self._closed = False

    def seal(self, plaintext: bytes) -> bytes:
        return self.seal_with_nonce(plaintext, os.urandom(12))

    def seal_with_nonce(self, plaintext: bytes, nonce: bytes) -> bytes:
        """Test hook accepts fixed nonce; production MUST call seal()."""
        if (self._closed or not isinstance(plaintext, bytes) or
            not 1 <= len(plaintext) <= _MAX or
            not isinstance(nonce, bytes) or len(nonce) != 12 or
            self._next_send >= 2**63 - 1):
            raise ValueError("Pairing channel unavailable")
        header = (bytes((1, self._send_direction)) +
                  self._next_send.to_bytes(8, "big") + nonce)
        ciphertext = AESGCM(self._send_key).encrypt(
            nonce, plaintext, _AAD + self._hash + header)
        self._next_send += 1
        return header + ciphertext

    def open(self, packet: bytes) -> bytes:
        if (self._closed or not isinstance(packet, bytes)
            or not _HEADER + 17 <= len(packet) <= _HEADER + 16 + _MAX):
            raise ValueError("Invalid pairing packet")
        version, direction = packet[0], packet[1]
        sequence = int.from_bytes(packet[2:10], "big")
        if (version != 1 or direction != 1 - self._send_direction
            or sequence != self._next_receive
            or self._next_receive >= 2**63 - 1):
            raise ValueError("Wrong direction, version or replayed packet")
        nonce = packet[10:22]
        try:
            clear = AESGCM(self._receive_key).decrypt(
                nonce, packet[_HEADER:], _AAD + self._hash + packet[:_HEADER])
        except Exception as error:
            raise ValueError("Pairing packet authentication failed") from error
        self._next_receive += 1
        return clear

    def close(self) -> None:
        self._closed = True
        # Python immutable bytes cannot guarantee memory zeroization; all
        # material is ephemeral and no file or Home Assistant storage is used.
        self._send_key = b""
        self._receive_key = b""
        self._hash = b""
