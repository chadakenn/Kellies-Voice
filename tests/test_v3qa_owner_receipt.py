"""Actual v3 P-256 fixture + authenticated owner registry -> sealed receipt.

No tokens, network listeners, live HA accounts, or real TV installation.
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
    # General HA runner skips optional crypto; dedicated receipt CI installs it.
    ec = None

ROOT = Path(__file__).resolve().parents[1] / "qa/ha_v3qa/kellies_voice_v3qa"
PKG = "kv_v3qa_owner_receipt_tests"
SID = "AQIDBAUGBwgJCgsMDQ4PEA"
SID2 = "AQIDBAUGBwgJCgsMDQ4PEQ"
TV_ID = "123e4567-e89b-42d3-a456-426614174000"
TV_ID2 = "123e4567-e89b-42d3-a456-426614174001"
FLOW = "real-owner-flow-id"
FROZEN_RECEIPT = "e5604d85a1208646dc1e6db915a359d53967bb7822568f04b77b8ba2146c"


def pair(sid=SID):
    tv_key = ec.derive_private_key(1, ec.SECP256R1())
    ha_key = ec.derive_private_key(2, ec.SECP256R1())
    tv = keys.CommitmentSession(
        "tv", sid, crypto.public_der(tv_key), bytes(range(32)))
    ha = keys.CommitmentSession(
        "ha", sid, crypto.public_der(ha_key), bytes(range(32, 64)))
    tv.receive_peer_commit(ha.public_commit)
    ha.receive_peer_commit(tv.public_commit)
    tp, tn = tv.reveal()
    hp, hn = ha.reveal()
    tv.receive_peer_reveal(hp, hn)
    ha.receive_peer_reveal(tp, tn)
    return tv_key, ha_key, tv, ha


pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg
keys = importlib.import_module(PKG + ".pairing_key_commitment")
crypto = importlib.import_module(PKG + ".pairing_v3_verified")
wire = importlib.import_module(PKG + ".pairing_v3_wire")
claims_mod = importlib.import_module(PKG + ".owner_claim")
receipt = (importlib.import_module(PKG + ".owner_receipt")
           if ec is not None else None)


@unittest.skipUnless(ec is not None, "v3 cryptography dependency not installed")
class OwnerReceiptTests(unittest.IsolatedAsyncioTestCase):
    def make(self, *, tv_uuid=TV_ID, sid=SID, claim=True, owner_active=True):
        tv_priv, ha_priv, tv_session, ha_session = pair(sid)
        obj = wire.VerifiedTv(tv_uuid=tv_uuid, session=sid)
        registry = claims_mod.OwnerClaimRegistry()
        registry.begin(flow_id=FLOW, session=sid, tv_uuid=tv_uuid,
                       comparison_code="83428994", now=100)
        owner = types.SimpleNamespace(
            id="actual-ha-owner", is_owner=True, is_active=owner_active)
        if claim:
            self.assertTrue(registry.claim(
                flow_id=FLOW, session=sid, user=owner, now=101))
        hass = types.SimpleNamespace(
            auth=types.SimpleNamespace(async_get_user=AsyncMock(return_value=owner)))
        return tv_priv, ha_priv, tv_session, ha_session, obj, registry, hass

    async def mint(self, context, *, now=102, **kwargs):
        _, ha_key, _, ha, obj, registry, hass = context
        return await receipt.issue_after_owner_claim(
            hass=hass, claims=registry, flow_id=FLOW,
            verified_tv=obj, private_key=ha_key, verified=ha,
            now=now, **kwargs)

    async def test_valid_owner_only_produces_fixed_cross_language_ciphertext(self):
        ctx = self.make()
        sequence, encrypted = await self.mint(ctx)
        self.assertEqual(sequence, 1)
        self.assertEqual(encrypted.hex(), FROZEN_RECEIPT)
        tv_key, _, tv, _, obj, _, _ = ctx
        verifier = receipt.TvOwnerReceiptVerifier(
            private_key=tv_key, verified=tv, tv=obj, now=100)
        self.assertTrue(verifier.accept(sequence, encrypted, now=102))
        self.assertTrue(verifier.closed)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            verifier.accept(sequence, encrypted, now=103)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await self.mint(ctx)

    async def test_cannot_mint_without_authenticated_owner_approval(self):
        ctx = self.make(claim=False)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await self.mint(ctx)
        # A failed attempt cannot forge or consume an unclaimed owner request.
        actual_owner = types.SimpleNamespace(
            id="real-owner", is_owner=True, is_active=True)
        self.assertEqual(len(ctx[-2].pending(user=actual_owner, now=102)), 1)

    async def test_nonowner_inactive_or_replaced_owner_never_gets_receipt(self):
        for value in (
            types.SimpleNamespace(id="actual-ha-owner", is_owner=False,
                                  is_active=True),
            types.SimpleNamespace(id="actual-ha-owner", is_owner=True,
                                  is_active=False),
            types.SimpleNamespace(id="different-owner", is_owner=True,
                                  is_active=True),
            None,
        ):
            with self.subTest(value=value):
                ctx = self.make()
                ctx[-1].auth.async_get_user.return_value = value
                with self.assertRaises(receipt.OwnerReceiptRejected):
                    await self.mint(ctx)
                # Owner claim was burned even on failed AuthManager recheck.
                with self.assertRaises(receipt.OwnerReceiptRejected):
                    await self.mint(ctx)

    async def test_expiry_wrong_flow_id_wrong_tv_and_bad_session(self):
        ctx = self.make()
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await self.mint(ctx, now=221)
        ctx = self.make()
        bad = wire.VerifiedTv(tv_uuid=TV_ID2, session=SID)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await receipt.issue_after_owner_claim(
                hass=ctx[-1], claims=ctx[-2], flow_id=FLOW,
                verified_tv=bad, private_key=ctx[1],
                verified=ctx[3], now=102)
        ctx = self.make()
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await receipt.issue_after_owner_claim(
                hass=ctx[-1], claims=ctx[-2], flow_id="wrong-flow-id",
                verified_tv=ctx[4], private_key=ctx[1],
                verified=ctx[3], now=102)
        ctx = self.make()
        wrong_session = wire.VerifiedTv(tv_uuid=TV_ID, session=SID2)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await receipt.issue_after_owner_claim(
                hass=ctx[-1], claims=ctx[-2], flow_id=FLOW,
                verified_tv=wrong_session, private_key=ctx[1],
                verified=ctx[3], now=102)

    async def test_ciphertext_authenticates_exact_tv_session_and_role(self):
        ctx = self.make()
        seq, encrypted = await self.mint(ctx)
        tv_key, _, tv, _, obj, _, _ = ctx
        wrong_tv = wire.VerifiedTv(tv_uuid=TV_ID2, session=SID)
        v = receipt.TvOwnerReceiptVerifier(
            private_key=tv_key, verified=tv, tv=wrong_tv, now=100)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            v.accept(seq, encrypted, now=102)
        self.assertTrue(v.closed)
        wrong_key_ctx = self.make(sid=SID2)
        v2 = receipt.TvOwnerReceiptVerifier(
            private_key=wrong_key_ctx[0], verified=wrong_key_ctx[2],
            tv=wrong_key_ctx[4], now=100)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            v2.accept(seq, encrypted, now=102)
        with self.assertRaises(receipt.OwnerReceiptRejected):
            receipt.TvOwnerReceiptVerifier(
                private_key=ctx[1], verified=ctx[3], tv=obj, now=100)

    async def test_tampering_wrong_sequence_and_expiry_burn_single_attempt(self):
        ctx = self.make()
        seq, encrypted = await self.mint(ctx)
        for bad_seq, body, when in [
            (True, encrypted, 102),
            (0, encrypted, 102),
            (seq, encrypted[:-1] + bytes([encrypted[-1] ^ 1]), 102),
            (seq, encrypted, 160),
            (seq, encrypted, float("nan")),
        ]:
            with self.subTest(bad_seq=bad_seq, when=when):
                v = receipt.TvOwnerReceiptVerifier(
                    private_key=ctx[0], verified=ctx[2],
                    tv=ctx[4], now=100)
                with self.assertRaises(receipt.OwnerReceiptRejected):
                    v.accept(bad_seq, body, now=when)
                self.assertTrue(v.closed)

    async def test_unverified_tv_key_rejected_before_owner_claim_consumed(self):
        ctx = self.make()
        ctx[3].close()
        with self.assertRaises(receipt.OwnerReceiptRejected):
            await self.mint(ctx)
        self.assertEqual(len(ctx[-2].pending(
            user=types.SimpleNamespace(id="actual-ha-owner",
                                       is_owner=True, is_active=True),
            now=102)), 0) # Owner claimed; no longer visible in pending list.
        # The claim still exists because there was no verified crypto key.
        self.assertIsNotNone(ctx[-2].consume(
            flow_id=FLOW, session=SID, tv_uuid=TV_ID, now=103))


if __name__ == "__main__":
    unittest.main()
