"""Offline tests: actual dedicated HA Users account + renewable token issuance.

No live Home Assistant data, tokens, devices or network operations.
"""
from __future__ import annotations
import asyncio
from datetime import timedelta
import importlib
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import AsyncMock, patch

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice_v3qa"
PKG = "kv_v3qa_dedicated_account_test"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg
homeassistant = types.ModuleType("homeassistant")
homeassistant.__path__ = []
authmod = types.ModuleType("homeassistant.auth")
authmod.__path__ = []
authconst = types.ModuleType("homeassistant.auth.const")
authconst.GROUP_ID_USER = "system-users"

TV = "123e4567-e89b-42d3-a456-426614174000"
ORIGIN = "https://ha.example.test"


class FakeAuth:
    def __init__(self):
        self.owner = types.SimpleNamespace(
            id="owner", is_owner=True, is_active=True)
        self.created = []
        self.tokens = {}
        self.fail_after_refresh = False
        self.fail_user_removal = False
        self.removed_users = []
        self.deactivated_users = []

    async def async_get_user(self, uid):
        if uid == "owner":
            return self.owner
        return next((x for x in self.created if x.id == uid), None)

    async def async_create_user(self, name, group_ids, local_only):
        assert group_ids == ["system-users"] and type(local_only) is bool
        result = types.SimpleNamespace(
            id="dedicated-user-" + str(len(self.created) + 1),
            name=name, is_active=True, is_owner=False, is_admin=False,
            system_generated=False, local_only=local_only,
            groups=[types.SimpleNamespace(id="system-users")],
            refresh_tokens={},
        )
        self.created.append(result)
        return result

    async def async_remove_user(self, user):
        if self.fail_user_removal:
            raise RuntimeError("simulated account store failure")
        assert user in self.created and not user.is_owner and not user.is_admin
        for token in list(user.refresh_tokens.values()):
            self.async_remove_refresh_token(token)
        self.created.remove(user)
        self.removed_users.append(user.id)

    async def async_deactivate_user(self, user):
        assert user in self.created and not user.is_owner
        user.is_active = False
        for token in list(user.refresh_tokens.values()):
            self.async_remove_refresh_token(token)
        self.deactivated_users.append(user.id)

    async def async_create_refresh_token(self, user, client_id):
        token_id = "created-refresh-id-" + str(len(self.tokens) + 1)
        obj = types.SimpleNamespace(
            id=token_id, token="SECRET-REFRESH-DO-NOT-LOG",
            client_id=client_id, user=user, token_type="normal",
            access_token_expiration=timedelta(minutes=30),
        )
        self.tokens[token_id] = obj
        user.refresh_tokens[token_id] = obj
        return obj

    def async_create_access_token(self, refresh):
        if self.fail_after_refresh:
            raise ValueError("simulated backend token error")
        return "SECRET-ACCESS-DO-NOT-LOG"

    def async_remove_refresh_token(self, refresh):
        self.tokens.pop(refresh.id, None)
        refresh.user.refresh_tokens.pop(refresh.id, None)


class DedicatedLoginTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        cls.patched = patch.dict(
            sys.modules, {"homeassistant": homeassistant,
                          "homeassistant.auth": authmod,
                          "homeassistant.auth.const": authconst})
        cls.patched.start()
        cls.module = importlib.import_module(PKG + ".paired_tv_login")
        cls.journal_cls = importlib.import_module(
            PKG + ".token_lease_journal").TokenLeaseJournal

    @classmethod
    def tearDownClass(cls):
        cls.patched.stop()
        for key in list(sys.modules):
            if key == PKG or key.startswith(PKG + "."):
                sys.modules.pop(key, None)

    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.auth = FakeAuth()
        self.hass = types.SimpleNamespace(
            auth=self.auth,
            async_add_executor_job=lambda fn, *args: asyncio.to_thread(fn, *args),
        )
        self.journal = self.journal_cls(
            self.hass, Path(self.tmp.name) / "lease.json")

    async def test_dedicated_nonadmin_local_account_issues_only_own_token(self):
        issued, recorded, account = await self.module.provision_dedicated_tv_login(
            self.hass, self.journal, owner_id="owner", tv_uuid=TV,
            ha_origin=ORIGIN)
        self.assertFalse(account.is_admin)
        self.assertFalse(account.is_owner)
        self.assertTrue(account.local_only)
        self.assertEqual({g.id for g in account.groups}, {"system-users"})
        self.assertEqual(issued.lease.tv_user_id, account.id)
        self.assertEqual(recorded.token_id, "created-refresh-id-1")
        self.assertEqual(recorded.state, "issued")
        self.assertEqual(issued.access_token_seconds, 1800)
        on_disk = (Path(self.tmp.name) / "lease.json").read_text()
        self.assertIn("created-refresh-id-1", on_disk)
        self.assertNotIn("SECRET-", on_disk)

    async def test_nabu_cloud_uses_dedicated_remote_capable_user(self):
        issued, recorded, account = await self.module.provision_dedicated_tv_login(
            self.hass, self.journal, owner_id="owner", tv_uuid=TV,
            ha_origin="https://example.ui.nabu.casa", remote_login=True)
        self.assertFalse(account.is_owner)
        self.assertFalse(account.is_admin)
        self.assertFalse(account.local_only)
        self.assertEqual({g.id for g in account.groups}, {"system-users"})
        self.assertEqual(recorded.state, "issued")
        self.assertEqual(issued.lease.tv_user_id, account.id)

    async def test_invalid_remote_flag_refused_before_creating_user(self):
        with self.assertRaises(self.module.DedicatedTvLoginDenied):
            await self.module.provision_dedicated_tv_login(
                self.hass, self.journal, owner_id="owner", tv_uuid=TV,
                ha_origin=ORIGIN, remote_login="true")
        self.assertEqual(self.auth.created, [])

    async def test_plain_http_does_not_even_create_a_user(self):
        with self.assertRaises(Exception):
            await self.module.provision_dedicated_tv_login(
                self.hass, self.journal, owner_id="owner", tv_uuid=TV,
                ha_origin="http://192.168.1.82:8123")
        self.assertEqual(self.auth.created, [])

    async def test_non_owner_rejected_before_account_creation(self):
        for owner_id in ("not-owner", "", "created-user"):
            with self.subTest(owner=owner_id):
                with self.assertRaises(Exception):
                    await self.module.provision_dedicated_tv_login(
                        self.hass, self.journal, owner_id=owner_id,
                        tv_uuid=TV, ha_origin=ORIGIN)
        self.assertEqual(self.auth.created, [])

    async def test_one_failed_tv_is_revoked_without_affecting_second_tv(self):
        first, lease1, _ = await self.module.provision_dedicated_tv_login(
            self.hass, self.journal, owner_id="owner", tv_uuid=TV,
            ha_origin=ORIGIN)
        second, lease2, _ = await self.module.provision_dedicated_tv_login(
            self.hass, self.journal, owner_id="owner",
            tv_uuid="123e4567-e89b-42d3-a456-426614174001",
            ha_origin=ORIGIN)
        self.assertEqual(len(self.auth.tokens), 2)
        self.assertTrue(await self.journal.revoke_unfinished_attempt(
            lease1.nonce))
        self.assertEqual(set(self.auth.tokens), {lease2.token_id})
        self.assertEqual(
            {x.nonce: x.state for x in self.journal.snapshot()}[lease2.nonce],
            "issued")
        await self.journal.commit(lease2.nonce)
        self.assertEqual(
            {x.nonce: x.state for x in self.journal.snapshot()}[lease2.nonce],
            "committed")
        with self.assertRaises(Exception):
            await self.journal.revoke_unfinished_attempt(lease2.nonce)

    async def test_retry_for_existing_tv_never_creates_an_orphan_account(self):
        first, lease, account = await self.module.provision_dedicated_tv_login(
            self.hass, self.journal, owner_id="owner",
            tv_uuid=TV, ha_origin=ORIGIN)
        await self.journal.commit(lease.nonce)
        with self.assertLogs(
                self.module.__name__, level="WARNING") as events:
            with self.assertRaisesRegex(
                    self.module.DedicatedTvLoginDenied,
                    "Existing TV enrollment must be resolved"):
                await self.module.provision_dedicated_tv_login(
                    self.hass, self.journal, owner_id="owner",
                    tv_uuid=TV, ha_origin=ORIGIN)
        self.assertEqual(len(self.auth.created), 1)
        self.assertEqual(len(self.auth.tokens), 1)
        self.assertEqual(self.journal.snapshot()[0].state, "committed")
        self.assertIn("existing_tv_lease", events.output[0])
        self.assertNotIn(account.id, events.output[0])

    async def test_issuer_logs_only_secret_free_failure_phase(self):
        self.auth.fail_after_refresh = True
        with self.assertLogs(self.module.__name__, level="WARNING") as events:
            with self.assertRaises(self.module.DedicatedTvLoginDenied):
                await self.module.provision_dedicated_tv_login(
                    self.hass, self.journal, owner_id="owner",
                    tv_uuid=TV, ha_origin=ORIGIN)
        self.assertIn("access_issuance (ValueError)", events.output[0])
        self.assertNotIn("SECRET-", str(events.output))
        self.assertNotIn("simulated backend token error", str(events.output))
        self.assertNotIn(TV, str(events.output))
        self.assertEqual(self.auth.tokens, {})

    async def test_issuer_error_removes_new_token_and_preserves_intent(self):
        self.auth.fail_after_refresh = True
        with self.assertRaises(self.module.DedicatedTvLoginDenied):
            await self.module.provision_dedicated_tv_login(
                self.hass, self.journal, owner_id="owner",
                tv_uuid=TV, ha_origin=ORIGIN)
        self.assertEqual(self.auth.tokens, {})
        self.assertEqual(self.auth.created, [])
        self.assertEqual(len(self.auth.removed_users), 1)
        self.assertEqual(len(self.journal.snapshot()), 1)



    async def test_bad_identity_never_creates_broad_tv_user(self):
        for tv in ("", "not-a-uuid", "123e4567-e89b-12d3-a456-426614174000",
                   TV.upper(), "123e4567-e89b-42d3-a456-42661417400"):
            with self.subTest(tv=tv):
                with self.assertRaises(self.module.DedicatedTvLoginDenied):
                    await self.module.provision_dedicated_tv_login(
                        self.hass, self.journal, owner_id="owner",
                        tv_uuid=tv, ha_origin=ORIGIN)
        self.assertEqual(self.auth.created, [])
        self.assertEqual(self.auth.tokens, [])

    async def test_failed_lease_write_removes_new_account(self):
        with patch.object(self.journal, "begin_intent",
                          new_callable=AsyncMock,
                          side_effect=RuntimeError("simulated journal write failure")):
            with self.assertRaises(self.module.DedicatedTvLoginDenied):
                await self.module.provision_dedicated_tv_login(
                    self.hass, self.journal, owner_id="owner",
                    tv_uuid=TV, ha_origin=ORIGIN)
        self.assertEqual(self.auth.created, [])
        self.assertEqual(len(self.auth.removed_users), 1)
        self.assertEqual(self.auth.tokens, {})
        self.assertEqual(self.journal.snapshot(), ())

    async def test_user_removal_failure_deactivates_new_user(self):
        self.auth.fail_after_refresh = True
        self.auth.fail_user_removal = True
        with self.assertRaises(self.module.DedicatedTvLoginDenied):
            await self.module.provision_dedicated_tv_login(
                self.hass, self.journal, owner_id="owner",
                tv_uuid=TV, ha_origin=ORIGIN)
        self.assertEqual(len(self.auth.created), 1)
        self.assertFalse(self.auth.created[0].is_active)
        self.assertEqual(len(self.auth.deactivated_users), 1)
        self.assertEqual(self.auth.tokens, {})

    async def test_two_simultaneous_attempts_for_same_tv_keep_one_account(self):
        async def enroll():
            try:
                return await self.module.provision_dedicated_tv_login(
                    self.hass, self.journal, owner_id="owner",
                    tv_uuid=TV, ha_origin=ORIGIN)
            except self.module.DedicatedTvLoginDenied:
                return None
        results = await asyncio.gather(enroll(), enroll())
        self.assertEqual(sum(result is not None for result in results), 1)
        self.assertEqual(len(self.auth.created), 1)
        self.assertEqual(len(self.auth.tokens), 1)
        self.assertEqual(len(self.journal.snapshot()), 1)


class TvLanCredentialRouteTests(unittest.IsolatedAsyncioTestCase):
    """Exercise the real aiohttp route guard without network or secrets."""

    async def test_post_only_encrypted_handoff_routes_and_no_other_routes(self):
        route_pkg = "kv_v3qa_route_test"
        module = types.ModuleType(route_pkg)
        module.__path__ = [str(ROOT)]
        with patch.dict(sys.modules, {route_pkg: module}):
            wire = importlib.import_module(route_pkg + ".pairing_v3_wire")
        transport = wire.AiohttpV3LanTransport("192.168.1.50", 12345)
        requests = []

        class FakeResponse:
            status = 200

            def __init__(self):
                self.content = self

            async def read(self, max_bytes):
                self_max = max_bytes
                assert self_max == wire._MAX_BODY + 1
                return b'{"ok":true}'

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return False

        class FakeSession:
            def __init__(self, **kwargs):
                assert kwargs["trust_env"] is False

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return False

            def request(self, method, url, **kwargs):
                assert kwargs["allow_redirects"] is False
                requests.append((method, url))
                return FakeResponse()

        fake_aiohttp = types.SimpleNamespace(
            ClientTimeout=lambda **kw: kw,
            DummyCookieJar=lambda: object(),
            ClientSession=FakeSession,
        )
        with patch.dict(sys.modules, {"aiohttp": fake_aiohttp}):
            for path in ("/v3/pair/token-stage", "/v3/pair/token-commit"):
                self.assertEqual(
                    await transport.post(path, {"protocol": 3}),
                    {"ok": True},
                )
            self.assertEqual(requests, [
                ("POST", "http://192.168.1.50:12345/v3/pair/token-stage"),
                ("POST", "http://192.168.1.50:12345/v3/pair/token-commit"),
            ])
            for path in ("/v3/pair/token-stage", "/v3/pair/token-commit",
                         "/v3/pair/owner-receipt"):
                with self.subTest(method="GET", path=path):
                    with self.assertRaisesRegex(ValueError, "Unrecognized v3 route"):
                        await transport.get(path, {"protocol": 3})
            for path in ("/v2/pair/token-stage", "/v3/pair/unexpected",
                         "http://example.test/v3/pair/token-stage"):
                with self.subTest(method="POST", path=path):
                    with self.assertRaisesRegex(ValueError, "Unrecognized v3 route"):
                        await transport.post(path, {"protocol": 3})
            self.assertEqual(len(requests), 2)


if __name__ == "__main__":
    unittest.main()
