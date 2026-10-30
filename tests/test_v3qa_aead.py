"""Fixed cross-language AES-GCM vectors from real v3 P-256 ECDH transcript."""
import importlib
import sys
import types
import unittest
from pathlib import Path
try:
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    # General HA validation does not install optional crypto dependencies;
    # dedicated v3 QA CI installs them and MUST execute these tests.
    ec = None

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice_v3qa"
PKG = "kv_v3qa_aead_tests"
package = types.ModuleType(PKG)
package.__path__ = [str(ROOT)]
sys.modules[PKG] = package
keys = importlib.import_module(PKG + ".pairing_key_commitment")
verified = importlib.import_module(PKG + ".pairing_v3_verified")
codec = (importlib.import_module(PKG + ".pairing_v3_qa_channel")
         if ec is not None else None)
SID = "AQIDBAUGBwgJCgsMDQ4PEA"
TV = "123e4567-e89b-42d3-a456-426614174000"
OTHER = "123e4567-e89b-42d3-a456-426614174001"
TV_TO_HA = "32cced09c0407f709d358666bff467c580c102e4fea474"
HA_TO_TV = "c96732d43dab9211e1d9a068bdd766428e25a33a9ae4d7"


def pair(tv_id=TV, sid=SID):
    tkey = ec.derive_private_key(1, ec.SECP256R1())
    hkey = ec.derive_private_key(2, ec.SECP256R1())
    tv = keys.CommitmentSession("tv", sid, verified.public_der(tkey), bytes(range(32)))
    ha = keys.CommitmentSession("ha", sid, verified.public_der(hkey), bytes(range(32, 64)))
    tv.receive_peer_commit(ha.public_commit)
    ha.receive_peer_commit(tv.public_commit)
    tpub, tnonce = tv.reveal()
    hpub, hnonce = ha.reveal()
    tv.receive_peer_reveal(hpub, hnonce)
    ha.receive_peer_reveal(tpub, tnonce)
    return (
        codec.V3QaPingChannel(private_key=tkey, verified=tv, tv_uuid=tv_id, now=100),
        codec.V3QaPingChannel(private_key=hkey, verified=ha, tv_uuid=tv_id, now=100),
    )


@unittest.skipUnless(ec is not None, "v3 cryptography dependency not installed")
class ChannelTests(unittest.TestCase):
    def test_cross_language_fixed_ciphertexts_and_two_way(self):
        tv, ha = pair()
        seq, ct = tv.seal(b"qa-ping", now=101)
        self.assertEqual((seq, ct.hex()), (1, TV_TO_HA))
        self.assertEqual(ha.open(seq, ct, now=101), b"qa-ping")
        seq, ct = ha.seal(b"qa-pong", now=101)
        self.assertEqual((seq, ct.hex()), (1, HA_TO_TV))
        self.assertEqual(tv.open(seq, ct, now=101), b"qa-pong")
        self.assertNotEqual(TV_TO_HA, HA_TO_TV)

    def test_replay_denied_and_closed(self):
        tv, ha = pair()
        n, ct = tv.seal(b"qa-ping", now=101)
        self.assertEqual(ha.open(n, ct, now=101), b"qa-ping")
        with self.assertRaises(codec.V3QaChannelRejected):
            ha.open(n, ct, now=101)
        self.assertTrue(ha.closed)

    def test_tamper_wrong_direction_tv_and_session(self):
        tv, ha = pair()
        n, ct = tv.seal(b"qa-ping", now=101)
        modified = ct[:-1] + bytes((ct[-1] ^ 1,))
        with self.assertRaises(codec.V3QaChannelRejected):
            ha.open(n, modified, now=101)
        self.assertTrue(ha.closed)
        tv, ha = pair()
        n, ct = tv.seal(b"qa-ping", now=101)
        with self.assertRaises(codec.V3QaChannelRejected):
            tv.open(n, ct, now=101)
        other_tv, other_ha = pair(tv_id=OTHER)
        with self.assertRaises(codec.V3QaChannelRejected):
            other_ha.open(n, ct, now=101)
        other_session_tv, other_session_ha = pair(
            sid="AQIDBAUGBwgJCgsMDQ4PEQ")
        with self.assertRaises(codec.V3QaChannelRejected):
            other_session_ha.open(n, ct, now=101)

    def test_expiry_no_credentials_and_unverified_sessions(self):
        tv, ha = pair()
        with self.assertRaises(codec.V3QaChannelRejected):
            tv.seal(b"ha-access-token", now=101)
        with self.assertRaises(codec.V3QaChannelRejected):
            tv.seal(b"qa-ping", now=160)
        self.assertTrue(tv.closed)
        with self.assertRaises(codec.V3QaChannelRejected):
            tv.seal(b"qa-ping", now=101)
        key = ec.derive_private_key(1, ec.SECP256R1())
        unverified = keys.CommitmentSession("tv", SID, verified.public_der(key))
        with self.assertRaises(codec.V3QaChannelRejected):
            codec.V3QaPingChannel(
                private_key=key, verified=unverified, tv_uuid=TV, now=100)


if __name__ == "__main__":
    unittest.main()
