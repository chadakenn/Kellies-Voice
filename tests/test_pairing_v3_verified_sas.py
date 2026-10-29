"""Offline v3 SAS + UUID physical remote proof test, never accesses HA."""
from __future__ import annotations

import base64
import importlib
from pathlib import Path
import sys
import types
import unittest
from cryptography.hazmat.primitives.asymmetric import ec

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
PACKAGE = "kv_v3_verified_tests"
pkg = types.ModuleType(PACKAGE)
pkg.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = pkg

keys = importlib.import_module(PACKAGE + ".pairing_key_commitment")
verified = importlib.import_module(PACKAGE + ".pairing_v3_verified")
crypto = importlib.import_module(PACKAGE + ".pairing_verification")

SID = "AQIDBAUGBwgJCgsMDQ4PEA"
UUID = "123e4567-e89b-42d3-a456-426614174000"
OTHER_UUID = "223e4567-e89b-42d3-a456-426614174000"
TV_NONCE = bytes(range(32))
HA_NONCE = bytes(range(32, 64))


def make_sessions(tv_nonce=TV_NONCE, ha_nonce=HA_NONCE):
    tv_private = ec.derive_private_key(1, ec.SECP256R1())
    ha_private = ec.derive_private_key(2, ec.SECP256R1())
    tv = keys.CommitmentSession(
        "tv", SID, crypto.public_der(tv_private), tv_nonce)
    ha = keys.CommitmentSession(
        "ha", SID, crypto.public_der(ha_private), ha_nonce)
    return tv_private, ha_private, tv, ha


def finish(tv, ha):
    tv.receive_peer_commit(ha.public_commit)
    ha.receive_peer_commit(tv.public_commit)
    tv_key, tv_nonce = tv.reveal()
    ha_key, ha_nonce = ha.reveal()
    tv.receive_peer_reveal(ha_key, ha_nonce)
    ha.receive_peer_reveal(tv_key, tv_nonce)


class VerifiedSasTests(unittest.TestCase):
    def test_no_sas_or_tv_ok_mac_until_both_commits_and_reveals(self):
        tpriv, hpriv, tv, ha = make_sessions()
        for owner_key, barrier in ((tpriv, tv), (hpriv, ha)):
            with self.assertRaises(ValueError):
                verified.comparison_code(owner_key, barrier)
            with self.assertRaises(ValueError):
                verified.physical_tv_ok_proof(owner_key, barrier, tv_uuid=UUID)
        tv.receive_peer_commit(ha.public_commit)
        ha.receive_peer_commit(tv.public_commit)
        tv.reveal()
        ha.reveal()
        with self.assertRaises(ValueError):
            verified.comparison_code(tpriv, tv)

    def test_verified_peer_sas_is_identical_and_proofs_match(self):
        tpriv, hpriv, tv, ha = make_sessions()
        finish(tv, ha)
        tv_code = verified.comparison_code(tpriv, tv)
        ha_code = verified.comparison_code(hpriv, ha)
        self.assertEqual(tv_code, ha_code)
        self.assertRegex(tv_code, r"^[0-9]{8}$")
        # Frozen cross-language fixture using P-256 private scalars 1/2.
        self.assertEqual("83428994", tv_code)
        tv_proof = verified.physical_tv_ok_proof(tpriv, tv, tv_uuid=UUID)
        ha_expected = verified.physical_tv_ok_proof(hpriv, ha, tv_uuid=UUID)
        self.assertEqual(tv_proof, ha_expected)
        self.assertEqual("TuUIMiT-zkUz3XaKygd5Gw", tv_proof)
        self.assertEqual(22, len(tv_proof))
        self.assertNotEqual(tv_proof, verified.physical_tv_ok_proof(
            tpriv, tv, tv_uuid=OTHER_UUID))

    def test_derivation_is_not_v2_and_binds_both_peer_nonces(self):
        tpriv, hpriv, tv, ha = make_sessions()
        finish(tv, ha)
        code = verified.comparison_code(tpriv, tv)
        old_code = crypto.calculate(tpriv, local_is_tv=True, session=SID,
                                    tv_key=crypto.public_der(tpriv),
                                    ha_key=crypto.public_der(hpriv))
        self.assertNotEqual(old_code, code)
        tpriv2, hpriv2, tv2, ha2 = make_sessions(
            tv_nonce=bytes([32])*32, ha_nonce=HA_NONCE)
        finish(tv2, ha2)
        self.assertNotEqual(code, verified.comparison_code(tpriv2, tv2))
        self.assertEqual(verified.comparison_code(tpriv2, tv2),
                         verified.comparison_code(hpriv2, ha2))
        tpriv3, hpriv3, tv3, ha3 = make_sessions(
            tv_nonce=TV_NONCE, ha_nonce=bytes([64])*32)
        finish(tv3, ha3)
        self.assertNotEqual(code, verified.comparison_code(tpriv3, tv3))
        self.assertEqual(verified.comparison_code(tpriv3, tv3),
                         verified.comparison_code(hpriv3, ha3))

    def test_wrong_role_key_or_wrong_tv_identity_fails(self):
        tpriv, hpriv, tv, ha = make_sessions()
        finish(tv, ha)
        with self.assertRaises(ValueError):
            verified.comparison_code(hpriv, tv)
        with self.assertRaises(ValueError):
            verified.comparison_code(tpriv, ha)
        for invalid in ("", "ABC", UUID.upper(), "00000000-0000-0000-0000-000000000000"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                verified.physical_tv_ok_proof(tpriv, tv, tv_uuid=invalid)

    def test_tampered_commitment_never_produces_sas(self):
        tpriv, hpriv, tv, ha = make_sessions()
        tv.receive_peer_commit(ha.public_commit)
        ha.receive_peer_commit(tv.public_commit)
        tv_key, tv_nonce = tv.reveal()
        ha_key, ha_nonce = ha.reveal()
        tv.receive_peer_reveal(ha_key, ha_nonce)
        with self.assertRaises(ValueError):
            ha.receive_peer_reveal(tv_key, bytes([1])*32)
        with self.assertRaises(ValueError):
            verified.comparison_code(hpriv, ha)
        self.assertFalse(ha.ready)
        self.assertTrue(tv.ready)

    def test_closed_or_reflected_session_never_replays_proof(self):
        tpriv, hpriv, tv, ha = make_sessions()
        finish(tv, ha)
        tv.close()
        with self.assertRaises(ValueError):
            verified.comparison_code(tpriv, tv)
        with self.assertRaises(ValueError):
            verified.physical_tv_ok_proof(tpriv, tv, tv_uuid=UUID)


if __name__ == "__main__":
    unittest.main()
