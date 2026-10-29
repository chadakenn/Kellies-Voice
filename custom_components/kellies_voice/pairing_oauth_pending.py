"""#213: bounded, in-memory pending OAuth transaction for HA-side pairing.

State machine:
  NEW → EXCHANGING → EXCHANGED → LEASED → COMMITTED
                  ↘
                   ABORTED (from any non-COMMITTED state)

- EXCHANGING is set before the HTTP await so concurrent callers fail closed.
- LEASED keeps the refresh token so a failed AEAD delivery can still revoke.
- Only COMMITTED drops the refresh token permanently (after TV proof ack).

Crash/restart policy: this object is process-local. A restart loses the
in-memory refresh token. Until a durable, encrypted pending store exists,
any live exchange must be followed by immediate revoke on process exit or
by a short-lived HA token that expires before restart recovery is needed.
This draft does not enable live exchange.

Never writes to config-entry data, mDNS, discovery endpoints, or logs.
"""

from __future__ import annotations

import enum
import time
from typing import Any

from .pairing_token_exchange import AccessCredentials, HomeAssistantTokenExchange, OAuthTokenError

_PENDING_TTL = 120  # seconds


class _State(enum.Enum):
    NEW = "new"
    EXCHANGING = "exchanging"
    EXCHANGED = "exchanged"
    LEASED = "leased"
    COMMITTED = "committed"
    ABORTED = "aborted"


class PendingOAuthTransaction:
    """One-use pending credential holder. Fail-closed on any mismatch."""

    def __init__(
        self,
        *,
        owner_user_id: str,
        tv_session: str,
        consumed: dict[str, str],
        started: float,
    ) -> None:
        if not isinstance(owner_user_id, str) or not owner_user_id:
            raise ValueError("Owner required")
        if not isinstance(tv_session, str) or not tv_session:
            raise ValueError("TV session required")
        if not isinstance(consumed, dict):
            raise ValueError("Consumed authorization required")
        self.owner_user_id = owner_user_id
        self.tv_session = tv_session
        self._consumed = consumed
        self.started = started
        self._credentials: AccessCredentials | None = None
        self._state = _State.NEW

    @property
    def expired(self) -> bool:
        return time.time() - self.started >= _PENDING_TTL or self._state in (
            _State.ABORTED, _State.COMMITTED
        )

    def matches(self, *, owner_user_id: str, tv_session: str) -> bool:
        return (
            not self.expired
            and owner_user_id == self.owner_user_id
            and tv_session == self.tv_session
        )

    async def exchange(self, exchanger: HomeAssistantTokenExchange) -> None:
        """Perform the one-use code exchange. Atomic transition before await."""
        if self._state is not _State.NEW:
            raise OAuthTokenError("Pending transaction not in exchangeable state")
        if self.expired:
            raise OAuthTokenError("Pending transaction expired")
        # Atomic: mark EXCHANGING before the await so a concurrent caller fails.
        self._state = _State.EXCHANGING
        consumed = self._consumed
        self._consumed = {}  # drop verifier before network call
        try:
            creds = await exchanger.exchange_from_consumed(consumed)
        except Exception:
            self._state = _State.ABORTED
            raise
        self._credentials = creds
        self._state = _State.EXCHANGED

    def lease_for_provision(self, *, owner_user_id: str, tv_session: str) -> AccessCredentials:
        """Lease credentials for AEAD delivery. Refresh token remains revocable.

        Caller must call commit_after_proof() on success or abort_and_revoke()
        on failure/timeout. The returned object is the same instance held here.
        """
        if self._state is not _State.EXCHANGED:
            raise OAuthTokenError("Pending transaction not ready to lease")
        if not self.matches(owner_user_id=owner_user_id, tv_session=tv_session):
            raise OAuthTokenError("Pending transaction mismatch")
        if self._credentials is None:
            raise OAuthTokenError("No credentials available")
        self._state = _State.LEASED
        return self._credentials

    def commit_after_proof(self) -> None:
        """Drop the HA-side refresh token after TV proof and atomic promotion.

        Only call this after the TV has acknowledged receipt and the
        Assist+alerts proof has passed. Secrets are forgotten.
        """
        if self._state is not _State.LEASED:
            raise OAuthTokenError("Pending transaction not leased")
        self._credentials = None
        self._state = _State.COMMITTED

    async def abort_and_revoke(self, exchanger: HomeAssistantTokenExchange | None = None) -> None:
        """Revoke any refresh token and clear secrets.

        After a successful exchange an exchanger is required; calling with
        None raises so a future integration cannot silently drop a live token.
        """
        if self._state in (_State.COMMITTED, _State.ABORTED):
            return
        creds = self._credentials
        had_creds = creds is not None
        self._credentials = None
        self._consumed = {}
        self._state = _State.ABORTED
        if had_creds:
            if exchanger is None:
                raise OAuthTokenError("Exchanger required to revoke after exchange")
            if creds.refresh_token:
                try:
                    await exchanger.revoke_refresh(creds.refresh_token)
                except OAuthTokenError:
                    pass  # secrets already dropped; caller may log the failure
