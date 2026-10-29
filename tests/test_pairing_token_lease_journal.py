"""Crash/restart regression tests for the *production* token lease journal.

Only synthetic user/token objects. Does not mint an OAuth token or
communicate with live HA, Android devices or Home Assistant Cloud.
"""
from __future__ import annotations

import asyncio
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
from unittest.mock import patch
import unittest

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / "custom_components/kellies_voice/pairing_token_lease_journal.py"
spec = importlib.util.spec_from_file_location("kv_token_journal_qa", FILE)
journal = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = journal
spec.loader.exec_module(journal)

TV = "123e4567-e89b-42d3-a456-426614174000"
USER = "dedicated-tv-user"
OWNER = "main-home-owner"
ORIGIN = "https://ha.example"
SECRET = "FAKE_SENSITIVE_REFRESH_TOKEN_NO_REAL_VALUE"


class FakeAuth:
    def __init__(self):
        self.user = SimpleNamespace(id=USER, refresh_tokens={})
        self.raise_on_remove = False
        self.removed = []

    async def async_get_user(self, user_id):
        return self.user if user_id == USER else None

    def async_remove_refresh_token(self, token):
        if self.raise_on_remove:
            raise RuntimeError(SECRET)
        self.removed.append(token.id)
        self.user.refresh_tokens.pop(token.id, None)

    def issue(self, intent, token_id="token-one"):
        token = SimpleNamespace(
            id=token_id, client_id=intent.client_id, user=self.user,
            token_type="normal",
        )
        self.user.refresh_tokens[token_id] = token
        return token


class FakeHass:
    def __init__(self):
        self.auth = FakeAuth()

    async def async_add_executor_job(self, func, *args):
        return await asyncio.to_thread(func, *args)


class JournalTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.path = Path(self.folder.name) / ".storage" / "kellies_voice_pairing_leases"
        self.hass = FakeHass()

    def new_journal(self):
        return journal.TokenLeaseJournal(self.hass, self.path)

    async def begin(self, j):
        return await j.begin_intent(
            origin=ORIGIN, tv_uuid=TV, tv_user_id=USER, owner_id=OWNER
        )

    async def test_intent_fsynced_before_token_and_no_refresh_secret(self):
        j = self.new_journal()
        lease = await self.begin(j)
        self.assertEqual("intent", lease.state)
        self.assertTrue(self.path.is_file())
        data = json.loads(self.path.read_text())
        self.assertEqual(1, data["version"])
        self.assertEqual("intent", data["leases"][0]["state"])
        self.assertEqual(32, len(lease.nonce))
        self.assertTrue(lease.client_id.endswith("/attempt/" + lease.nonce))
        self.assertNotIn(SECRET, self.path.read_text())
        self.assertEqual(0o600, self.path.stat().st_mode & 0o777)

    async def test_recover_after_crash_between_token_issuance_and_id_record(self):
        first = self.new_journal()
        lease = await self.begin(first)
        self.hass.auth.issue(lease)
        # Simulate HA process restart: entirely new journal object.
        restart = self.new_journal()
        result = await restart.recover_unfinished()
        self.assertEqual({lease.nonce: "revoked"}, result)
        self.assertEqual(["token-one"], self.hass.auth.removed)
        self.assertEqual("revoked", restart.snapshot()[0].state)
        second = self.new_journal()
        self.assertEqual({}, await second.recover_unfinished())
        self.assertEqual(["token-one"], self.hass.auth.removed)

    async def test_recover_when_issued_id_saved_before_crash(self):
        first = self.new_journal()
        lease = await self.begin(first)
        self.hass.auth.issue(lease)
        await first.record_id(nonce=lease.nonce, token_id="token-one")
        replay = self.new_journal()
        self.assertEqual({lease.nonce: "revoked"},
                         await replay.recover_unfinished())
        self.assertEqual(["token-one"], self.hass.auth.removed)

    async def test_committed_lease_is_never_reaped(self):
        j = self.new_journal()
        lease = await self.begin(j)
        self.hass.auth.issue(lease)
        await j.record_id(nonce=lease.nonce, token_id="token-one")
        await j.commit(lease.nonce)
        fresh = self.new_journal()
        self.assertEqual({}, await fresh.recover_unfinished())
        self.assertIn("token-one", self.hass.auth.user.refresh_tokens)
        self.assertEqual("committed", fresh.snapshot()[0].state)

    async def test_revoke_failed_persists_retry_required_across_restart(self):
        j = self.new_journal()
        lease = await self.begin(j)
        self.hass.auth.issue(lease)
        self.hass.auth.raise_on_remove = True
        self.assertEqual({lease.nonce: "retry_required"},
                         await self.new_journal().recover_unfinished())
        disk = json.loads(self.path.read_text())
        self.assertEqual("revoking", disk["leases"][0]["state"])
        self.assertNotIn(SECRET, self.path.read_text())
        self.hass.auth.raise_on_remove = False
        self.assertEqual({lease.nonce: "revoked"},
                         await self.new_journal().recover_unfinished())

    async def test_disk_failure_during_preissuance_rejects_and_does_not_publish(self):
        j = self.new_journal()
        with patch.object(journal, "_write_atomic",
                          side_effect=journal.LeaseRecoveryError("write failed")):
            with self.assertRaises(journal.LeaseRecoveryError):
                await self.begin(j)
        self.assertFalse(self.path.exists())
        self.assertEqual((), j.snapshot())

    async def test_disk_failure_during_record_id_preserves_intent(self):
        j = self.new_journal()
        lease = await self.begin(j)
        self.hass.auth.issue(lease)
        with patch.object(journal, "_write_atomic",
                          side_effect=journal.LeaseRecoveryError("write failed")):
            with self.assertRaises(journal.LeaseRecoveryError):
                await j.record_id(nonce=lease.nonce, token_id="token-one")
        self.assertEqual("intent", j.snapshot()[0].state)
        restart = self.new_journal()
        self.assertEqual({lease.nonce: "revoked"},
                         await restart.recover_unfinished())

    async def test_recovery_rejects_wrong_user_or_client_without_revoking(self):
        j = self.new_journal()
        lease = await self.begin(j)
        token = self.hass.auth.issue(lease)
        token.client_id = ORIGIN + "/unrelated"
        self.assertEqual({lease.nonce: "revoked"},
                         await self.new_journal().recover_unfinished())
        self.assertEqual([], self.hass.auth.removed)
        self.assertIn(token.id, self.hass.auth.user.refresh_tokens)

    async def test_multiple_matches_does_not_revoke_ambiguous_identity(self):
        j = self.new_journal()
        lease = await self.begin(j)
        self.hass.auth.issue(lease, "token-one")
        self.hass.auth.issue(lease, "token-two")
        self.assertEqual({lease.nonce: "retry_required"},
                         await self.new_journal().recover_unfinished())
        self.assertEqual([], self.hass.auth.removed)
        self.assertEqual("intent", self.new_journal_snapshot_state())

    def new_journal_snapshot_state(self):
        doc = json.loads(self.path.read_text())
        return doc["leases"][0]["state"]

    async def test_duplicate_tv_uncommitted_or_committed_refused(self):
        j = self.new_journal()
        lease = await self.begin(j)
        with self.assertRaises(journal.LeaseRecoveryError):
            await self.begin(j)
        self.hass.auth.issue(lease)
        await j.record_id(nonce=lease.nonce, token_id="token-one")
        await j.commit(lease.nonce)
        with self.assertRaises(journal.LeaseRecoveryError):
            await self.begin(j)

    async def test_wrong_token_id_cannot_commit(self):
        j = self.new_journal()
        lease = await self.begin(j)
        with self.assertRaises(journal.LeaseRecoveryError):
            await j.commit(lease.nonce)
        with self.assertRaises(journal.LeaseRecoveryError):
            await j.record_id(nonce=lease.nonce, token_id="malformed space")
        self.assertEqual("intent", j.snapshot()[0].state)

    async def test_corrupt_file_fail_closed_without_overwriting(self):
        self.path.parent.mkdir(parents=True)
        self.path.write_text('{"version":1,"leases":"invalid"}')
        before = self.path.read_text()
        with self.assertRaises(journal.LeaseRecoveryError):
            await self.begin(self.new_journal())
        self.assertEqual(before, self.path.read_text())

    async def test_unavailable_user_stays_pending_not_marked_revoked(self):
        j = self.new_journal()
        lease = await self.begin(j)
        self.hass.auth.issue(lease)
        self.hass.auth.user.id = "another-user"
        result = await self.new_journal().recover_unfinished()
        self.assertEqual({lease.nonce: "retry_required"}, result)
        self.assertEqual("intent", self.new_journal_snapshot_state())

if __name__ == "__main__":
    unittest.main()
