"""Offline security/interop checks for the standalone pairing preview client.

No multicast, HA authentication, OAuth, or real TV required in this test.
"""
from __future__ import annotations

import base64
from contextlib import redirect_stdout
from io import StringIO
import importlib.util
from pathlib import Path
import struct
from unittest import mock
import unittest
from uuid import UUID

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "pairing_preview_cli.py"
spec = importlib.util.spec_from_file_location("pairing_preview_cli", SCRIPT)
cli = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = cli
spec.loader.exec_module(cli)

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
TV_UUID = "123e4567-e89b-42d3-a456-426614174000"
OTHER_UUID = "223e4567-e89b-42d3-a456-426614174000"
DATA = {b"protocol": b"1", b"mode": b"discovery-only", b"session": SESSION.encode()}


def public(private):
    return private.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)


class PreviewCliTests(unittest.TestCase):
    def test_real_zeroconf_byte_txt_keys_and_str_keys(self):
        expected = cli.Candidate("192.168.1.25", 43456, SESSION)
        self.assertEqual(expected, cli.candidate("192.168.1.25", 43456, DATA))
        self.assertEqual(expected, cli.candidate(
            "192.168.1.25", 43456,
            {"protocol": "1", "mode": "discovery-only", "session": SESSION}))

    def test_reject_untrusted_address_port_metadata(self):
        for ip in ("8.8.8.8", "127.0.0.1", "::1", "evil.example", "172.32.0.1"):
            with self.subTest(ip=ip), self.assertRaises(ValueError):
                cli.candidate(ip, 43456, DATA)
        for port in (0, 1023, 65536, "43456", True):
            with self.subTest(port=port), self.assertRaises(ValueError):
                cli.candidate("192.168.1.25", port, DATA)
        for metadata in ({}, {b"protocol": b"2", b"mode": b"discovery-only",
                                  b"session": SESSION.encode()},
                         {b"protocol": b"1", b"mode": b"authorize",
                          b"session": SESSION.encode()}):
            with self.subTest(metadata=metadata), self.assertRaises(ValueError):
                cli.candidate("192.168.1.25", 43456, metadata)

    def test_cryptographic_proof_matches_independent_tv_direction(self):
        ha = ec.derive_private_key(2, ec.SECP256R1())
        tv = ec.derive_private_key(1, ec.SECP256R1())
        ha_der, tv_der = public(ha), public(tv)
        code = cli.comparison_code(ha, SESSION, tv_der, ha_der)
        self.assertEqual(8, len(code))
        self.assertTrue(code.isdecimal())

        # Derive the same transcript/approved-UUID proof from the TV private
        # scalar independently, not by calling the HA-role verification helper.
        import hashlib
        import hmac
        def part(raw):
            return struct.pack(">H", len(raw)) + raw
        def tv_proof(uuid):
            transcript = (b"KVPair/v1/transcript"
                          + part(base64.urlsafe_b64decode(SESSION + "=="))
                          + part(tv_der) + part(ha_der)
                          + part(uuid.encode("ascii")))
            prk = hmac.new(
                hashlib.sha256(transcript).digest(),
                tv.exchange(ec.ECDH(), ha.public_key()),
                hashlib.sha256,
            ).digest()
            return cli._b64(hmac.new(
                prk, b"KelliesVoice/v2/tv-approved-uuid\x01",
                hashlib.sha256).digest()[:16])

        self.assertEqual(tv_proof(TV_UUID),
                         cli.approved_proof(ha, SESSION, tv_der, ha_der, TV_UUID))
        self.assertNotEqual(tv_proof(OTHER_UUID), tv_proof(TV_UUID))

    @mock.patch("builtins.input", return_value="")
    def test_full_mock_http_exchange_no_credentials(self, _input):
        peer = cli.Candidate("192.168.1.25", 43456, SESSION)
        tv = ec.generate_private_key(ec.SECP256R1())
        calls = []
        def fake_http(info, path, body=None):
            self.assertEqual(peer, info)
            calls.append((path, body))
            if path == "/v1/pair/capabilities":
                return {"protocol": 1, "mode": "discovery-only",
                        "authorized": False, "session": SESSION}
            if path == "/v1/pair/offer":
                self.assertEqual({"session", "ha_key"}, set(body))
                fake_http.ha_der = cli._unb64(body["ha_key"])
                return {"session": SESSION, "authorized": False,
                        "tv_key": cli._b64(public(tv))}
            if path == "/v1/pair/status?session=" + SESSION:
                # Verification is also tested with independent crypto above.
                from cryptography.hazmat.primitives.serialization import load_der_public_key
                import hashlib
                import hmac
                shared = tv.exchange(ec.ECDH(), load_der_public_key(fake_http.ha_der))
                items = (
                    cli._sid(SESSION), public(tv), fake_http.ha_der,
                    TV_UUID.encode("ascii"))
                transcript = b"KVPair/v1/transcript" + b"".join(cli._part(x) for x in items)
                prk = hmac.new(hashlib.sha256(transcript).digest(),
                               shared, hashlib.sha256).digest()
                proof = cli._b64(hmac.new(
                    prk, b"KelliesVoice/v2/tv-approved-uuid\x01",
                    hashlib.sha256).digest()[:16])
                return {"approved": True, "tv_uuid": TV_UUID, "proof": proof}
            self.fail("Unexpected pairing path: " + path)
        output = StringIO()
        with mock.patch.object(cli, "http_json", side_effect=fake_http), redirect_stdout(output):
            cli.run(peer, 40)
        self.assertIn("PASS: Physical TV remote OK", output.getvalue())
        self.assertEqual(3, len(calls))
        self.assertNotIn("access_token", str(calls))
        self.assertNotIn("refresh_token", str(calls))

    def test_reject_uncanonical_sessions_and_public_keys(self):
        for value in ("short", "A" * 22, SESSION + "=", ""):
            with self.subTest(value=value), self.assertRaises(ValueError):
                cli._sid(value)
        with self.assertRaises(ValueError):
            cli._p256_public(b"not a P-256 key")
        with self.assertRaises(ValueError):
            cli._unb64("AA=")


if __name__ == "__main__":
    unittest.main()
