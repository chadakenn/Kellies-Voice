"""Python v3 HA network driver against an independently simulated TV peer.

Tests the actual async request/response message contract and P-256 key
agreement, not a mocked constant eight-digit success. No real HA instance,
LAN socket, OAuth code, access or refresh token is accessed.
"""
from __future__ import annotations

import importlib
from pathlib import Path
import sys
import types
import unittest

try:
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    ec = None

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
PKG = "kv_v3_wire_qa"
mod = types.ModuleType(PKG)
mod.__path__ = [str(ROOT)]
sys.modules[PKG] = mod

wire = importlib.import_module(PKG + ".pairing_v3_wire")
commits = importlib.import_module(PKG + ".pairing_key_commitment")
verified = importlib.import_module(PKG + ".pairing_v3_verified")

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
OTHER_SESSION = "AQIDBAUGBwgJCgsMDQ4PEQ"
TV_UUID = "123e4567-e89b-42d3-a456-426614174000"
TV_NONCE = bytes(range(32))
HA_NONCE = bytes(range(32, 64))


class SimulatedTvWire:
    """Same four TV wire states expected from actual Android implementation."""

    def __init__(self):
        self.tv_private = ec.derive_private_key(1, ec.SECP256R1())
        self.tv = commits.CommitmentSession(
            "tv", SESSION, verified.public_der(self.tv_private), TV_NONCE)
        self.events = []
        self.state = "fresh"
        self.approved = False
        self.mode = None
        self.code = None
        self.held = None

    @staticmethod
    def require(doc, keys, state_session=SESSION):
        if type(doc) is not dict or set(doc) != set(keys):
            raise ValueError("Malformed request")
        if type(doc["protocol"]) is not int or doc["protocol"] != 3:
            raise ValueError("V3 only, no protocol downgrade")
        if doc["session"] != state_session:
            raise ValueError("Wrong session")

    async def post(self, path, doc):
        self.events.append(path)
        if path == "/v3/pair/commit" and self.state == "fresh":
            self.require(doc, ("protocol", "session", "ha_commit"))
            self.tv.receive_peer_commit(wire._decode(doc["ha_commit"], 32))
            self.state = "committed"
            response = {
                "protocol": 3,
                "session": SESSION,
                "tv_commit": wire._encode(self.tv.public_commit),
            }
            if self.mode == "bad_commit":
                response["tv_commit"] = wire._encode(bytes(32))
            if self.mode == "wrong_version":
                response["protocol"] = 2
            if self.mode == "extra_field":
                response["secret"] = "not-allowed"
            return response
        if path == "/v3/pair/reveal-ha" and self.state == "committed":
            self.require(doc, ("protocol", "session", "ha_key", "ha_nonce"))
            self.held = self.tv.reveal()
            try:
                self.tv.receive_peer_reveal(
                    wire._decode(doc["ha_key"]), wire._decode(doc["ha_nonce"], 32))
            except Exception:
                self.tv.close()
                raise
            self.code = verified.comparison_code(self.tv_private, self.tv)
            self.state = "ha-verified"
            return {
                "protocol": 3, "session": SESSION,
                "ha_verified": False if self.mode == "bad_ack" else True,
            }
        raise ValueError("Out-of-order v3 request")

    async def get(self, path, data):
        self.events.append(path)
        self.require(data, ("protocol", "session"))
        if path == "/v3/pair/reveal-tv" and self.state == "ha-verified":
            self.state = "revealed"
            response = {
                "protocol": 3, "session": SESSION,
                "tv_key": wire._encode(self.held[0]),
                "tv_nonce": wire._encode(self.held[1]),
            }
            if self.mode == "bad_reveal":
                response["tv_nonce"] = wire._encode(bytes(32))
            return response
        if path == "/v3/pair/status" and self.state == "revealed":
            if not self.approved:
                return {"protocol": 3, "session": SESSION, "approved": False}
            result = {
                "protocol": 3, "session": SESSION, "approved": True,
                "tv_uuid": TV_UUID, "proof": verified.physical_tv_ok_proof(
                    self.tv_private, self.tv, tv_uuid=TV_UUID),
            }
            if self.mode == "false_proof":
                result["proof"] = wire._encode(bytes(16))
            if self.mode == "wrong_uuid":
                result["tv_uuid"] = "223e4567-e89b-42d3-a456-426614174000"
            return result
        raise ValueError("Out-of-order v3 read")


@unittest.skipUnless(ec is not None, "Optional cryptography test dependency")
class HaV3LanWireTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tv = SimulatedTvWire()
        self.ha = wire.HaV3WireAttempt(
            SESSION,
            private=ec.derive_private_key(2, ec.SECP256R1()),
            nonce=HA_NONCE,
        )

    async def test_real_p256_full_commits_both_reveals_code_and_remote_ok(self):
        code = await self.ha.start(self.tv)
        self.assertEqual("83428994", code)
        self.assertEqual(code, self.tv.code)
        self.assertEqual([
            "/v3/pair/commit",
            "/v3/pair/reveal-ha",
            "/v3/pair/reveal-tv",
        ], self.tv.events)
        self.assertIsNone(await self.ha.confirmed_by_tv_remote(self.tv))
        self.tv.approved = True  # Simulated actual physical remote event.
        approved = await self.ha.confirmed_by_tv_remote(self.tv)
        self.assertEqual(TV_UUID, approved.tv_uuid)
        self.assertEqual(SESSION, approved.session)
        self.assertIsNone(self.ha.code)
        with self.assertRaises(ValueError):
            await self.ha.confirmed_by_tv_remote(self.tv)

    async def test_protocol_downgrade_wrong_fields_and_key_tampering(self):
        for mode in ("wrong_version", "extra_field", "bad_commit",
                     "bad_ack", "bad_reveal"):
            with self.subTest(mode=mode):
                tv = SimulatedTvWire()
                tv.mode = mode
                ha = wire.HaV3WireAttempt(
                    SESSION, private=ec.derive_private_key(2, ec.SECP256R1()),
                    nonce=HA_NONCE)
                with self.assertRaises(ValueError):
                    await ha.start(tv)
                self.assertIsNone(ha.code)
                with self.assertRaises(ValueError):
                    await ha.start(tv)

    async def test_rejects_wrong_tv_uuid_and_forged_remote_proof(self):
        for mode in ("wrong_uuid", "false_proof"):
            with self.subTest(mode=mode):
                tv = SimulatedTvWire()
                tv.mode = mode
                ha = wire.HaV3WireAttempt(
                    SESSION, private=ec.derive_private_key(2, ec.SECP256R1()),
                    nonce=HA_NONCE)
                await ha.start(tv)
                tv.approved = True
                with self.assertRaises(ValueError):
                    await ha.confirmed_by_tv_remote(tv)
                self.assertIsNone(ha.code)

    async def test_wrong_session_and_duplicate_start_fail_closed(self):
        tv = SimulatedTvWire()
        ha = wire.HaV3WireAttempt(
            OTHER_SESSION, private=ec.derive_private_key(2, ec.SECP256R1()),
            nonce=HA_NONCE)
        with self.assertRaises(ValueError):
            await ha.start(tv)
        self.assertIsNone(ha.code)
        with self.assertRaises(ValueError):
            await ha.start(tv)

    def test_strict_base64url_no_padded_values_or_junk(self):
        for value in ("abcdef=", "", "abc+", "abc/", "!!!", 123, None):
            with self.subTest(value=value), self.assertRaises(ValueError):
                wire._decode(value, 32)

    def test_only_literal_private_lan_ipv4_endpoint_accepted(self):
        for bad in (
            "example.com", "8.8.8.8", "127.0.0.1",
            "https://192.168.1.100", "::1", "224.0.0.10",
        ):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                wire.AiohttpV3LanTransport(bad, 42000)
        client = wire.AiohttpV3LanTransport("192.168.1.100", 42000)
        self.assertIn("192.168.1.100:42000", client._base)
        for invalid in (0, 65536, "8123", True):
            with self.subTest(port=invalid), self.assertRaises(ValueError):
                wire.AiohttpV3LanTransport("192.168.1.100", invalid)


if __name__ == "__main__":
    unittest.main()
