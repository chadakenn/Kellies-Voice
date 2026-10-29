"""#213: tests for the bounded pending OAuth transaction.

Uses a fake exchanger so no network or aiohttp is required.
"""

import asyncio
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


    async def test_cancel_during_network_exchange_cannot_resurrect_token(self):
        exchanger = MagicMock()
        entered = asyncio.Event()
        response = asyncio.get_running_loop().create_future()
        async def slow_exchange(_):
            entered.set()
            return await response
        exchanger.exchange_from_consumed = slow_exchange
        exchanger.revoke_refresh = AsyncMock()
        work = asyncio.create_task(self.txn.exchange(exchanger))
        await entered.wait()
        # No token in memory until the server finishes authorization.
        await self.txn.abort_and_revoke(exchanger)
        self.assertEqual("ABORTED", self.txn._state.name)
        response.set_result(AccessCredentials("a" * 16, 1800, "r" * 16))
        with self.assertRaises(OAuthTokenError):
            await work
        exchanger.revoke_refresh.assert_awaited_once_with("r" * 16)
        self.assertIsNone(self.txn._credentials)
        self.assertEqual("ABORTED", self.txn._state.name)
        with self.assertRaises(OAuthTokenError):
            await self.txn.exchange(exchanger)

    async def test_expiry_during_network_exchange_revokes_token(self):
        exchanger = MagicMock()
        entered = asyncio.Event()
        response = asyncio.get_running_loop().create_future()
        async def slow_exchange(_):
            entered.set()
            return await response
        exchanger.exchange_from_consumed = slow_exchange
        exchanger.revoke_refresh = AsyncMock()
        work = asyncio.create_task(self.txn.exchange(exchanger))
        await entered.wait()
        self.txn.started = time.time() - 180
        response.set_result(AccessCredentials("a" * 16, 1800, "r" * 16))
        with self.assertRaises(OAuthTokenError):
            await work
        exchanger.revoke_refresh.assert_awaited_once_with("r" * 16)
        self.assertEqual("ABORTED", self.txn._state.name)

    async def test_no_exchanger_does_not_discard_live_token(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("a" * 16, 1800, "r" * 16)
        )
        exchanger.revoke_refresh = AsyncMock()
        await self.txn.exchange(exchanger)
        with self.assertRaises(OAuthTokenError):
            await self.txn.abort_and_revoke(None)
        self.assertIsNotNone(self.txn._credentials)
        await self.txn.abort_and_revoke(exchanger)
        exchanger.revoke_refresh.assert_awaited_once_with("r" * 16)
        self.assertIsNone(self.txn._credentials)

    async def test_revoke_failure_preserves_token_for_retry(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("a" * 16, 1800, "r" * 16)
        )
        exchanger.revoke_refresh = AsyncMock(side_effect=[
            OAuthTokenError("offline"), None
        ])
        await self.txn.exchange(exchanger)
        with self.assertRaises(OAuthTokenError):
            await self.txn.abort_and_revoke(exchanger)
        self.assertEqual("REVOCATION_PENDING", self.txn._state.name)
        self.assertIsNotNone(self.txn._credentials)
        await self.txn.abort_and_revoke(exchanger)
        self.assertEqual("ABORTED", self.txn._state.name)
        self.assertIsNone(self.txn._credentials)
        self.assertEqual(2, exchanger.revoke_refresh.await_count)

    async def test_expired_lease_cannot_commit(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("a" * 16, 1800, "r" * 16)
        )
        exchanger.revoke_refresh = AsyncMock()
        await self.txn.exchange(exchanger)
        self.txn.lease_for_provision(owner_user_id="user-1", tv_session="tv-session-xyz")
        self.txn.started = time.time() - 180
        with self.assertRaises(OAuthTokenError):
            self.txn.commit_after_proof()
        await self.txn.abort_and_revoke(exchanger)
        exchanger.revoke_refresh.assert_awaited_once()

    async def test_mismatch_rejected(self):
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=AccessCredentials("access-token-value-ok", 1800, "refresh-token-value-ok")
        )
        await self.txn.exchange(exchanger)
        with self.assertRaises(OAuthTokenError):
            self.txn.lease_for_provision(owner_user_id="other", tv_session="tv-session-xyz")
