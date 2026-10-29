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
    REVOKING = "revoking"
    REVOCATION_PENDING = "revocation_pending"


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
        except BaseException:
            # Include coroutine cancellation. The code is single-use even
            # when HTTP fails; it must never become exchangeable again.
            self._state = _State.ABORTED
            raise
        # Pairing may have been cancelled, timed out, or revoked while the
        # network was awaiting an OAuth response. Never resurrect it.
        if self._state is not _State.EXCHANGING or self.expired:
            self._credentials = creds
            self._state = _State.EXCHANGED
            await self.abort_and_revoke(exchanger)
            raise OAuthTokenError("Authorization was cancelled or expired")
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
        if self._state is not _State.LEASED or self.expired:
            raise OAuthTokenError("Pending transaction not leased or expired")
        self._credentials = None
        self._state = _State.COMMITTED

    async def abort_and_revoke(self, exchanger: HomeAssistantTokenExchange | None = None) -> None:
        """Cancel pairing and revoke a provisional refresh credential.

        Never silently forget a refresh token after a failed revoke. Keep
        it in a recoverable in-memory REVOCATION_PENDING state to allow
        an explicit retry, even after the 120-second pairing window ends.
        No live network exchange is wired to this draft flow yet.
        """
        if self._state in (_State.COMMITTED, _State.ABORTED):
            return
        if self._state is _State.REVOKING:
            raise OAuthTokenError("Credential revocation already in progress")
        creds = self._credentials
        if creds and creds.refresh_token and exchanger is None:
            raise OAuthTokenError("Exchanger required to revoke after exchange")
        self._consumed = {}
        if not creds or not creds.refresh_token:
            self._credentials = None
            self._state = _State.ABORTED
            return
        # Keep the refresh token in memory for safe retries on failure.
        self._state = _State.REVOKING
        try:
            await exchanger.revoke_refresh(creds.refresh_token)
        except Exception:
            self._state = _State.REVOCATION_PENDING
            raise OAuthTokenError("Provisional credential revocation failed") from None
        self._credentials = None
        self._state = _State.ABORTED
