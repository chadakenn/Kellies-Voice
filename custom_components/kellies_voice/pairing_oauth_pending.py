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

    async def exchange(
        self,
        exchanger: HomeAssistantTokenExchange,
        *,
        state: str,
        code: str,
        now: float,
    ) -> None:
        """Perform the one-use code exchange. Credentials stay in memory."""
        if self._aborted or self._credentials is not None:
            raise OAuthTokenError("Pending transaction already finished")
        if self.expired:
            raise OAuthTokenError("Pending transaction expired")
        # The caller must have already consumed the attempt exactly once.
        # We use the verifier returned by that single consume.
        creds = await exchanger.exchange_approved_callback(
            # exchange_approved_callback still expects an attempt; for the
            # already-consumed path we supply a thin adapter that returns
            # the previously obtained verifier without consuming again.
            _AlreadyConsumed(self._consumed),
            state=state,
            code=code,
            tv_session=self.tv_session,
            now=now,
        )
        self._credentials = creds

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


class _AlreadyConsumed:
    """Adapter so HomeAssistantTokenExchange can use a pre-consumed result
    without calling consume_callback a second time.
    """

    def __init__(self, consumed: dict[str, str]) -> None:
        self._consumed = consumed
        self.client_id = consumed.get("client_id", "")

    def consume_callback(self, *, received_state, code, tv_session, now):
        # Already consumed; just return the stored values if they match.
        if (
            received_state != self._consumed.get("state")
            or code != self._consumed.get("code")
            or tv_session != self._consumed.get("tv_session")
        ):
            raise ValueError("Already-consumed mismatch")
        return {
            "code": self._consumed["code"],
            "code_verifier": self._consumed["code_verifier"],
            "client_id": self.client_id,
            "redirect_uri": self._consumed["redirect_uri"],
        }

    def cancel(self) -> None:
        self._consumed = {}
