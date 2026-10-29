"""#213: bounded, in-memory pending OAuth transaction for HA-side pairing.

Holds an already-consumed authorization-code result and optional credentials
only in process memory, bound to exact owner + TV session. Never writes to
config-entry data, mDNS, discovery endpoints, or logs.

On abort, expiry, or failed provision the refresh token (if any) is revoked
via HomeAssistantTokenExchange.revoke_refresh and the object is cleared.

This module does not mark Connected or create a config entry. The AEAD
provisioning channel (#203 / #212) must be ready before any credential is
handed to a TV.
"""

from __future__ import annotations

import time
from typing import Any

from .pairing_token_exchange import AccessCredentials, HomeAssistantTokenExchange, OAuthTokenError

_PENDING_TTL = 120  # seconds


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
        self._aborted = False

    @property
    def expired(self) -> bool:
        return time.time() - self.started >= _PENDING_TTL or self._aborted

    def matches(self, *, owner_user_id: str, tv_session: str) -> bool:
        return (
            not self.expired
            and owner_user_id == self.owner_user_id
            and tv_session == self.tv_session
        )

    async def exchange(self, exchanger: HomeAssistantTokenExchange) -> None:
        """Perform the one-use code exchange using the already-consumed result.

        Credentials stay in memory. The verifier is discarded after the call.
        """
        if self._aborted or self._credentials is not None:
            raise OAuthTokenError("Pending transaction already finished")
        if self.expired:
            raise OAuthTokenError("Pending transaction expired")
        creds = await exchanger.exchange_from_consumed(self._consumed)
        self._credentials = creds
        # Drop the verifier immediately.
        self._consumed = {}

    async def abort_and_revoke(self, exchanger: HomeAssistantTokenExchange | None = None) -> None:
        """Revoke any refresh token and clear secrets."""
        self._aborted = True
        creds = self._credentials
        self._credentials = None
        self._consumed = {}
        if creds and creds.refresh_token and exchanger is not None:
            try:
                await exchanger.revoke_refresh(creds.refresh_token)
            except OAuthTokenError:
                pass  # best-effort; secrets already dropped from memory

    def take_for_provision(self, *, owner_user_id: str, tv_session: str) -> AccessCredentials:
        """Return credentials only for the exact bound owner+TV, then clear.

        Caller must immediately encrypt and deliver via the AEAD channel.
        If delivery fails the caller must call abort_and_revoke.
        """
        if not self.matches(owner_user_id=owner_user_id, tv_session=tv_session):
            raise OAuthTokenError("Pending transaction mismatch")
        if self._credentials is None:
            raise OAuthTokenError("No credentials available")
        creds = self._credentials
        self._credentials = None
        self._aborted = True
        return creds
