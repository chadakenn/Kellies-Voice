"""#213: tests for the bounded pending OAuth transaction.

Uses a fake exchanger so no network or aiohttp is required.
"""

import time
import unittest
from unittest.mock import AsyncMock, MagicMock

from custom_components.kellies_voice.pairing_oauth_pending import PendingOAuthTransaction
from custom_components.kellies_voice.pairing_token_exchange import AccessCredentials, OAuthTokenError


class TestPendingOAuthTransaction(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.consumed = {
            "code": "auth-code-12345678",
            "code_verifier": "verifier-value-that-is-long-enough",
            "client_id": "https://ha.example/",
            "redirect_uri": "https://ha.example/api/kellies_voice/pair/oauth_callback",
        }
        self.txn = PendingOAuthTransaction(
            owner_user_id="user-1",
            tv_session="tv-session-xyz",
            consumed=dict(self.consumed),
            started=time.time(),
        )

    async def test_exchange_stores_credentials_and_drops_verifier(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        )
        await self.txn.exchange(exchanger)
        self.assertEqual(self.txn._consumed, {})
        self.assertIsNotNone(self.txn._credentials)

    async def test_concurrent_exchange_rejected(self):
        exchanger = MagicMock()
        async def slow(*a, **k):
            await self.txn.exchange(exchanger)  # re-entrant while EXCHANGING
            return AccessCredentials("a" * 16, 1800, "r" * 16)
        exchanger.exchange_from_consumed = slow
        with self.assertRaises(OAuthTokenError):
            await self.txn.exchange(exchanger)

    async def test_abort_after_exchange_requires_exchanger(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        )
        await self.txn.exchange(exchanger)
        with self.assertRaises(OAuthTokenError):
            await self.txn.abort_and_revoke(None)

    async def test_abort_revokes_and_clears(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        )
        exchanger.revoke_refresh = AsyncMock()
        await self.txn.exchange(exchanger)
        await self.txn.abort_and_revoke(exchanger)
        exchanger.revoke_refresh.assert_awaited_once()
        self.assertIsNone(self.txn._credentials)
        self.assertEqual(self.txn._state.name, "ABORTED")

    async def test_lease_keeps_revocable_until_commit(self):
        exchanger = MagicMock()
        creds = AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        exchanger.exchange_from_consumed = AsyncMock(return_value=creds)
        exchanger.revoke_refresh = AsyncMock()
        await self.txn.exchange(exchanger)
        leased = self.txn.lease_for_provision(owner_user_id="user-1", tv_session="tv-session-xyz")
        self.assertIs(leased, creds)
        # Still revocable while leased.
        await self.txn.abort_and_revoke(exchanger)
        exchanger.revoke_refresh.assert_awaited_once()

    async def test_commit_after_proof_drops_token(self):
        exchanger = MagicMock()
        creds = AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        exchanger.exchange_from_consumed = AsyncMock(return_value=creds)
        await self.txn.exchange(exchanger)
        self.txn.lease_for_provision(owner_user_id="user-1", tv_session="tv-session-xyz")
        self.txn.commit_after_proof()
        self.assertIsNone(self.txn._credentials)
        self.assertEqual(self.txn._state.name, "COMMITTED")
        # Second abort is a no-op.
        await self.txn.abort_and_revoke(exchanger)

    async def test_mismatch_rejected(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        )
        await self.txn.exchange(exchanger)
        with self.assertRaises(OAuthTokenError):
            self.txn.lease_for_provision(owner_user_id="other", tv_session="tv-session-xyz")
