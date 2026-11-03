"""Regression for HACS review #13067: canonical v3 transport and HA executors."""
from __future__ import annotations

import ast
import importlib
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
V3 = ROOT / "v3"
PKG = "kv_hacs_v3_review"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(V3)]
sys.modules[PKG] = pkg
wire = importlib.import_module(PKG + ".pairing_v3_wire")


class ProductionPairingReviewTests(unittest.TestCase):
    def test_config_flow_uses_the_only_approved_v3_transport(self):
        source = (ROOT / "config_flow.py").read_text()
        tree = ast.parse(source)
        imports = [node for node in tree.body if isinstance(node, ast.ImportFrom)]
        self.assertTrue(any(
            node.module == "v3.pairing_v3_wire"
            and node.level == 1
            and any(alias.name == "AiohttpV3LanTransport" for alias in node.names)
            for node in imports
        ))
        self.assertIn("from .v3.pairing_key_commitment import _session_bytes", source)
        self.assertNotIn("from .pairing_v3_wire import", source)
        for old in ("pairing_v3_wire.py", "pairing_v3_verified.py",
                    "pairing_key_commitment.py"):
            self.assertFalse((ROOT / old).exists(), old)

    def test_every_production_ecdh_boundary_offloads_crypto(self):
        flow = (V3 / "flow.py").read_text()
        protocol = (V3 / "pairing_v3_wire.py").read_text()
        receipt = (V3 / "owner_receipt.py").read_text()
        executor = (V3 / "executor.py").read_text()
        self.assertIn("attempt = await async_crypto_job(", flow)
        self.assertIn("HaV3WireAttempt, self._v3_session,", flow)
        self.assertIn("attempt.start(self._v3_transport, hass=self.hass)", flow)
        self.assertIn("self._v3_transport, hass=self.hass)", flow)
        self.assertIn("hass, comparison_code, self._private, self._barrier", protocol)
        self.assertIn("hass, partial(physical_tv_ok_proof,", protocol)
        self.assertIn("hass, partial(V3QaPingChannel,", protocol)
        self.assertIn("hass, partial(HaTokenHandoff,", protocol)
        self.assertIn("hass, _verified_key, private_key, verified", receipt)
        self.assertIn("await executor(func, *args)", executor)
        self.assertIn("async_add_executor_job", executor)
        for path in (ROOT / "config_flow.py", V3 / "flow.py",
                     V3 / "pairing_v3_wire.py", V3 / "owner_receipt.py",
                     V3 / "executor.py"):
            with self.subTest(path=path.name):
                ast.parse(path.read_text())


class ProductionTransportRouteTests(unittest.IsolatedAsyncioTestCase):
    async def test_owner_receipt_and_renewable_login_posts_reach_tv(self):
        class Response:
            status = 200
            def __init__(self):
                self.content = self
            async def read(self, n):
                return b'{"protocol":3}'
            async def __aenter__(self):
                return self
            async def __aexit__(self, *args):
                return False

        requested = []

        class Session:
            def __init__(self, **kw):
                self.assertion = kw["trust_env"] is False
            async def __aenter__(self):
                return self
            async def __aexit__(self, *args):
                return False
            def request(self, method, url, **kw):
                self_kwargs = kw
                assert kw["allow_redirects"] is False
                requested.append((method, url))
                return Response()

        fake = types.SimpleNamespace(
            ClientTimeout=lambda **kw: kw,
            DummyCookieJar=lambda: object(),
            ClientSession=Session,
        )
        transport = wire.AiohttpV3LanTransport("192.168.1.50", 42000)
        required = ("/v3/pair/owner-receipt", "/v3/pair/token-stage",
                    "/v3/pair/token-commit")
        with patch.dict(sys.modules, {"aiohttp": fake}):
            for route in required:
                self.assertEqual(
                    {"protocol": 3},
                    await transport.post(route, {"protocol": 3}),
                )
            self.assertEqual(
                [("POST", "http://192.168.1.50:42000" + route)
                 for route in required],
                requested,
            )
            for route in (*required, "/v3/pair/other"):
                with self.subTest(route=route), self.assertRaisesRegex(
                        ValueError, "Unrecognized v3 route"):
                    await transport.get(route, {"protocol": 3})
            with self.assertRaisesRegex(ValueError, "Unrecognized v3 route"):
                await transport.post("/v3/pair/arbitrary", {"protocol": 3})


if __name__ == "__main__":
    unittest.main()
