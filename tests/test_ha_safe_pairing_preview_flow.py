"""Verify the real Home Assistant preview flow's controls without HA Core.

This branch is intentionally MAIN-based and has no OAuth imports or flows.
Backend transport is fake; real two-device code+TV-OK was already tested
on onn hardware and Windows in #228.
"""
from __future__ import annotations

import ast
import importlib
import json
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
PACKAGE = "kv_ha_safe_flow"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package
probe = importlib.import_module(PACKAGE + ".pairing_probe")

source = (ROOT / "config_flow.py").read_text(encoding="utf-8")
tree = ast.parse(source)
flow_cls = next(x for x in tree.body if isinstance(x, ast.ClassDef)
                and x.name == "KellieVoiceConfigFlow")
method_names = ("async_step_zeroconf", "async_step_pairing_preview",
                "async_step_pairing_code")
methods = [x for x in flow_cls.body
           if isinstance(x, ast.AsyncFunctionDef) and x.name in method_names]
assert len(methods) == 3

class FakeVol:
    defaults = {}
    @staticmethod
    def Schema(value):
        return value
    @staticmethod
    def Required(value, default=None):
        if default is not None:
            FakeVol.defaults[value] = default
        return value

class FakeClock:
    now = 5000
    @classmethod
    def monotonic(cls):
        return cls.now

class Backend:
    def __init__(self):
        self.calls = 0
        self.remote_ok = False
        self.invalid_uuid = False
        self.fail_open = False

    async def begin(self, candidate):
        self.calls += 1
        if self.fail_open:
            raise ValueError("unreachable")
        return types.SimpleNamespace(code="12345678", candidate=candidate)

    async def confirm(self, attempt):
        self.calls += 1
        if self.invalid_uuid:
            return types.SimpleNamespace(session="WRONG", tv_uuid="bad")
        if self.remote_ok:
            return types.SimpleNamespace(
                session=attempt.candidate.session,
                tv_uuid="123e4567-e89b-42d3-a456-426614174000")
        return None

backend = Backend()

def _text(properties, key):
    value = properties.get(key)
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace").strip()
    return str(value or "").strip()

namespace = {
    "vol": FakeVol,
    "_text": _text,
    "candidate_from_discovery": probe.candidate_from_discovery,
    "begin_preview": backend.begin,
    "confirm_tv_ok": backend.confirm,
    "time": FakeClock,
}
exec(compile(ast.Module(body=methods, type_ignores=[]),
             "ha_preview_flow", "exec"), namespace)

class FakeFlow:
    async_step_zeroconf = namespace["async_step_zeroconf"]
    async_step_pairing_preview = namespace["async_step_pairing_preview"]
    async_step_pairing_code = namespace["async_step_pairing_code"]

    def __init__(self):
        self.context = {}
        self._preview_candidate = None
        self._preview_attempt = None
        self._preview_deadline = 0
        self._name = "Normal TV"
        self._device_id = ""
        self.created = []
        self.ids = []

    async def async_set_unique_id(self, value):
        self.ids.append(value)

    def _abort_if_unique_id_configured(self):
        pass

    def async_abort(self, **kw):
        return {"type": "abort", **kw}

    def async_show_form(self, **kw):
        return {"type": "form", **kw}

    def async_create_entry(self, **kw):
        self.created.append(kw)
        return {"type": "entry", **kw}

    async def async_step_confirm(self):
        return {"type": "form", "step_id": "confirm"}

class PreviewFlowTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        backend.calls = 0
        backend.remote_ok = False
        backend.invalid_uuid = False
        backend.fail_open = False
        FakeClock.now = 5000
        self.flow = FakeFlow()
        FakeVol.defaults = {}

    async def discover(self, extra=None):
        props = {
            "preview": b"2",
            "mode": b"discovery-only",
            "protocol": b"1",
            "session": b"AQIDBAUGBwgJCgsMDQ4PEA",
        }
        props.update(extra or {})
        return await self.flow.async_step_zeroconf(
            types.SimpleNamespace(properties=props,
                                  ip_address="192.168.1.56", port=48700))

    async def test_discovery_no_auto_connection_or_config_entry(self):
        result = await self.discover()
        self.assertEqual("pairing_preview", result["step_id"])
        self.assertEqual(0, backend.calls)
        self.assertEqual(["preview:AQIDBAUGBwgJCgsMDQ4PEA"], self.flow.ids)
        self.assertEqual([], self.flow.created)

    async def test_real_remote_ok_exits_success_without_oauth(self):
        await self.discover()
        start = await self.flow.async_step_pairing_preview({"start": True})
        self.assertEqual("pairing_code", start["step_id"])
        self.assertEqual("12345678", start["description_placeholders"]["code"])
        # The code MUST be in the actual HA form schema, not only in
        # translated description strings that can be stale or absent.
        self.assertEqual("12345678", FakeVol.defaults["ha_code"])
        self.assertEqual(str, start["data_schema"]["ha_code"])
        self.assertIn("numbers_match", start["data_schema"])
        self.assertEqual(1, backend.calls)
        waiting = await self.flow.async_step_pairing_code({
            "numbers_match": True, "ha_code": "12345678"
        })
        self.assertEqual("waiting_for_tv", waiting["errors"]["base"])
        backend.remote_ok = True
        verified = await self.flow.async_step_pairing_code({
            "numbers_match": True, "ha_code": "12345678"
        })
        self.assertEqual({"type": "abort", "reason": "preview_verified"}, verified)
        self.assertEqual([], self.flow.created)
        self.assertIsNone(self.flow._preview_candidate)
        self.assertIsNone(self.flow._preview_attempt)

    async def test_start_false_cancels_without_contacting_tv(self):
        await self.discover()
        result = await self.flow.async_step_pairing_preview({"start": False})
        self.assertEqual("preview_cancelled", result["reason"])
        self.assertEqual(0, backend.calls)

    async def test_nonmatching_code_cancels_without_remote_ok(self):
        await self.discover()
        await self.flow.async_step_pairing_preview({"start": True})
        result = await self.flow.async_step_pairing_code({"numbers_match": False})
        self.assertEqual("preview_cancelled", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_missing_or_modified_visible_code_cannot_claim_success(self):
        for code in (None, "", "87654321", "1234567", "12345678 "):
            with self.subTest(code=code):
                self.flow = FakeFlow()
                await self.discover()
                await self.flow.async_step_pairing_preview({"start": True})
                backend.remote_ok = True
                data = {"numbers_match": True}
                if code is not None:
                    data["ha_code"] = code
                before = backend.calls
                result = await self.flow.async_step_pairing_code(data)
                self.assertEqual(
                    {"type": "abort", "reason": "preview_code_missing"}, result
                )
                self.assertEqual(before, backend.calls)
                self.assertEqual([], self.flow.created)
                self.assertIsNone(self.flow._preview_attempt)

    async def test_stale_window_never_authorizes_tv(self):
        await self.discover()
        await self.flow.async_step_pairing_preview({"start": True})
        backend.remote_ok = True
        FakeClock.now += 101
        result = await self.flow.async_step_pairing_code({"numbers_match": True})
        self.assertEqual("preview_expired", result["reason"])
        self.assertEqual(1, backend.calls)
        self.assertEqual([], self.flow.created)

    async def test_wrong_session_never_succeeds(self):
        await self.discover()
        await self.flow.async_step_pairing_preview({"start": True})
        backend.invalid_uuid = True
        result = await self.flow.async_step_pairing_code({
            "numbers_match": True, "ha_code": "12345678"
        })
        self.assertEqual("preview_unavailable", result["errors"]["base"])
        self.assertEqual([], self.flow.created)

    async def test_untrusted_discovery_rejected_before_network(self):
        result = await self.discover({"mode": b"authorize"})
        self.assertEqual("invalid_pairing_preview", result["reason"])
        self.assertEqual(0, backend.calls)

    async def test_failed_network_stays_on_preview_and_creates_nothing(self):
        await self.discover()
        backend.fail_open = True
        result = await self.flow.async_step_pairing_preview({"start": True})
        self.assertEqual("preview_unavailable", result["errors"]["base"])
        self.assertEqual([], self.flow.created)

    def test_qa_translation_has_friendly_text_and_code_label(self):
        for path in (
            ROOT / "strings.json", ROOT / "translations" / "en.json"
        ):
            config = json.loads(path.read_text(encoding="utf-8"))["config"]
            code = config["step"]["pairing_code"]
            self.assertIn("{code}", code["description"])
            self.assertIn("Home Assistant verification code", code["data"]["ha_code"])
            self.assertIn("numbers_match", code["data"])
            self.assertIn("preview_code_missing", config["abort"])
            self.assertIn("preview_verified", config["abort"])

    def test_no_oauth_handlers_or_new_entry_path_on_preview_only_branch(self):
        preview = next(x for x in flow_cls.body if
                       isinstance(x, ast.AsyncFunctionDef)
                       and x.name == "async_step_pairing_code")
        self.assertNotIn("async_create_entry", ast.unparse(preview))
        self.assertNotIn("async_step_oauth", source)
        self.assertNotIn("from .pairing_owner_oauth", source)
        self.assertNotIn("PendingOAuthTransaction", source)

if __name__ == "__main__":
    unittest.main()
