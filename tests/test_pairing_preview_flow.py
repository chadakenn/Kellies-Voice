"""Test the real HA discovery preview flow with fake HA UI plumbing.

The preview must never create a device entry or start a network exchange before
the owner explicitly selects Start on the Home Assistant discovery card.
After TV OK the flow now advances to the owner OAuth consent step.
"""
import ast
import asyncio
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PACKAGE = "kellies_pairing_flow_tests"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package
probes = importlib.import_module(PACKAGE + ".pairing_probe")

tree = ast.parse((ROOT / "config_flow.py").read_text())
flow = next(node for node in tree.body if isinstance(node, ast.ClassDef)
            and node.name == "KellieVoiceConfigFlow")
names = {"async_step_zeroconf", "async_step_pairing_preview",
         "async_step_pairing_code", "async_step_oauth_authorize"}
methods = [node for node in flow.body if isinstance(node, ast.AsyncFunctionDef)
           and node.name in names]
assert len(methods) == 4

class Vol:
    @staticmethod
    def Schema(schema): return schema
    @staticmethod
    def Required(name): return name

TV_UUID = "123e4567-e89b-42d3-a456-426614174000"
SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"

class Backend:
    def __init__(self):
        self.calls = 0
        self.approved = False

    async def begin(self, candidate):
        self.calls += 1
        self.candidate = candidate
        return types.SimpleNamespace(code="12345678", candidate=candidate)

    async def check(self, attempt):
        self.calls += 1
        if not self.approved:
            return None
        return types.SimpleNamespace(tv_uuid=TV_UUID, session=SESSION)


backend = Backend()

def _text(data, key):
    value = data.get(key)
    if isinstance(value, bytes): return value.decode("utf-8").strip()
    return str(value or "").strip()

namespace = {
    "vol": Vol,
    "ZeroconfServiceInfo": object,
    "_text": _text,
    "candidate_from_discovery": probes.candidate_from_discovery,
    "begin_preview": backend.begin,
    "confirm_tv_ok": backend.check,
    "OwnerAuthorizationAttempt": None,
    "register_pending_authorization": lambda *a, **k: None,
    "consume_pending_authorization": lambda *a, **k: None,
    "time": types.SimpleNamespace(time=lambda: 0),
    "_LOGGER": types.SimpleNamespace(debug=lambda *a, **k: None),
}
exec(compile(ast.Module(body=methods, type_ignores=[]), "pairing_flow", "exec"), namespace)

class FakeFlow:
    async_step_zeroconf = namespace["async_step_zeroconf"]
    async_step_pairing_preview = namespace["async_step_pairing_preview"]
    async_step_pairing_code = namespace["async_step_pairing_code"]
    async_step_oauth_authorize = namespace["async_step_oauth_authorize"]

    def __init__(self):
        self._preview_candidate = None
        self._preview_attempt = None
        self._preview_tv_ok_confirmed = False
        self._verified_tv_session = None
        self._provisional_tv_uuid = None
        self._oauth_attempt = None
        self._oauth_state = None
        self._name = "Demo TV"
        self.context = {}
        self.created = []
        self.unique_ids = []
        self.flow_id = "test-flow"
        self.hass = types.SimpleNamespace(
            config=types.SimpleNamespace(api=types.SimpleNamespace(base_url=None))
        )

    async def async_set_unique_id(self, value):
        self.unique_ids.append(value)

    def _abort_if_unique_id_configured(self): pass

    def async_show_form(self, **kwargs):
        return {"type": "form", **kwargs}

    def async_abort(self, **kwargs):
        return {"type": "abort", **kwargs}

    def async_create_entry(self, **kwargs):
        self.created.append(kwargs)
        return {"type": "created"}

    def async_external_step(self, **kwargs):
        return {"type": "external", **kwargs}


class FlowTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        backend.calls = 0
        backend.approved = False
        self.flow = FakeFlow()

    async def discover(self):
        props = {
            "preview": b"2",
            "mode": b"discovery-only",
            "protocol": b"1",
            "session": b"AQIDBAUGBwgJCgsMDQ4PEA",
        }
        info = types.SimpleNamespace(
            properties=props, ip_address="192.168.1.52", port=45011)
        return await self.flow.async_step_zeroconf(info)

    async def test_no_network_before_explicit_owner_start(self):
        view = await self.discover()
        self.assertEqual("pairing_preview", view["step_id"])
        self.assertEqual(0, backend.calls)
        self.assertEqual([], self.flow.created)
        self.assertEqual(["preview:AQIDBAUGBwgJCgsMDQ4PEA"],
                         self.flow.unique_ids)

    async def test_cannot_complete_without_matching_and_physical_tv_ok(self):
        await self.discover()
        code = await self.flow.async_step_pairing_preview({"start": True})
        self.assertEqual("pairing_code", code["step_id"])
        self.assertEqual("12345678", code["description_placeholders"]["code"])
        self.assertEqual(1, backend.calls)
        waiting = await self.flow.async_step_pairing_code({"numbers_match": True})
        self.assertEqual("waiting_for_tv", waiting["errors"]["base"])
        backend.approved = True
        # After TV OK the flow advances to the OAuth consent gate.
        gate = await self.flow.async_step_pairing_code({"numbers_match": True})
        self.assertEqual("form", gate["type"])
        self.assertEqual("oauth_authorize", gate["step_id"])
        self.assertTrue(self.flow._preview_tv_ok_confirmed)
        self.assertEqual(SESSION, self.flow._verified_tv_session)
        self.assertEqual(TV_UUID, self.flow._provisional_tv_uuid)
        self.assertEqual([], self.flow.created)

    async def test_user_rejects_mismatched_code(self):
        await self.discover()
        await self.flow.async_step_pairing_preview({"start": True})
        abort = await self.flow.async_step_pairing_code({"numbers_match": False})
        self.assertEqual("preview_cancelled", abort["reason"])
        self.assertEqual(1, backend.calls)
        self.assertEqual([], self.flow.created)

    async def test_bad_discovery_rejected_before_ui_or_network(self):
        info = types.SimpleNamespace(
            properties={"preview": "2", "mode": "discovery-only",
                        "protocol": "1", "session": "short"},
            ip_address="127.0.0.1", port=80)
        result = await self.flow.async_step_zeroconf(info)
        self.assertEqual("abort", result["type"])
        self.assertEqual("invalid_pairing_preview", result["reason"])
        self.assertEqual(0, backend.calls)

    async def test_oauth_gate_requires_verified_owner(self):
        await self.discover()
        await self.flow.async_step_pairing_preview({"start": True})
        backend.approved = True
        await self.flow.async_step_pairing_code({"numbers_match": True})
        # No user_id in context → fail closed.
        result = await self.flow.async_step_oauth_authorize({"confirm_authorize": True})
        self.assertEqual("abort", result["type"])
        self.assertEqual("oauth_owner_required", result["reason"])


if __name__ == "__main__":
    unittest.main()
