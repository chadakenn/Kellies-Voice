"""No-secrets tests for the future #193 Home Assistant pairing discovery flow."""

import asyncio
import importlib.util
import json
from pathlib import Path
import sys
import unittest

PATH = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice/pairing_probe.py"
spec = importlib.util.spec_from_file_location("kellie_pairing_probe_under_test", PATH)
assert spec is not None and spec.loader is not None
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

NONCE = "AbCdEf0123456789abCD_-"
PROPS = {"mode": b"discovery-only", "protocol": b"1", "session": NONCE.encode()}


class DiscoveryTests(unittest.TestCase):
    def test_private_ip_and_exact_read_only_path(self):
        for address in ("192.168.1.123", "192.168.40.12", "10.2.3.4", "172.20.11.2"):
            candidate = module.candidate_from_discovery(address, 45679, PROPS)
            self.assertEqual(
                f"http://{address}:45679/v1/pair/capabilities", candidate.url
            )

    def test_rejects_public_loopback_link_local_and_nonliteral_hosts(self):
        for address in (
            "127.0.0.1", "0.0.0.0", "8.8.8.8", "169.254.2.2",
            "100.64.0.2", "fe80::1", "homeassistant.local",
            "192.168.0.1@evil.example", "192.168.1.1:8080",
        ):
            with self.subTest(address=address), self.assertRaises(ValueError):
                module.candidate_from_discovery(address, 45123, PROPS)

    def test_rejects_invalid_port_or_metadata(self):
        for port in (0, 80, 1023, 65536, True, "5555"):
            with self.subTest(port=port), self.assertRaises(ValueError):
                module.candidate_from_discovery("192.168.1.7", port, PROPS)
        for change in (
            {"mode": "ready"},
            {"protocol": "2"},
            {"session": "short"},
            {"session": "AbCdEf0123456789abCD_-?"},
            {"session": "AbCdEf0123456789abCD_+"},
        ):
            props = {**PROPS, **change}
            with self.subTest(change=change), self.assertRaises(ValueError):
                module.candidate_from_discovery("192.168.1.7", 45123, props)


class FakeBody:
    def __init__(self, data):
        self.data = data
    async def read(self, limit):
        chunk, self.data = self.data[:limit], self.data[limit:]
        return chunk


class FakeResponse:
    def __init__(self, data, status=200):
        self.status = status
        self.content = FakeBody(data)
    async def __aenter__(self):
        return self
    async def __aexit__(self, *_args):
        return None


class FakeSession:
    def __init__(self, payload, status=200):
        self.payload = payload
        self.status = status
        self.urls = []
        self.kwargs = []
    async def __aenter__(self):
        return self
    async def __aexit__(self, *_args):
        return None
    def get(self, url, **kwargs):
        self.urls.append(url)
        self.kwargs.append(kwargs)
        return FakeResponse(self.payload, self.status)


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_matches_public_session_and_no_redirect_or_auth_header(self):
        candidate = module.candidate_from_discovery("192.168.1.7", 45123, PROPS)
        data = json.dumps({"protocol": 1, "mode": "discovery-only",
                           "authorized": False, "session": NONCE}).encode()
        client = FakeSession(data)
        self.assertTrue(await module.probe_candidate(
            candidate, _session_factory=lambda: client))
        self.assertEqual([candidate.url], client.urls)
        self.assertEqual([{"allow_redirects": False}], client.kwargs)

    async def test_refuses_wrong_session_auth_claim_redirect_big_body_or_junk(self):
        candidate = module.candidate_from_discovery("192.168.1.7", 45123, PROPS)
        base = {"protocol": 1, "mode": "discovery-only",
                "authorized": False, "session": NONCE}
        for doc in (
            {**base, "session": "another-session"},
            {**base, "authorized": True},
            {**base, "token": "should-never-be-present"},
            {**base, "protocol": 2},
        ):
            with self.subTest(doc=doc):
                self.assertFalse(await module.probe_candidate(
                    candidate, _session_factory=lambda: FakeSession(json.dumps(doc).encode())))
        for status in (301, 302, 401, 500):
            self.assertFalse(await module.probe_candidate(
                candidate, _session_factory=lambda: FakeSession(json.dumps(base).encode(), status)))
        self.assertFalse(await module.probe_candidate(
            candidate, _session_factory=lambda: FakeSession(b"x" * 513)))
        self.assertFalse(await module.probe_candidate(
            candidate, _session_factory=lambda: FakeSession(b"not-json")))
        # A valid leading object must not hide extra body bytes past the bound.
        self.assertFalse(await module.probe_candidate(
            candidate, _session_factory=lambda: FakeSession(
                json.dumps(base).encode() + b" " * 520)))

    async def test_never_requests_an_unvalidated_candidate(self):
        malicious = module.PairingCandidate("attacker.example", 45123, NONCE)
        client = FakeSession(b"{}")
        with self.assertRaises(ValueError):
            await module.probe_candidate(malicious, _session_factory=lambda: client)
        self.assertEqual([], client.urls)


if __name__ == "__main__":
    unittest.main()
