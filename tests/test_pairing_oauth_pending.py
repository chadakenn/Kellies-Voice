"""#213: tests for the bounded pending OAuth transaction.

Uses a fake exchanger so no network or aiohttp is required.
"""

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
            started=0.0,
        )

    async def test_exchange_stores_credentials_and_drops_verifier(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        )
        await self.txn.exchange(exchanger)
        self.assertEqual(self.txn._consumed, {})
        self.assertIsNotNone(self.txn._credentials)

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
        self.assertTrue(self.txn._aborted)

    async def test_mismatch_rejected(self):
        with self.assertRaises(OAuthTokenError):
            self.txn.take_for_provision(owner_user_id="other", tv_session="tv-session-xyz")

    async def test_take_for_provision_returns_and_clears(self):
        exchanger = MagicMock()
        creds = AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        exchanger.exchange_from_consumed = AsyncMock(return_value=creds)
        await self.txn.exchange(exchanger)
        taken = self.txn.take_for_provision(owner_user_id="user-1", tv_session="tv-session-xyz")
        self.assertIs(taken, creds)
        self.assertIsNone(self.txn._credentials)
        self.assertTrue(self.txn._aborted)
