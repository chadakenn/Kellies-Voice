"""Full real P-256 v3 HA↔synthetic TV encrypted owner receipt wire QA.

Not a hardware test. Exercises the production HA v3 driver and strict
network JSON, authenticated HA owner registry, TV-side AES-GCM decryption,
and HA receipt acknowledgement with no login or secrets.
"""
from __future__ import annotations

import importlib
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock

try:
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    ec = None

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice_v3qa"
PKG = "kv_v3qa_full_receipt_wire"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg
wire = importlib.import_module(PKG + ".pairing_v3_wire")
keys = importlib.import_module(PKG + ".pairing_key_commitment")
verified = importlib.import_module(PKG + ".pairing_v3_verified")
claim = importlib.import_module(PKG + ".owner_claim")
if ec is not None:
    crypto = importlib.import_module(PKG + ".owner_receipt")
    channel = importlib.import_module(PKG + ".pairing_v3_qa_channel")
else:
    crypto = channel = None

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
TV = "123e4567-e89b-42d3-a456-426614174000"
FLOW = "flow-secure-ack-qa-1"


class FakeTvTransport:
    """Independently drives TV role's actual crypto primitives, not a fake ACK."""
    def __init__(self, *, alter_ack=False, drop_ack=False):
        self.key = ec.derive_private_key(1, ec.SECP256R1())
        self.barrier = keys.CommitmentSession(
            "tv", SESSION, verified.public_der(self.key), bytes(range(32)))
        self.state = "new"
        self.approved = False
        self.ack_count = 0
        self.alter_ack = alter_ack
        self.drop_ack = drop_ack
        self.http_payloads = []

    def _require(self, doc, expected):
        assert set(doc) == set(expected), doc
        assert doc["protocol"] == 3
        assert doc["session"] == SESSION

    async def post(self, route, doc):
        self.http_payloads.append((route, doc))
        if route == "/v3/pair/commit" and self.state == "new":
            self._require(doc, ("protocol", "session", "ha_commit"))
            self.barrier.receive_peer_commit(wire._decode(doc["ha_commit"], 32))
            self.state = "committed"
            return {"protocol": 3, "session": SESSION,
                    "tv_commit": wire._encode(self.barrier.public_commit)}
        if route == "/v3/pair/reveal-ha" and self.state == "committed":
            self._require(doc, ("protocol", "session", "ha_key", "ha_nonce"))
            self.held = self.barrier.reveal()
            self.barrier.receive_peer_reveal(
                wire._decode(doc["ha_key"]), wire._decode(doc["ha_nonce"], 32))
            self.state = "ha-verified"
            return {"protocol": 3, "session": SESSION, "ha_verified": True}
        if route == "/v3/pair/owner-receipt" and self.state == "approved":
            self._require(doc, ("protocol", "session", "sequence", "receipt", "login_next"))
            assert doc["login_next"] is False
            assert type(doc["sequence"]) is int
            assert type(doc["receipt"]) is str
            assert set(doc) == {"protocol", "session", "sequence", "receipt", "login_next"}
            try:
                tv = wire.VerifiedTv(TV, SESSION)
                with_ack = channel.V3QaPingChannel(
                    private_key=self.key, verified=self.barrier,
                    tv_uuid=TV, ttl_seconds=60)
                try:
                    verifier = crypto.TvOwnerReceiptVerifier(
                        private_key=self.key, verified=self.barrier, tv=tv)
                    assert verifier.accept(
                        doc["sequence"], wire._decode(doc["receipt"], 30))
                    seq, ciphertext = with_ack.seal(b"qa-pong")
                finally:
                    with_ack.close()
            finally:
                self.barrier.close()
                self.state = "complete"
            self.ack_count += 1
            if self.drop_ack:
                raise ValueError("Simulated TV disconnect")
            if self.alter_ack:
                ciphertext = ciphertext[:-1] + bytes([ciphertext[-1] ^ 1])
            return {"protocol": 3, "session": SESSION, "ack_seq": seq,
                    "ack": wire._encode(ciphertext)}
        raise ValueError("Unexpected transport step")

    async def get(self, route, doc):
        self._require(doc, ("protocol", "session"))
        if route == "/v3/pair/reveal-tv" and self.state == "ha-verified":
            self.state = "shown-code"
            return {"protocol": 3, "session": SESSION,
                    "tv_key": wire._encode(self.held[0]),
                    "tv_nonce": wire._encode(self.held[1])}
        if route == "/v3/pair/status" and self.state == "shown-code":
            if not self.approved:
                return {"protocol": 3, "session": SESSION, "approved": False}
            self.state = "approved"
            return {"protocol": 3, "session": SESSION, "approved": True,
                    "tv_uuid": TV,
                    "proof": verified.physical_tv_ok_proof(
                        self.key, self.barrier, tv_uuid=TV)}
        raise ValueError("Unexpected transport read")


@unittest.skipUnless(ec is not None, "Optional P-256 dependency absent in general HA CI")
class FullOwnerReceiptWireTests(unittest.IsolatedAsyncioTestCase):
    async def setup_flow(self, *, approved=True, alter_ack=False, drop_ack=False):
        tv = FakeTvTransport(alter_ack=alter_ack, drop_ack=drop_ack)
        ha = wire.HaV3WireAttempt(
            SESSION, private=ec.derive_private_key(2, ec.SECP256R1()),
            nonce=bytes(range(32, 64)), retain_owner_receipt=True)
        self.assertEqual("83428994", await ha.start(tv))
        self.assertIsNone(await ha.confirmed_by_tv_remote(tv))
        tv.approved = True
        checked = await ha.confirmed_by_tv_remote(tv)
        self.assertEqual(checked.tv_uuid, TV)
        self.assertTrue(ha._barrier.ready)
        registry = claim.OwnerClaimRegistry()
        registry.begin(flow_id=FLOW, session=SESSION, tv_uuid=TV,
                       comparison_code="83428994")
        owner = types.SimpleNamespace(
            id="actual-ha-owner", is_owner=True, is_active=True)
        if approved:
            self.assertTrue(registry.claim(
                flow_id=FLOW, session=SESSION, user=owner))
        hass = types.SimpleNamespace(auth=types.SimpleNamespace(
            async_get_user=AsyncMock(return_value=owner)))
        return ha, tv, registry, hass

    async def test_real_crypto_owner_claim_tv_receipt_and_authenticated_ack(self):
        ha, tv, registry, hass = await self.setup_flow()
        self.assertTrue(await ha.finish_owner_receipt(
            tv, hass=hass, claims=registry, flow_id=FLOW))
        self.assertEqual(tv.ack_count, 1)
        self.assertEqual(tv.state, "complete")
        self.assertFalse(ha._barrier.ready)
        self.assertEqual(
            set(tv.http_payloads[-1][1]),
            {"protocol", "session", "sequence", "receipt"})
        self.assertNotIn("token", repr(tv.http_payloads).lower())
        with self.assertRaises(ValueError):
            await ha.finish_owner_receipt(
                tv, hass=hass, claims=registry, flow_id=FLOW)

    async def test_missing_owner_never_sends_encrypted_receipt(self):
        ha, tv, registry, hass = await self.setup_flow(approved=False)
        with self.assertRaises(ValueError):
            await ha.finish_owner_receipt(
                tv, hass=hass, claims=registry, flow_id=FLOW)
        self.assertEqual(tv.ack_count, 0)
        self.assertEqual(
            [route for route, _ in tv.http_payloads
             if "owner-receipt" in route], [])
        self.assertFalse(ha._barrier.ready)

    async def test_tampered_ack_fail_closed(self):
        ha, tv, registry, hass = await self.setup_flow(alter_ack=True)
        with self.assertRaises(ValueError):
            await ha.finish_owner_receipt(
                tv, hass=hass, claims=registry, flow_id=FLOW)
        self.assertEqual(tv.ack_count, 1)
        self.assertFalse(ha._barrier.ready)

    async def test_lost_ack_is_not_false_success_and_burns_claim(self):
        ha, tv, registry, hass = await self.setup_flow(drop_ack=True)
        with self.assertRaises(ValueError):
            await ha.finish_owner_receipt(
                tv, hass=hass, claims=registry, flow_id=FLOW)
        self.assertFalse(ha._barrier.ready)
        self.assertFalse(registry.approved_for(
            flow_id=FLOW, session=SESSION, tv_uuid=TV))


if __name__ == "__main__":
    unittest.main()
