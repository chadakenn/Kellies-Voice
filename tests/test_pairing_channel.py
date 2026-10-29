"""#193: real cryptography and cross-language fixed-vector tests.

Test payload is NONSECRET development text. These helpers are NOT wired to
Home Assistant authentication or any TV credential store.
"""
import base64
import importlib
from pathlib import Path
import sys
import types
import unittest

from cryptography.hazmat.primitives.asymmetric import ec

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PACKAGE = "kellies_sealed_channel_tests"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package
mod = importlib.import_module(PACKAGE + ".pairing_channel")
crypto = importlib.import_module(PACKAGE + ".pairing_verification")

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
PLAIN = b"PAIRING PREVIEW ONLY"
WIRE = bytes.fromhex(
    "01000000000000000000000102030405060708090a0b"
    "f29a8b99db35f1a23b424b1c0cf5a5a3ecbd7cf98b39cbbc68e2ae4b14808ff445d0c78a"
)


def peers(session=SESSION):
    tv = ec.derive_private_key(1, ec.SECP256R1())
    ha = ec.derive_private_key(2, ec.SECP256R1())
    tv_pub, ha_pub = crypto.public_der(tv), crypto.public_der(ha)
    return (
        mod.PairingSealedChannel(
            tv, local_is_tv=True, session=session, tv_key=tv_pub, ha_key=ha_pub),
        mod.PairingSealedChannel(
            ha, local_is_tv=False, session=session, tv_key=tv_pub, ha_key=ha_pub),
    )


class SealedChannelTests(unittest.TestCase):
    def test_ha_to_tv_matches_java_reference_vector(self):
        tv, ha = peers()
        packet = ha.seal_with_nonce(PLAIN, bytes(range(12)))
        self.assertEqual(WIRE, packet)
        self.assertEqual(PLAIN, tv.open(packet))
        with self.assertRaises(ValueError):
            tv.open(packet)

    def test_tv_to_ha_different_direction_and_in_order(self):
        tv, ha = peers()
        packet = tv.seal_with_nonce(b"TV CONFIRMED", bytes(range(12)))
        self.assertEqual(1, packet[1])
        self.assertEqual(b"TV CONFIRMED", ha.open(packet))
        next_packet = tv.seal(b"SECOND")
        self.assertEqual(b"SECOND", ha.open(next_packet))

    def test_wrong_role_tamper_reflection_and_wrong_session_fail(self):
        tv, ha = peers()
        packet = ha.seal_with_nonce(PLAIN, bytes(range(12)))
        with self.assertRaises(ValueError):
            ha.open(packet)  # reflection
        for index in (0, 1, 9, 11, 23, len(packet) - 1):
            altered = bytearray(packet)
            altered[index] ^= 1
            with self.subTest(index=index), self.assertRaises(ValueError):
                tv.open(bytes(altered))
        other_tv, _other_ha = peers("AQIDBAUGBwgJCgsMDQ4PEQ")
        with self.assertRaises(ValueError):
            other_tv.open(packet)
        self.assertEqual(PLAIN, tv.open(packet))  # failed packets did not advance counter

    def test_reordering_and_closed_sessions_fail(self):
        tv, ha = peers()
        first = ha.seal(b"first")
        second = ha.seal(b"second")
        with self.assertRaises(ValueError):
            tv.open(second)
        self.assertEqual(b"first", tv.open(first))
        self.assertEqual(b"second", tv.open(second))
        tv.close()
        ha.close()
        with self.assertRaises(ValueError):
            tv.open(first)
        with self.assertRaises(ValueError):
            ha.seal(b"another")

    def test_oversized_plaintext_or_packet_rejected(self):
        tv, ha = peers()
        for bad in (b"", b"x" * 2049):
            with self.assertRaises(ValueError):
                ha.seal(bad)
        for bad in (b"", b"x" * 2200):
            with self.assertRaises(ValueError):
                tv.open(bad)


if __name__ == "__main__":
    unittest.main()
