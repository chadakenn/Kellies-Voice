"""Security contract for the draft in-memory AEAD handoff state machine.

All test candidates are synthetic, non-secret bytes. There is NO token
exchange, Home Assistant runtime, browser callback or HTTP listener.
"""
import importlib
import json
from pathlib import Path
import sys
import time
import types
import unittest
from unittest.mock import AsyncMock, MagicMock

from cryptography.hazmat.primitives.asymmetric import ec

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PACKAGE = "kellies_handoff_state_tests"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package

handoff = importlib.import_module(PACKAGE + ".pairing_handoff_state")
channel_mod = importlib.import_module(PACKAGE + ".pairing_channel")
crypto = importlib.import_module(PACKAGE + ".pairing_verification")
pending_mod = importlib.import_module(PACKAGE + ".pairing_oauth_pending")
token_mod = importlib.import_module(PACKAGE + ".pairing_token_exchange")

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
TV_UUID = "123e4567-e89b-42d3-a456-426614174000"
OTHER_UUID = "223e4567-e89b-42d3-a456-426614174000"
MOCK_CANDIDATE = b"SYNTHETIC NON-SECRET TEST PAYLOAD"


class HandoffTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tv_key = ec.derive_private_key(1, ec.SECP256R1())
        self.ha_key = ec.derive_private_key(2, ec.SECP256R1())
        self.tv_public = crypto.public_der(self.tv_key)
        self.ha_public = crypto.public_der(self.ha_key)
        self.now = [time.time()]
        self.tv = channel_mod.PairingSealedChannel(
            self.tv_key, local_is_tv=True, session=SESSION,
            tv_key=self.tv_public, ha_key=self.ha_public)
        self.ha = channel_mod.PairingSealedChannel(
            self.ha_key, local_is_tv=False, session=SESSION,
            tv_key=self.tv_public, ha_key=self.ha_public)
        self.handoff = handoff.HASealedHandoff(
            self.ha, owner_user_id="owner-1",
            tv_uuid=TV_UUID, session=SESSION,
            tv_key=self.tv_public, ha_key=self.ha_public,
            clock=lambda: self.now[0])

    def reply(self, kind, **fields):
        doc = self.handoff._base(kind)
        doc.update(fields)
        return self.tv.seal(json.dumps(doc, sort_keys=True).encode("ascii"))

    def through_proof(self):
        packet = self.handoff.credential_offer(MOCK_CANDIDATE)
        self.assertNotIn(MOCK_CANDIDATE, packet)
        offer = json.loads(self.tv.open(packet))
        self.assertEqual("credential_offer", offer["type"])
        self.assertEqual(TV_UUID, offer["tv_uuid"])
        self.assertEqual(SESSION, offer["session"])
        self.assertEqual(64, len(offer["transcript"]))
        proof = self.reply("tv_proof_receipt",
                           ack_for="credential_offer", assist_ok=True, alerts_ok=True)
        self.handoff.accept_tv_proof(proof)
        self.assertEqual(handoff.Stage.READY_TO_COMMIT, self.handoff.stage)
        self.assertFalse(self.handoff.ready_to_release_lease)

    def through_persisted(self):
        self.through_proof()
        packet = self.handoff.commit_authorization()
        auth = json.loads(self.tv.open(packet))
        self.assertEqual("ha_commit_authorization", auth["type"])
        self.assertTrue(auth["authorize_commit"])
        persisted = self.reply("tv_persisted_receipt",
                               ack_for="ha_commit_authorization",
                               verified_store_committed=True)
        self.handoff.accept_tv_persisted(persisted)
        self.assertTrue(self.handoff.ready_to_release_lease)
        self.assertEqual(handoff.Stage.PERSISTED, self.handoff.stage)

    async def test_full_four_messages_release_only_after_authenticated_persisted(self):
        self.through_persisted()
        exchanger = MagicMock()
        exchanger.exchange_from_consumed = AsyncMock(
            return_value=token_mod.AccessCredentials("a" * 20, 1800, "r" * 20))
        p = pending_mod.PendingOAuthTransaction(
            owner_user_id="owner-1", tv_session=SESSION,
            consumed={"dummy": "fixture"}, started=self.now[0])
        await p.exchange(exchanger)
        p.lease_for_provision(owner_user_id="owner-1", tv_session=SESSION)
        self.handoff.release_pending_lease(p)
        self.assertEqual(handoff.Stage.LEASE_RELEASED, self.handoff.stage)
        self.assertEqual("COMMITTED", p._state.name)
        self.assertIsNone(p._credentials)
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.release_pending_lease(p)
        self.assertEqual(handoff.Stage.LEASE_RELEASED, self.handoff.stage)

    async def test_missing_assist_or_alert_proof_revokes_without_release(self):
        for result in ({"assist_ok": False, "alerts_ok": True},
                       {"assist_ok": True, "alerts_ok": False}):
            with self.subTest(result=result):
                self.setUp()
                offer = self.handoff.credential_offer(MOCK_CANDIDATE)
                self.tv.open(offer)
                proof = self.reply("tv_proof_receipt", ack_for="credential_offer", **result)
                with self.assertRaises(handoff.HandoffRejected):
                    self.handoff.accept_tv_proof(proof)
                self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)
                self.assertFalse(self.handoff.ready_to_release_lease)

    async def test_tampered_wrong_tv_uuid_and_session_aborts(self):
        for field, changed in (("tv_uuid", OTHER_UUID),
                               ("session", "AQIDBAUGBwgJCgsMDQ4PEQ"),
                               ("transcript", "0" * 64),
                               ("expires_at", 99999),
                               ("v", True)):
            with self.subTest(field=field):
                self.setUp()
                self.tv.open(self.handoff.credential_offer(MOCK_CANDIDATE))
                doc = self.handoff._base("tv_proof_receipt")
                doc.update(ack_for="credential_offer", assist_ok=True, alerts_ok=True)
                doc[field] = changed
                packet = self.tv.seal(json.dumps(doc).encode("ascii"))
                with self.assertRaises(handoff.HandoffRejected):
                    self.handoff.accept_tv_proof(packet)
                self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)

    async def test_unencrypted_forged_receipt_and_wrong_sequence_fail_closed(self):
        self.handoff.credential_offer(MOCK_CANDIDATE)
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.accept_tv_proof(b'{"assist_ok":true,"alerts_ok":true}')
        self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.commit_authorization()

    async def test_early_commit_and_early_persisted_receipt_rejected(self):
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.commit_authorization()
        self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)
        self.setUp()
        offer = self.handoff.credential_offer(MOCK_CANDIDATE)
        self.tv.open(offer)
        premature = self.reply("tv_persisted_receipt",
                               ack_for="ha_commit_authorization",
                               verified_store_committed=True)
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.accept_tv_persisted(premature)
        self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)

    async def test_false_or_replayed_persisted_ack_not_accepted(self):
        self.through_proof()
        self.tv.open(self.handoff.commit_authorization())
        bad = self.reply("tv_persisted_receipt",
                         ack_for="ha_commit_authorization",
                         verified_store_committed=False)
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.accept_tv_persisted(bad)
        self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)

    async def test_timeout_prevents_offer_and_late_lease_release(self):
        self.now[0] += 121
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.credential_offer(MOCK_CANDIDATE)
        self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)
        self.setUp()
        self.through_persisted()
        self.now[0] += 121
        self.assertFalse(self.handoff.ready_to_release_lease)
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.release_pending_lease(None)

    async def test_wrong_owner_or_session_lease_rejected(self):
        self.through_persisted()
        p = MagicMock()
        p.owner_user_id = "wrong-owner"
        p.tv_session = SESSION
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.release_pending_lease(p)
        p.commit_after_proof.assert_not_called()
        self.assertEqual(handoff.Stage.ABORTED, self.handoff.stage)

    async def test_plaintext_oversize_or_wrong_role_channel_rejected(self):
        with self.assertRaises(handoff.HandoffRejected):
            self.handoff.credential_offer(b"x" * 1025)
        self.setUp()
        with self.assertRaises(handoff.HandoffRejected):
            handoff.HASealedHandoff(
                self.tv, owner_user_id="owner-1", tv_uuid=TV_UUID,
                session=SESSION, tv_key=self.tv_public, ha_key=self.ha_public)
        with self.assertRaises(handoff.HandoffRejected):
            handoff.HASealedHandoff(
                self.ha, owner_user_id="owner-1", tv_uuid=OTHER_UUID,
                session="AQIDBAUGBwgJCgsMDQ4PEQ",
                tv_key=self.tv_public, ha_key=self.ha_public)


if __name__ == "__main__":
    unittest.main()
