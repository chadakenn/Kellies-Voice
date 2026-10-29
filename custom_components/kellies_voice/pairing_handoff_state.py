"""#193 draft: HA half of a four-message, AEAD-authenticated handoff.

NO transport, OAuth exchange, credentials, or config entry is wired here.
A caller must independently verify the authenticated HA owner, physical TV OK
and UUID-bound pairing transcript BEFORE creating this state machine.

Only an encrypted and authenticated TV persisted receipt allows releasing the
pending token lease. A boolean, public HTTP status, or unverified callback
cannot advance this protocol. Tokens are never logged or serialized by this
module. Restart recovery and credential revocation remain release blockers.
"""
from __future__ import annotations

import base64
import hashlib
import json
import time
from enum import Enum
from typing import Callable

from .pairing_channel import PairingSealedChannel
from .pairing_verification import _session_bytes, _part
from .validation import clean_tv_device_id

_TTL = 120
_MAX_OFFER = 1024
_TRANSCRIPT = b"KVPair/v1/transcript"
_VERSION = 1


class HandoffRejected(ValueError):
    """Intentionally excludes potentially sensitive packet contents."""


class Stage(Enum):
    READY = "ready"
    WAIT_TV_PROOF = "wait_tv_proof"
    READY_TO_COMMIT = "ready_to_commit"
    WAIT_PERSISTED = "wait_persisted"
    PERSISTED = "persisted"
    LEASE_RELEASED = "lease_released"
    ABORTED = "aborted"


class HASealedHandoff:
    """One in-memory HA-side protocol session; no network or persistence.

    Uses the existing directional, in-order AES-GCM channel for confidentiality
    and integrity. The public transcript hash, UUID, session, version and
    expiry are also authenticated INSIDE each sealed JSON message.
    """

    def __init__(
        self, channel: PairingSealedChannel, *,
        owner_user_id: str, tv_uuid: str, session: str,
        tv_key: bytes, ha_key: bytes, clock: Callable[[], float] = time.time,
    ) -> None:
        if (not isinstance(owner_user_id, str) or not owner_user_id
                or owner_user_id == "unknown"):
            raise HandoffRejected("Authenticated owner required")
        if not isinstance(channel, PairingSealedChannel):
            raise HandoffRejected("Verified sealed channel required")
        try:
            clean_uuid = clean_tv_device_id(tv_uuid)
            if clean_uuid != tv_uuid:
                raise ValueError("Noncanonical identity")
            digest = hashlib.sha256(
                _TRANSCRIPT + _part(_session_bytes(session))
                + _part(tv_key) + _part(ha_key)
            ).digest()
        except (TypeError, AttributeError, ValueError):
            raise HandoffRejected("Invalid verified pairing context") from None
        if (getattr(channel, "_hash", None) != digest
                or getattr(channel, "_send_direction", None) != 0
                or getattr(channel, "_next_send", None) != 0
                or getattr(channel, "_next_receive", None) != 0
                or getattr(channel, "_closed", True)):
            raise HandoffRejected("Wrong or used HA pairing channel")
        now = clock()
        if not isinstance(now, (int, float)) or not 0 <= now < 2**53:
            raise HandoffRejected("Invalid pairing clock")
        self._clock = clock
        self._started = now
        self._expires_at = int(now) + _TTL
        self._owner_user_id = owner_user_id
        self._tv_uuid = tv_uuid
        self._session = session
        self._transcript = digest.hex()
        self._channel = channel
        self._stage = Stage.READY

    @property
    def stage(self) -> Stage:
        return self._stage

    @property
    def ready_to_release_lease(self) -> bool:
        return self._stage is Stage.PERSISTED and self._live()

    def _live(self) -> bool:
        now = self._clock()
        return (isinstance(now, (int, float))
                and self._started <= now < self._expires_at)

    def abort(self) -> None:
        if self._stage is Stage.LEASE_RELEASED:
            return
        self._stage = Stage.ABORTED
        self._channel.close()

    def _expect(self, expected: Stage) -> None:
        if self._stage is not expected or not self._live():
            self.abort()
            raise HandoffRejected("Pairing handoff unavailable")

    def _base(self, kind: str) -> dict:
        return {
            "v": _VERSION,
            "type": kind,
            "tv_uuid": self._tv_uuid,
            "session": self._session,
            "transcript": self._transcript,
            "expires_at": self._expires_at,
        }

    @staticmethod
    def _encode(frame: dict) -> bytes:
        return json.dumps(
            frame, sort_keys=True, separators=(",", ":"), ensure_ascii=True,
            allow_nan=False,
        ).encode("ascii")

    def _send(self, frame: dict) -> bytes:
        try:
            return self._channel.seal(self._encode(frame))
        except (ValueError, TypeError):
            self.abort()
            raise HandoffRejected("Could not seal pairing message") from None

    def _receive(self, packet: bytes, expected: str, required: dict) -> None:
        try:
            plaintext = self._channel.open(packet)
            frame = json.loads(plaintext)
            if not isinstance(frame, dict):
                raise ValueError("Non-object")
            baseline = self._base(expected)
            if set(frame) != set(baseline) | set(required):
                raise ValueError("Unexpected fields")
            for key, value in {**baseline, **required}.items():
                # Reject JSON boolean masquerading as version/expiry.
                if type(frame[key]) is not type(value) or frame[key] != value:
                    raise ValueError("Wrong authenticated context")
        except (ValueError, TypeError, UnicodeError, KeyError):
            self.abort()
            raise HandoffRejected("Invalid authenticated TV receipt") from None

    def credential_offer(self, opaque_candidate: bytes) -> bytes:
        """Encrypt an opaque candidate; it MUST come from approved OAuth flow.

        The candidate format and actual token source are not wired in this
        draft. An artificial fixture should be used for testing.
        """
        self._expect(Stage.READY)
        if (type(opaque_candidate) is not bytes
                or not 1 <= len(opaque_candidate) <= _MAX_OFFER):
            self.abort()
            raise HandoffRejected("Invalid candidate size")
        frame = self._base("credential_offer")
        frame["candidate_b64"] = base64.urlsafe_b64encode(
            opaque_candidate).rstrip(b"=").decode("ascii")
        packet = self._send(frame)
        self._stage = Stage.WAIT_TV_PROOF
        return packet

    def accept_tv_proof(self, packet: bytes) -> None:
        """The trusted TV caller must actually perform Assist AND alert checks."""
        self._expect(Stage.WAIT_TV_PROOF)
        self._receive(packet, "tv_proof_receipt", {
            "ack_for": "credential_offer",
            "assist_ok": True,
            "alerts_ok": True,
        })
        self._stage = Stage.READY_TO_COMMIT

    def commit_authorization(self) -> bytes:
        self._expect(Stage.READY_TO_COMMIT)
        frame = self._base("ha_commit_authorization")
        frame["authorize_commit"] = True
        packet = self._send(frame)
        self._stage = Stage.WAIT_PERSISTED
        return packet

    def accept_tv_persisted(self, packet: bytes) -> None:
        """Persistence must be proved after the authenticated HA commit."""
        self._expect(Stage.WAIT_PERSISTED)
        self._receive(packet, "tv_persisted_receipt", {
            "ack_for": "ha_commit_authorization",
            "verified_store_committed": True,
        })
        self._stage = Stage.PERSISTED
        self._channel.close()

    def release_pending_lease(self, pending) -> None:
        """Allow the existing pending transaction to forget its refresh token.

        This is the ONLY release path from this state machine and requires
        an authenticated persisted receipt, owner and TV-session match.
        On failure the caller must revoke and roll back; no retry is implied.
        """
        self._expect(Stage.PERSISTED)
        if (getattr(pending, "owner_user_id", None) != self._owner_user_id
                or getattr(pending, "tv_session", None) != self._session
                or not pending.matches(
                    owner_user_id=self._owner_user_id, tv_session=self._session)):
            self.abort()
            raise HandoffRejected("Pending lease binding mismatch")
        try:
            pending.commit_after_proof()
        except Exception:
            self.abort()
            raise HandoffRejected("Pending lease cannot commit") from None
        self._stage = Stage.LEASE_RELEASED
