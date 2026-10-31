"""Cross-language KVPair/v3 commitment proof only, never live credentials."""
from __future__ import annotations

import base64
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
spec = importlib.util.spec_from_file_location("kv_v3_commit", ROOT / "pairing_key_commitment.py")
proto = importlib.util.module_from_spec(spec)
spec.loader.exec_module(proto)

SID = "AQIDBAUGBwgJCgsMDQ4PEA"
TV = base64.urlsafe_b64decode(
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q=="
)
HA = base64.urlsafe_b64decode(
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q=="
)
TV_NONCE = bytes(range(32))
HA_NONCE = bytes(range(32, 64))


class CommitmentInteropTests(unittest.TestCase):
    def test_fixed_python_java_vector(self):
        self.assertEqual(
            "df28eb33b2fb5c6d5e45e8732a5893e3c938892dc2ff642361235f7b5f1a9f2d",
            proto.commitment("tv", SID, TV, TV_NONCE).hex()
        )
        self.assertEqual(
            "f6fbfcfefc4a3aa3c9ccb39a9bd97888a2c02a564e85c1a869681fe02dc9bd9c",
            proto.commitment("ha", SID, HA, HA_NONCE).hex()
        )

    def test_commit_both_before_any_key_reveal_then_verify(self):
        tv = proto.CommitmentSession("tv", SID, TV, TV_NONCE)
        ha = proto.CommitmentSession("ha", SID, HA, HA_NONCE)
        with self.assertRaises(ValueError):
            tv.reveal()
        self.assertFalse(tv.ready)
        tv.receive_peer_commit(ha.public_commit)
        ha.receive_peer_commit(tv.public_commit)
        tv_key, tv_nonce = tv.reveal()
        ha_key, ha_nonce = ha.reveal()
        tv.receive_peer_reveal(ha_key, ha_nonce)
        ha.receive_peer_reveal(tv_key, tv_nonce)
        self.assertTrue(tv.ready)
        self.assertTrue(ha.ready)
        self.assertEqual(
            (SID, TV, HA, TV_NONCE, HA_NONCE), tv.verified_transcript()
        )
        self.assertEqual(
            (SID, HA, TV, HA_NONCE, TV_NONCE), ha.verified_transcript()
        )
        with self.assertRaises(ValueError):
            tv.reveal()
        with self.assertRaises(ValueError):
            ha.receive_peer_reveal(tv_key, tv_nonce)
        tv.close()
        self.assertFalse(tv.ready)
        with self.assertRaises(ValueError):
            tv.verified_transcript()

    def test_last_mover_cannot_substitute_its_key_after_commit(self):
        tv = proto.CommitmentSession("tv", SID, TV, TV_NONCE)
        ha = proto.CommitmentSession("ha", SID, HA, HA_NONCE)
        tv.receive_peer_commit(ha.public_commit)
        ha.receive_peer_commit(tv.public_commit)
        tv.reveal()
        ha.reveal()
        changed_key = HA[:-1] + bytes([HA[-1] ^ 1])
        with self.assertRaises(ValueError):
            tv.receive_peer_reveal(changed_key, HA_NONCE)
        self.assertFalse(tv.ready)
        with self.assertRaises(ValueError):
            tv.receive_peer_reveal(HA, HA_NONCE)

    def test_nonce_session_and_role_cannot_be_swapped(self):
        expected = proto.commitment("tv", SID, TV, TV_NONCE)
        self.assertNotEqual(expected, proto.commitment("tv", SID, TV, bytes([0])*32))
        self.assertNotEqual(expected, proto.commitment("ha", SID, TV, TV_NONCE))
        self.assertNotEqual(expected, proto.commitment(
            "tv", "AQIDBAUGBwgJCgsMDQ4PEQ", TV, TV_NONCE))
        for role, session, key, nonce in [
            ("bad", SID, TV, TV_NONCE),
            ("tv", "not-a-session", TV, TV_NONCE),
            ("tv", SID, b"bad", TV_NONCE),
            ("tv", SID, TV, b"short"),
        ]:
            with self.subTest(role=role,session=session), self.assertRaises(ValueError):
                proto.commitment(role, session, key, nonce)

    def test_duplicate_reflected_bad_size_and_stale_state_rejected(self):
        tv = proto.CommitmentSession("tv", SID, TV, TV_NONCE)
        with self.assertRaises(ValueError):
            tv.receive_peer_commit(tv.public_commit)
        with self.assertRaises(ValueError):
            tv.receive_peer_commit(b"short")
        peer = proto.commitment("ha", SID, HA, HA_NONCE)
        tv.receive_peer_commit(peer)
        with self.assertRaises(ValueError):
            tv.receive_peer_commit(peer)
        with self.assertRaises(ValueError):
            tv.receive_peer_reveal(HA, HA_NONCE)
        tv.reveal()
        with self.assertRaises(ValueError):
            tv.receive_peer_reveal(HA, bytes(32))
        self.assertFalse(tv.ready)


if __name__ == "__main__":
    unittest.main()
