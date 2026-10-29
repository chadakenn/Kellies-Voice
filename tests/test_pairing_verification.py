"""Cross-language P-256 ECDH/PKCE stage interoperability for issue #193.

The deterministic keypair and code are public test fixtures, never credentials.
Python HA test environments without optional cryptography are allowed to
skip the live-curve cases; installed Home Assistant supplies cryptography.
"""

import base64
import importlib.util
import pathlib
import os
import sys
import unittest

PATH = pathlib.Path(__file__).resolve().parents[1] / "custom_components/kellies_voice/pairing_verification.py"
spec = importlib.util.spec_from_file_location("pairing_verification_under_test", PATH)
assert spec is not None and spec.loader is not None
verify = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = verify
spec.loader.exec_module(verify)

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
TV = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q"
HA = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q"

try:
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    ec = None

# CI must exercise the real cross-language key agreement, not pass on skips.
if os.getenv('PAIRING_CRYPTO_REQUIRED') == '1' and ec is None:
    raise RuntimeError('Pairing cryptography tests require the cryptography package')


@unittest.skipUnless(ec is not None, "cryptography dependency unavailable")
class PairingVisualCodeTests(unittest.TestCase):
    @staticmethod
    def public(value: str) -> bytes:
        return base64.urlsafe_b64decode(value + "==")

    def keys(self):
        return ec.derive_private_key(1, ec.SECP256R1()), ec.derive_private_key(2, ec.SECP256R1())

    def test_java_python_reference_vector_and_role_agreement(self):
        tv, ha = self.keys()
        self.assertEqual(self.public(TV), verify.public_der(tv))
        self.assertEqual(self.public(HA), verify.public_der(ha))
        code_tv = verify.calculate(tv, local_is_tv=True, session=SESSION,
                                   tv_key=self.public(TV), ha_key=self.public(HA))
        code_ha = verify.calculate(ha, local_is_tv=False, session=SESSION,
                                   tv_key=self.public(TV), ha_key=self.public(HA))
        self.assertEqual("34821918", code_tv)
        self.assertEqual(code_tv, code_ha)
        tv_proof = verify.approved_proof(
            tv, local_is_tv=True, session=SESSION,
            tv_key=self.public(TV), ha_key=self.public(HA))
        self.assertEqual("zWMp2ha4rIAnsdSpGP8sow", tv_proof)
        self.assertEqual(tv_proof, verify.approved_proof(
            ha, local_is_tv=False, session=SESSION,
            tv_key=self.public(TV), ha_key=self.public(HA)))

    def test_new_peer_new_session_change_code(self):
        tv, _ha = self.keys()
        old = verify.calculate(tv, local_is_tv=True, session=SESSION,
                               tv_key=self.public(TV), ha_key=self.public(HA))
        other_ha = verify.fresh_keypair()
        self.assertNotEqual(old, verify.calculate(
            tv, local_is_tv=True, session=SESSION,
            tv_key=self.public(TV), ha_key=verify.public_der(other_ha)))
        self.assertNotEqual(old, verify.calculate(
            tv, local_is_tv=True, session="AQIDBAUGBwgJCgsMDQ4PEQ",
            tv_key=self.public(TV), ha_key=self.public(HA)))

    def test_wrong_role_duplicate_peer_or_changed_sas(self):
        tv, _ha = self.keys()
        for kwargs in (
            dict(local_is_tv=False, tv_key=self.public(TV), ha_key=self.public(HA)),
            dict(local_is_tv=True, tv_key=self.public(TV), ha_key=self.public(TV)),
            dict(local_is_tv=True, tv_key=self.public(HA), ha_key=self.public(TV)),
        ):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                verify.calculate(tv, session=SESSION, **kwargs)

    def test_rejects_bad_sessions_and_wrong_curve(self):
        tv, _ha = self.keys()
        for session in ("", "short", SESSION + "A", SESSION[:-1] + "B", "#" * 22):
            with self.subTest(session=session), self.assertRaises(ValueError):
                verify.calculate(tv, local_is_tv=True, session=session,
                                 tv_key=self.public(TV), ha_key=self.public(HA))
        other = ec.generate_private_key(ec.SECP384R1())
        with self.assertRaises(ValueError):
            verify.calculate(tv, local_is_tv=True, session=SESSION,
                             tv_key=self.public(TV), ha_key=verify.public_der(other))

    def test_fresh_ephemeral_keys_are_different(self):
        one, two = verify.fresh_keypair(), verify.fresh_keypair()
        self.assertNotEqual(verify.public_der(one), verify.public_der(two))


if __name__ == "__main__":
    unittest.main()
