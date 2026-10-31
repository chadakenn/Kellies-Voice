"""V3 per-TV capability decision gate, offline QA only.

There are NO routes, services, listeners, HA tokens or credentials here.
A future trusted HA server code path must supply a verified v3 TV proof
and the separately consumed/HA-AuthManager-validated owner claim. Do NOT
expose bind_verified_channel as a WebSocket/HTTP API: Python data classes
are not authenticator credentials. The opaque connection object must be
the already authenticated server-side transport, not a client-provided ID.

This gate enforces a second explicit authorization layer after the security
preflight. It does not establish that any Assist/HA service honors the scope.
"""
from __future__ import annotations

from dataclasses import dataclass
import math
import time

from .credential_scope import TvScopeProof
from .pairing_key_commitment import _session_bytes
from .pairing_v3_wire import VerifiedTv
from .validation import clean_tv_device_id

_MAX_ACTIVE = 16
_MAX_TTL_SECONDS = 120.0
_MAX_SEQUENCE = (2 ** 31) - 1
_ALLOWED_CONTROL_DOMAINS = frozenset(("light", "fan", "media_player"))
_KNOWN_OPS = frozenset((
    "alerts.subscribe", "entity.read", "entity.control", "camera.snapshot",
))
_SENSITIVE_ENTITY_DOMAINS = frozenset((
    "lock", "alarm_control_panel", "cover", "script", "automation",
    "button", "update", "number", "switch",
))


class TvCapabilityDenied(ValueError):
    """Safe generic error: never echo a device ID, code, or request payload."""


@dataclass
class _Channel:
    connection: object
    tv_uuid: str
    session: str
    owner_id: str
    scope: TvScopeProof
    permitted_cameras: frozenset[str]
    permitted_controls: frozenset[str]
    expires_at: float
    last_sequence: int = 0


class ScopedTvGate:
    """Process-only, no-secret gate bound to an opaque server connection.

    No durable TV identity or credential is created. Restart loses all
    authorizations. This is a policy prototype, never a production login.
    """

    def __init__(self) -> None:
        self._channels: list[_Channel] = []

    def _prune(self, instant: float) -> None:
        self._channels = [
            bound for bound in self._channels if instant < bound.expires_at
        ]

    def bind_verified_channel(
        self, *, connection: object, verified_tv: VerifiedTv,
        approved_owner_id: str, scope: TvScopeProof,
        permitted_cameras: frozenset[str] = frozenset(),
        permitted_controls: frozenset[str] = frozenset(),
        ttl_seconds: float = 60.0, now: float | None = None,
    ) -> None:
        """QA-internal hook. Caller MUST have independently verified owner.

        Accepts no password, HA user token, OAuth grant, or user-provided TV
        session. A VerifiedTv object is not sufficient proof of owner approval.
        This method is not registered with HA and must not be reachable by a TV.
        """
        instant = time.monotonic() if now is None else now
        if (connection is None or isinstance(connection, (str, bytes, int, dict))
                or type(verified_tv) is not VerifiedTv
                or type(scope) is not TvScopeProof
                or type(approved_owner_id) is not str or not approved_owner_id
                or scope.tv_user_id == approved_owner_id
                or type(ttl_seconds) not in (int, float)
                or not math.isfinite(ttl_seconds)
                or not 0 < ttl_seconds <= _MAX_TTL_SECONDS
                or type(instant) not in (int, float)
                or not math.isfinite(instant) or instant < 0
                or type(permitted_cameras) is not frozenset
                or type(permitted_controls) is not frozenset):
            raise TvCapabilityDenied("TV capability binding unavailable")
        if (clean_tv_device_id(verified_tv.tv_uuid) != verified_tv.tv_uuid
                or verified_tv.tv_uuid != scope.tv_uuid):
            raise TvCapabilityDenied("TV identity mismatch")
        try:
            _session_bytes(verified_tv.session)
        except (TypeError, ValueError):
            raise TvCapabilityDenied("Invalid TV session") from None

        selected = set(scope.assist_entity_ids)
        if not all(type(e) is str for e in permitted_cameras | permitted_controls):
            raise TvCapabilityDenied("Invalid TV entity scope")
        if (not permitted_cameras.issubset(selected)
                or any(not e.startswith("camera.") for e in permitted_cameras)
                or not permitted_controls.issubset(selected)
                or any(e.split(".", 1)[0] not in _ALLOWED_CONTROL_DOMAINS
                       for e in permitted_controls)):
            raise TvCapabilityDenied("TV action outside approved scope")

        self._prune(instant)
        if (len(self._channels) >= _MAX_ACTIVE
                or any(b.connection is connection
                       or b.tv_uuid == verified_tv.tv_uuid
                       for b in self._channels)):
            raise TvCapabilityDenied("TV channel already active")
        self._channels.append(_Channel(
            connection=connection, tv_uuid=verified_tv.tv_uuid,
            session=verified_tv.session, owner_id=approved_owner_id,
            scope=scope, permitted_cameras=permitted_cameras,
            permitted_controls=permitted_controls, expires_at=instant + ttl_seconds,
        ))

    def authorize(
        self, *, connection: object, tv_uuid: str, session: str,
        sequence: int, operation: str, entity_id: str | None,
        now: float | None = None,
    ) -> bool:
        """No side effects except one-time sequence consumption on success.

        No auto-fallback from a UUID/owner ID to a connection. The caller
        MUST also enforce its API's own HA permissions before execution.
        """
        instant = time.monotonic() if now is None else now
        if type(instant) not in (int, float) or not math.isfinite(instant):
            raise TvCapabilityDenied("Authorization unavailable")
        self._prune(instant)
        match = next((b for b in self._channels if b.connection is connection), None)
        if match is None:
            raise TvCapabilityDenied("TV channel not authenticated")
        if (type(tv_uuid) is not str or tv_uuid != match.tv_uuid
                or type(session) is not str or session != match.session
                or type(sequence) is not int or not 1 <= sequence <= _MAX_SEQUENCE
                or sequence <= match.last_sequence
                or type(operation) is not str or operation not in _KNOWN_OPS):
            raise TvCapabilityDenied("TV operation denied")
        if operation == "alerts.subscribe":
            if entity_id is not None:
                raise TvCapabilityDenied("TV alerts must not request an entity")
        else:
            if type(entity_id) is not str or entity_id not in match.scope.assist_entity_ids:
                raise TvCapabilityDenied("TV entity not approved")
            domain = entity_id.split(".", 1)[0]
            if operation == "entity.read":
                pass
            elif operation == "camera.snapshot":
                if entity_id not in match.permitted_cameras:
                    raise TvCapabilityDenied("TV camera not approved")
            elif operation == "entity.control":
                if (domain in _SENSITIVE_ENTITY_DOMAINS
                        or entity_id not in match.permitted_controls):
                    raise TvCapabilityDenied("TV control not approved")
        match.last_sequence = sequence
        return True

    def disconnect(self, connection: object) -> None:
        """Immediately revoke just this exact server connection."""
        self._channels = [
            b for b in self._channels if b.connection is not connection
        ]

    def reset(self) -> None:
        """On HA shutdown/restart revoke all process-only preview grants."""
        self._channels.clear()

    def active_count(self, *, now: float | None = None) -> int:
        instant = time.monotonic() if now is None else now
        self._prune(instant)
        return len(self._channels)
