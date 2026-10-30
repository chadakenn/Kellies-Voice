"""Synthetic HA AuthManager test of restricted renewable TV issuance and revoke.

Zero real users, tokens, network requests or HA configuration changes.
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

ROOT = (Path(__file__).resolve().parents[1] /
        "custom_components/kellies_voice_v3qa")
PKG = "kv_safe_renewable_login_test"
package = types.ModuleType(PKG)
package.__path__ = [str(ROOT)]
sys.modules[PKG] = package
issuer = importlib.import_module(PKG + ".scoped_login_issuer")
journal_module = importlib.import_module(PKG + ".token_lease_journal")

TV = "123e4567-e89b-42d3-a456-426614174000"
ALERT = "select.tv_alert_position"
LIGHT = "light.office"
CAM = "camera.doorbell"
OTHER = "lock.front_door"
CLIENT = "https://ha.example.test"


class Permissions:
    def __init__(self, grants=None):
        self.grants = grants or {
            ALERT: {"read"}, LIGHT: {"read", "control"},
            CAM: {"read", "control"},
        }

    def access_all_entities(self, op):
        return False

    def check_entity(self, entity, op):
        return op in self.grants.get(entity, set())


def make_user(*, group=None, owner=False, uid="tv-restricted"):
    if group is None:
        group = types.SimpleNamespace(
            id="tv-scope-only",
            policy={"entities": {"entity_ids": {
                ALERT: {"read": True},
                LIGHT: {"read": True, "control": True},
                CAM: {"read": True, "control": True},
            }}}
        )
    return types.SimpleNamespace(
        id=uid, is_owner=owner, is_admin=owner,
        is_active=True, system_generated=False,
        local_only=True, groups=[group], permissions=Permissions(),
        refresh_tokens={},
    )


class FakeAuth:
    def __init__(self, *, user):
        self.user = user
        self.owner = make_user(owner=True, uid="owner")
        self.tokens = {}
        self.issue_calls = 0
        self.fail_access = False

    async def async_get_user(self, uid):
        return {"owner": self.owner, self.user.id: self.user}.get(uid)

    async def async_create_refresh_token(self, user, client_id, token_type="normal"):
        self.issue_calls += 1
        assert user is self.user and token_type == "normal"
        token_id = "tv-token-identifier-" + str(self.issue_calls)
        obj = types.SimpleNamespace(
            id=token_id, token="SENSITIVE-REFRESH-SECRET",
            client_id=client_id, token_type=token_type, user=user,
            access_token_expiration=timedelta(minutes=30),
        )
        user.refresh_tokens[token_id] = obj
        self.tokens[token_id] = obj
        return obj

    def async_create_access_token(self, token):
        if self.fail_access:
            raise RuntimeError("Access token issuance failed")
        return "SENSITIVE-ACCESS-SECRET"

    def async_get_refresh_token(self, uid):
        return self.tokens.get(uid)

    def async_remove_refresh_token(self, obj):
        self.tokens.pop(obj.id, None)
        obj.user.refresh_tokens.pop(obj.id, None)


class SafeRenewalTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addAsyncCleanup(self._cleanup)
        self.user = make_user()
        self.auth = FakeAuth(user=self.user)
        self.hass = types.SimpleNamespace(
            auth=self.auth,
            states=types.SimpleNamespace(
                async_entity_ids=lambda: [ALERT, LIGHT, CAM, OTHER]),
            async_add_executor_job=lambda func, *args: asyncio.to_thread(func, *args),
        )
        self.journal = journal_module.TokenLeaseJournal(
            self.hass, Path(self.tmp.name) / "private-lease-journal.json",
        )
        self.args = dict(
            trusted_ha_origin=CLIENT, owner_id="owner",
            tv_user_id=self.user.id, tv_uuid=TV,
            alert_entity_id=ALERT, assist_entity_ids=(LIGHT, CAM),
        )

    async def _cleanup(self):
        self.tmp.cleanup()

    async def test_issues_renewable_token_after_fsynced_durable_intent(self):
        result = await issuer.issue_provisional(
            self.hass, self.journal, **self.args)
        self.assertEqual(result.access_token_seconds, 1800)
        self.assertEqual(result.refresh_token, "SENSITIVE-REFRESH-SECRET")
        self.assertEqual(result.lease.tv_user_id, self.user.id)
        self.assertEqual(result.lease.owner_id, "owner")
        self.assertEqual(result.lease.state, "intent")
        data = (Path(self.tmp.name) / "private-lease-journal.json").read_text()
        self.assertNotIn("SENSITIVE", data)
        self.assertIn("issued", data)
        self.assertNotIn("refresh_token", data)
        self.assertNotIn("access_token", data)
        self.assertIn("https://ha.example.test", data)

    async def test_regular_ha_users_group_refused_before_token(self):
        self.user.groups = [types.SimpleNamespace(
            id="system-users", policy={"entities": True})]
        with self.assertRaises(issuer.TvLoginDenied):
            await issuer.issue_provisional(self.hass, self.journal, **self.args)
        self.assertEqual(self.auth.issue_calls, 0)

    async def test_owner_id_and_admin_user_refused(self):
        for update in (
            {"owner_id": "tv-restricted"},
            {"owner_id": "unknown-owner"},
            {"tv_user_id": "owner"},
        ):
            with self.subTest(update=update):
                with self.assertRaises(issuer.TvLoginDenied):
                    await issuer.issue_provisional(
                        self.hass, self.journal, **(self.args | update))
        self.assertEqual(self.auth.issue_calls, 0)

    async def test_http_and_untrusted_origin_refused(self):
        for url in (
            "http://ha.local:8123", "https://evil.example/redirect",
            "https://user:password@ha.example.test", "https://ha.example.test?q=a",
            "https://ha.example.test#fragment", "ftp://ha.example.test",
        ):
            with self.subTest(url=url):
                with self.assertRaises(issuer.TvLoginDenied):
                    await issuer.issue_provisional(self.hass, self.journal, **(
                        self.args | {"trusted_ha_origin": url}))
        self.assertEqual(self.auth.issue_calls, 0)

    async def test_unrelated_entity_control_prevents_credentials(self):
        self.user.permissions.grants[OTHER] = {"read", "control"}
        with self.assertRaises(issuer.TvLoginDenied):
            await issuer.issue_provisional(self.hass, self.journal, **self.args)
        self.assertEqual(self.auth.issue_calls, 0)

    async def test_access_failure_revokes_issued_token(self):
        self.auth.fail_access = True
        with self.assertRaises(issuer.TvLoginDenied):
            await issuer.issue_provisional(self.hass, self.journal, **self.args)
        self.assertEqual(self.auth.tokens, {})
        self.assertEqual(len(self.journal.snapshot()), 1)

    async def test_revoke_only_matching_id_and_no_tv_supplied_handle(self):
        result = await issuer.issue_provisional(
            self.hass, self.journal, **self.args)
        recorded = next(iter(self.journal.snapshot()))
        self.assertTrue(issuer.revoke_by_saved_lease(self.hass, recorded))
        self.assertFalse(issuer.revoke_by_saved_lease(self.hass, recorded))
        self.assertEqual(self.auth.tokens, {})
        self.assertEqual(self.auth.issue_calls, 1)
        with self.assertRaises(issuer.TvLoginDenied):
            issuer.revoke_by_saved_lease(self.hass, dict(token_id="x"))

    async def test_commit_only_after_cryptographic_activation_ack_and_final_scope_check(self):
        from cryptography.hazmat.primitives.asymmetric import ec
        keys = importlib.import_module(PKG + ".pairing_key_commitment")
        v3 = importlib.import_module(PKG + ".pairing_v3_verified")
        wire = importlib.import_module(PKG + ".pairing_v3_wire")
        handoff = importlib.import_module(PKG + ".scoped_token_handoff")
        issued = await issuer.issue_provisional(
            self.hass, self.journal, **self.args)
        lease = next(iter(self.journal.snapshot()))
        tv_priv = ec.derive_private_key(1, ec.SECP256R1())
        ha_priv = ec.derive_private_key(2, ec.SECP256R1())
        sid = "AQIDBAUGBwgJCgsMDQ4PEA"
        tv = keys.CommitmentSession(
            "tv", sid, v3.public_der(tv_priv), bytes(range(32)))
        ha = keys.CommitmentSession(
            "ha", sid, v3.public_der(ha_priv), bytes(range(32, 64)))
        tv.receive_peer_commit(ha.public_commit)
        ha.receive_peer_commit(tv.public_commit)
        tv_pub, tv_nonce = tv.reveal()
        ha_pub, ha_nonce = ha.reveal()
        tv.receive_peer_reveal(ha_pub, ha_nonce)
        ha.receive_peer_reveal(tv_pub, tv_nonce)
        identity = wire.VerifiedTv(tv_uuid=TV, session=sid)
        server = handoff.HaTokenHandoff(
            private_key=ha_priv, verified=ha, tv=identity, now=100)
        client = handoff.TvTokenHandoff(
            private_key=tv_priv, verified=tv, tv=identity,
            expected_ha_origin=CLIENT, now=100)
        with self.assertRaises(issuer.TvLoginDenied):
            await issuer.finalize_after_authenticated_activation(
                self.hass, self.journal, lease=lease, handoff=server,
                alert_entity_id=ALERT, assist_entity_ids=(LIGHT, CAM))
        n, sealed = server.seal_recorded_provisional(issued, lease, now=101)
        grant = client.open_provisional(n, sealed, now=101)
        self.assertEqual(grant.token_id, lease.token_id)
        n, ack = client.stage_ack(now=102)
        server.accept_stage_ack(n, ack, now=102)
        n, ready = server.seal_commit_ready(now=103)
        client.accept_commit_ready(n, ready, now=103)
        n, final_ack = client.activation_ack(now=104)
        self.assertTrue(server.accept_activation_ack(n, final_ack, now=104))
        await issuer.finalize_after_authenticated_activation(
            self.hass, self.journal, lease=lease, handoff=server,
            alert_entity_id=ALERT, assist_entity_ids=(LIGHT, CAM))
        self.assertEqual(next(iter(self.journal.snapshot())).state, "committed")
        restored = journal_module.TokenLeaseJournal(
            self.hass, Path(self.tmp.name) / "private-lease-journal.json")
        self.assertEqual(await restored.recover_unfinished(), {})
        self.assertEqual(len(self.auth.tokens), 1)

    async def test_restart_recovers_uncommitted_credential(self):
        await issuer.issue_provisional(
            self.hass, self.journal, **self.args)
        fresh = journal_module.TokenLeaseJournal(
            self.hass, Path(self.tmp.name) / "private-lease-journal.json")
        result = await fresh.recover_unfinished()
        self.assertEqual(set(result.values()), {"revoked"})
        self.assertEqual(self.auth.tokens, {})
        self.assertEqual(next(iter(fresh.snapshot())).state, "revoked")


if __name__ == "__main__":
    unittest.main()
