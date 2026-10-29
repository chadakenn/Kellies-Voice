"""End-to-end anonymous HA public-key exchange and TV proof validation tests.

Mock only HTTP transport; cryptography is real, including ECDH on both peers.
"""
import base64
import importlib
import json
import pathlib
import sys
import types
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PACKAGE = "kellies_pairing_exchange_tests"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package
exchange = importlib.import_module(PACKAGE + ".pairing_exchange")
crypto = importlib.import_module(PACKAGE + ".pairing_verification")
probe = importlib.import_module(PACKAGE + ".pairing_probe")

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
CANDIDATE = probe.candidate_from_discovery(
    "192.168.1.20", 45670,
    {"mode": "discovery-only", "protocol": "1", "session": SESSION},
)


class Body:
    def __init__(self, data):
        self.data = data

    async def read(self, length):
        chunk, self.data = self.data[:length], self.data[length:]
        return chunk


class Response:
    def __init__(self, doc, status=200):
        self.status = status
        self.content = Body(json.dumps(doc).encode())

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass


class Peer:
    def __init__(self):
        self.private = crypto.fresh_keypair()
        self.ha_key = None
        self.calls = []
        self.confirmed = False
        self.invalid_proof = False

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    def post(self, url, **kwargs):
        self.calls.append(("POST", url, kwargs))
        body = kwargs["json"]
        self.ha_key = base64.urlsafe_b64decode(body["ha_key"] + "==")
        return Response({
            "session": body["session"],
            "authorized": False,
            "tv_key": base64.urlsafe_b64encode(
                crypto.public_der(self.private)).rstrip(b"=").decode("ascii"),
        })

    def get(self, url, **kwargs):
        self.calls.append(("GET", url, kwargs))
        if not self.confirmed:
            return Response({"approved": False})
        proof = crypto.approved_proof(
            self.private, local_is_tv=True, session=SESSION,
            tv_key=crypto.public_der(self.private), ha_key=self.ha_key,
        )
        if self.invalid_proof:
            proof = "AAAAAAAAAAAAAAAAAAAAAA"
        return Response({"approved": True, "proof": proof})


class PairingExchangeTests(unittest.IsolatedAsyncioTestCase):
    async def test_temporary_keys_real_crypto_and_remote_ok_proof(self):
        peer = Peer()
        attempt = await exchange.begin_preview(CANDIDATE, _session_factory=lambda: peer)
        self.assertEqual(8, len(attempt.code))
        self.assertEqual(
            attempt.code,
            crypto.calculate(peer.private, local_is_tv=True, session=SESSION,
                             tv_key=crypto.public_der(peer.private), ha_key=peer.ha_key),
        )
        self.assertFalse(await exchange.confirm_tv_ok(
            attempt, _session_factory=lambda: peer))
        peer.confirmed = True
        self.assertTrue(await exchange.confirm_tv_ok(
            attempt, _session_factory=lambda: peer))
        self.assertEqual("POST", peer.calls[0][0])
        self.assertEqual("GET", peer.calls[1][0])
        self.assertFalse(peer.calls[0][2]["allow_redirects"])
        self.assertFalse(peer.calls[1][2]["allow_redirects"])
        self.assertEqual(
            {"session", "ha_key"}, set(peer.calls[0][2]["json"]))
        self.assertNotIn("token", json.dumps(peer.calls))
        self.assertNotIn("Authorization", json.dumps(peer.calls))

    async def test_forged_ok_proof_is_rejected(self):
        peer = Peer()
        attempt = await exchange.begin_preview(CANDIDATE, _session_factory=lambda: peer)
        peer.confirmed = True
        peer.invalid_proof = True
        with self.assertRaises(ValueError):
            await exchange.confirm_tv_ok(attempt, _session_factory=lambda: peer)

    async def test_only_private_numeric_ip_is_contacted(self):
        bad = probe.PairingCandidate("attacker.example", 45670, SESSION)
        peer = Peer()
        with self.assertRaises(ValueError):
            await exchange.begin_preview(bad, _session_factory=lambda: peer)
        self.assertEqual([], peer.calls)


if __name__ == "__main__":
    unittest.main()
