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
from unittest.mock import patch

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

    async def async_get_user(self, uid):
        if uid == "owner":
            return self.owner
        return next((x for x in self.created if x.id == uid), None)

    async def async_create_user(self, name, group_ids, local_only):
        assert group_ids == ["system-users"] and local_only is True
        result = types.SimpleNamespace(
            id="dedicated-user-" + str(len(self.created) + 1),
            name=name, is_active=True, is_owner=False, is_admin=False,
            system_generated=False, local_only=True,
            groups=[types.SimpleNamespace(id="system-users")],
            refresh_tokens={},
        )
        self.created.append(result)
        return result

    async def async_create_refresh_token(self, user, client_id):
        token_id = "created-refresh-id"
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
        self.assertEqual(recorded.token_id, "created-refresh-id")
        self.assertEqual(recorded.state, "issued")
        self.assertEqual(issued.access_token_seconds, 1800)
        on_disk = (Path(self.tmp.name) / "lease.json").read_text()
        self.assertIn("created-refresh-id", on_disk)
        self.assertNotIn("SECRET-", on_disk)

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

    async def test_issuer_error_removes_new_token_and_preserves_intent(self):
        self.auth.fail_after_refresh = True
        with self.assertRaises(self.module.DedicatedTvLoginDenied):
            await self.module.provision_dedicated_tv_login(
                self.hass, self.journal, owner_id="owner",
                tv_uuid=TV, ha_origin=ORIGIN)
        self.assertEqual(self.auth.tokens, {})
        self.assertEqual(len(self.journal.snapshot()), 1)


if __name__ == "__main__":
    unittest.main()
