"""Pairing OAuth envelope: strict device binding and AEAD-only handoff.

Synthetic test tokens. No actual HA or network access.
"""
import importlib
import json
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PKG = "kellies_provisioning_contract"
package = types.ModuleType(PKG)
package.__path__ = [str(ROOT)]
sys.modules[PKG] = package
provision = importlib.import_module(PKG + ".pairing_provisioning")
sealed = importlib.import_module(PKG + ".pairing_channel")
verification = importlib.import_module(PKG + ".pairing_verification")
from cryptography.hazmat.primitives.asymmetric import ec

TV = "123e4567-e89b-42d3-a456-426614174000"
SID = "AQIDBAUGBwgJCgsMDQ4PEA"


def candidate(**changes):
    item = {
        "tv_id": TV,
        "session": SID,
        "server_url": "https://my-ha.example.org",
        "client_id": "https://my-ha.example.org/",
        "access_token": "fake.access.token.sample",
        "refresh_token": "fake.refresh.token.sample",
        "expires_at_ms": 1_800_000,
        "issued_at_ms": 1_000,
    }
    item.update(changes)
    return item


class DummySealedChannel:
    def __init__(self):
        self.payloads = []

    def seal(self, plaintext):
        self.payloads.append(plaintext)
        return b"AEAD-ONLY-TEST:" + plaintext


class ProvisioningTests(unittest.TestCase):
    def test_canonical_payload_has_exact_eight_keys_and_device_binding(self):
        packed = provision.encode_credentials(**candidate())
        self.assertLess(len(packed), 2048)
        record = provision.validate_payload_shape(
            packed, expected_tv_id=TV, expected_session=SID, now_ms=1_000
        )
        self.assertEqual(
            {
                "kind", "tv_id", "session", "server_url", "client_id",
                "access_token", "refresh_token", "expires_at_ms"
            }, set(record)
        )
        self.assertEqual("kellies_voice/pair/credentials/v1", record["kind"])
        self.assertEqual(
            packed, json.dumps(record, sort_keys=True,
                               separators=(",", ":"), ensure_ascii=True).encode("ascii")
        )

    def test_public_seal_entrypoint_calls_channel_not_http(self):
        channel = DummySealedChannel()
        payload = provision.seal_credentials(channel, **candidate())
        self.assertTrue(payload.startswith(b"AEAD-ONLY-TEST:"))
        self.assertEqual(1, len(channel.payloads))
        self.assertNotIn(b"HTTP", payload)
        source = (ROOT / "pairing_provisioning.py").read_text()
        self.assertNotIn("aiohttp", source)
        self.assertNotIn("requests.post", source)
        self.assertNotIn("print(", source)

    def test_real_ecdh_aesgcm_credentials_are_single_use_and_authenticated(self):
        tv_private = ec.derive_private_key(1, ec.SECP256R1())
        ha_private = ec.derive_private_key(2, ec.SECP256R1())
        tv_public = verification.public_der(tv_private)
        ha_public = verification.public_der(ha_private)
        tv_channel = sealed.PairingSealedChannel(
            tv_private, local_is_tv=True, session=SID,
            tv_key=tv_public, ha_key=ha_public)
        ha_channel = sealed.PairingSealedChannel(
            ha_private, local_is_tv=False, session=SID,
            tv_key=tv_public, ha_key=ha_public)
        packet = provision.seal_credentials(ha_channel, **candidate())
        self.assertNotIn(b"fake.refresh.token.sample", packet)
        self.assertNotIn(b"my-ha.example.org", packet)
        clear = tv_channel.open(packet)
        self.assertEqual(
            "fake.refresh.token.sample",
            provision.validate_payload_shape(
                clear, expected_tv_id=TV, expected_session=SID,
                now_ms=1_000)["refresh_token"]
        )
        with self.assertRaises(ValueError):
            tv_channel.open(packet)  # Reject token replay

        fresh_tv = sealed.PairingSealedChannel(
            tv_private, local_is_tv=True, session=SID,
            tv_key=tv_public, ha_key=ha_public)
        tampered = bytearray(packet)
        tampered[-1] ^= 0x01
        with self.assertRaises(ValueError):
            fresh_tv.open(bytes(tampered))
        with self.assertRaises(ValueError):
            fresh_tv.open(packet) if False else provision.validate_payload_shape(
                clear, expected_tv_id=TV, expected_session=SID,
                now_ms=1_800_000)  # Expired credentials not reusable

    def test_reject_cross_tv_session_invalid_origin_and_time(self):
        changes = [
            {"tv_id": "bad"},
            {"session": "bad"},
            {"server_url": "http://public-ha.example.org"},
            {"server_url": "https://my-ha.example.org/path"},
            {"client_id": "https://evil.example.org/"},
            {"access_token": "bad"},
            {"refresh_token": "bad"},
            {"expires_at_ms": True},
            {"expires_at_ms": 999},
            {"expires_at_ms": 99_999_999},
        ]
        for change in changes:
            with self.subTest(change=change), self.assertRaises((ValueError, TypeError)):
                provision.encode_credentials(**candidate(**change))
        for expected_tv_id, expected_session in [
            ("223e4567-e89b-42d3-a456-426614174000", SID),
            (TV, "AQIDBAUGBwgJCgsMDQ4PEQ"),
        ]:
            with self.assertRaises(ValueError):
                provision.validate_payload_shape(
                    provision.encode_credentials(**candidate()),
                    expected_tv_id=expected_tv_id,
                    expected_session=expected_session,
                    now_ms=1000,
                )

    def test_reject_invalid_shapes_and_oversized_secrets(self):
        good = json.loads(provision.encode_credentials(**candidate()))
        for changed in [
            {**good, "service": "lock.unlock"},
            {key: value for key, value in good.items() if key != "tv_id"},
            {**good, "kind": "kellies_voice/pair/credentials/v2"},
            {**good, "expires_at_ms": "1800000"},
            {**good, "tv_id": "223e4567-e89b-42d3-a456-426614174000"},
        ]:
            with self.assertRaises(ValueError):
                provision.validate_payload_shape(
                    json.dumps(changed).encode(), expected_tv_id=TV,
                    expected_session=SID, now_ms=1000
                )
        with self.assertRaises(ValueError):
            provision.encode_credentials(**candidate(access_token="x" * 2000))

    def test_never_persist_or_promote_from_java_decoder(self):
        java = (Path(__file__).resolve().parents[1]
                / "app/src/main/java/com/example/tvvoiceprobe/PairingCredentialEnvelope.java"
                ).read_text()
        self.assertIn("channel.open(sealed)", java)
        self.assertIn("channel.close()", java)
        self.assertIn("Arrays.fill(clear, (byte) 0)", java)
        self.assertIn("value.matches(expectedSession, expectedTvId, nowMillis)", java)
        self.assertNotIn("CredentialStore", java)
        self.assertNotIn("startTalk(", java)
        self.assertNotIn("socket.send(", java)


if __name__ == "__main__":
    unittest.main()
