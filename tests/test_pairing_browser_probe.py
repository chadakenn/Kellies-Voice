"""Owner browser OAuth callback probe: no tokens, no config entries, no login.

All requests are fake and in-process. A real HA consent window is an
independent hardware QA gate before any future credential exchange.
"""
from __future__ import annotations

from pathlib import Path
from types import SimpleNamespace, ModuleType
from unittest.mock import AsyncMock, MagicMock, patch
import importlib
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
PACKAGE = "kv_owner_browser_test"
package = ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package
probe = importlib.import_module(PACKAGE + ".pairing_browser_probe")
oauth = importlib.import_module(PACKAGE + ".pairing_owner_oauth")
STATE = "A" * 43
SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"


class BrowserRegistryTests(unittest.TestCase):
    def setUp(self):
        probe.clear_probes()

    def test_single_use_owner_tv_flow_and_ttl(self):
        probe.register_probe(state=STATE, flow_id="flow-1", owner_id="owner-1",
                             tv_session=SESSION, now=500)
        binding = probe.consume_probe(STATE, now=501)
        self.assertEqual("owner-1", binding.owner_id)
        self.assertEqual("flow-1", binding.flow_id)
        self.assertEqual(SESSION, binding.tv_session)
        self.assertIsNone(probe.consume_probe(STATE, now=502))

    def test_expired_and_replayed_states_fail_closed(self):
        probe.register_probe(state=STATE, flow_id="flow", owner_id="owner",
                             tv_session=SESSION, now=0)
        self.assertIsNone(probe.consume_probe(STATE, now=120))
        self.assertIsNone(probe.consume_probe(STATE, now=119))
        probe.register_probe(state=STATE, flow_id="flow", owner_id="owner",
                             tv_session=SESSION, now=500)
        with self.assertRaises(ValueError):
            probe.register_probe(state=STATE, flow_id="flow", owner_id="owner",
                                 tv_session=SESSION, now=501)
        self.assertIsNone(probe.consume_probe("evil-state", now=501))
        self.assertIsNotNone(probe.consume_probe(STATE, now=501))

    def test_owner_session_and_state_must_be_valid(self):
        cases = (
            {"state": "short"}, {"owner_id": "unknown"},
            {"owner_id": ""}, {"flow_id": ""},
            {"tv_session": "invalid"}, {"state": None},
        )
        for change in cases:
            with self.subTest(change=change), self.assertRaises(ValueError):
                probe.register_probe(**{
                    "state": STATE, "flow_id": "flow", "owner_id": "owner",
                    "tv_session": SESSION, "now": 100, **change
                })

    def test_no_token_exchange_is_even_imported(self):
        for file in ("pairing_browser_probe.py", "pairing_owner_oauth.py"):
            code = (ROOT / file).read_text()
            self.assertNotIn("HomeAssistantTokenExchange", code)
            self.assertNotIn("exchange_from_consumed", code)
            self.assertNotIn("client.post(", code)
            self.assertNotIn("CredentialStore", code)


class BrowserCallbackTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        probe.clear_probes()
        class Response:
            def __init__(self, *, text, content_type, status=200, headers=None):
                self.text = text
                self.content_type = content_type
                self.status = status
                self.headers = headers
        web = ModuleType("aiohttp.web")
        web.Response = Response
        aiohttp = ModuleType("aiohttp")
        aiohttp.web = web
        self.patch = patch.dict(sys.modules, {
            "aiohttp": aiohttp, "aiohttp.web": web,
        })
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.hass = MagicMock()
        self.hass.config_entries.flow.async_configure = AsyncMock()
        self.view = probe.OAuthBrowserProbeView(self.hass)

    async def test_owner_bound_real_callback_resumes_only_once(self):
        probe.register_probe(state=STATE, flow_id="flow-1", owner_id="owner-1",
                             tv_session=SESSION)
        request = SimpleNamespace(query={
            "state": STATE, "code": "AUTHORIZATIONCODE123456789"
        })
        result = await self.view.get(request)
        self.assertEqual(200, result.status)
        self.assertNotIn("AUTHORIZATIONCODE", result.text)
        self.assertEqual("no-store", result.headers["Cache-Control"])
        self.hass.config_entries.flow.async_configure.assert_awaited_once()
        args, kwargs = self.hass.config_entries.flow.async_configure.await_args
        self.assertEqual("flow-1", args[0])
        self.assertEqual("owner-1", kwargs["user_input"]["probe_owner_id"])
        self.assertEqual(SESSION, kwargs["user_input"]["probe_tv_session"])
        self.assertEqual(STATE, kwargs["user_input"]["probe_state"])
        result2 = await self.view.get(request)
        self.assertEqual(400, result2.status)
        self.assertEqual(1, self.hass.config_entries.flow.async_configure.await_count)

    async def test_denied_or_unknown_callback_never_resumes(self):
        for parameters in ({}, {"state": STATE, "code": "invalid code"},
                           {"state": "evil", "code": "AUTHORIZATIONCODE123456789"}):
            with self.subTest(parameters=parameters):
                self.assertEqual(400, (await self.view.get(
                    SimpleNamespace(query=parameters))).status)
        probe.register_probe(state=STATE, flow_id="flow-1", owner_id="owner-1",
                             tv_session=SESSION)
        self.assertEqual(400, (await self.view.get(SimpleNamespace(query={
            "state": STATE, "error": "access_denied"
        }))).status)
        self.assertIsNone(probe.consume_probe(STATE))
        self.hass.config_entries.flow.async_configure.assert_not_awaited()

    async def test_resume_error_cannot_leak_authorization_code(self):
        probe.register_probe(state=STATE, flow_id="flow-1", owner_id="owner-1",
                             tv_session=SESSION)
        self.hass.config_entries.flow.async_configure.side_effect = RuntimeError(
            "secret AUTHORIZATIONCODE123456789 should not appear"
        )
        result = await self.view.get(SimpleNamespace(query={
            "state": STATE, "code": "AUTHORIZATIONCODE123456789"
        }))
        self.assertEqual(400, result.status)
        self.assertNotIn("AUTHORIZATIONCODE", result.text)
        self.assertIsNone(probe.consume_probe(STATE))


if __name__ == "__main__":
    unittest.main()
