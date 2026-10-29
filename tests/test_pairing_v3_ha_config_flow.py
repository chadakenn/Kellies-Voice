"""Actual HA v3 config-flow method tests with fake Home Assistant frontend.

All synthetic. Real v3 crypto/HTTP is separately tested in test_pairing_v3_wire.py.
No HA account, live config entry, OAuth, token or TV is touched.
"""
from __future__ import annotations

import importlib
from pathlib import Path
import sys
import types
from unittest.mock import patch
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
PKG = "kv_ha_v3_config_flow_tests"
module = types.ModuleType(PKG)
module.__path__ = [str(ROOT)]
sys.modules[PKG] = module

SID = "AQIDBAUGBwgJCgsMDQ4PEA"
UUID = "123e4567-e89b-42d3-a456-426614174000"


class FakeFlow:
    def __init_subclass__(cls, domain=None, **kwargs):
        super().__init_subclass__(**kwargs)

    async def async_set_unique_id(self, value):
        self.unique = value

    def _abort_if_unique_id_configured(self):
        pass

    def async_abort(self, *, reason):
        return {"type": "abort", "reason": reason}

    def async_show_form(self, **kwargs):
        return {"type": "form", **kwargs}

    def async_create_entry(self, **kwargs):
        raise AssertionError("Verification-only path MUST NOT create an HA entry")


class FakeVol:
    @staticmethod
    def Required(key, default=None):
        return key

    @staticmethod
    def Optional(key, default=None):
        return key

    @staticmethod
    def Schema(data):
        return data


class FakeAttempt:
    def __init__(self, session):
        self.session = session
        self.code = "87654321"
        self.closed = False
        self.calls = 0
        self.waiting = False

    async def start(self, transport):
        self.calls += 1
        assert transport._base.startswith("http://192.168.1.100:")
        return self.code

    async def confirmed_by_tv_remote(self, transport):
        self.calls += 1
        if self.waiting:
            return None
        return types.SimpleNamespace(session=self.session, tv_uuid=UUID)

    def close(self):
        self.closed = True


class HaV3PreviewTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        fake_hass = types.ModuleType("homeassistant")
        fake_hass.__path__ = []
        fake_config_entries = types.ModuleType("homeassistant.config_entries")
        fake_config_entries.ConfigFlow = FakeFlow
        fake_config_entries.OptionsFlow = type("OptionsFlow", (), {})
        fake_helpers = types.ModuleType("homeassistant.helpers")
        fake_helpers.__path__ = []
        fake_info = types.ModuleType("homeassistant.helpers.service_info")
        fake_info.__path__ = []
        fake_zc = types.ModuleType("homeassistant.helpers.service_info.zeroconf")
        fake_zc.ZeroconfServiceInfo = type("ZeroconfServiceInfo", (), {})
        fake_vol = types.ModuleType("voluptuous")
        fake_vol.Required = FakeVol.Required
        fake_vol.Optional = FakeVol.Optional
        fake_vol.Schema = FakeVol.Schema
        cls.patched_modules = patch.dict(sys.modules, {
            "homeassistant": fake_hass,
            "homeassistant.config_entries": fake_config_entries,
            "homeassistant.helpers": fake_helpers,
            "homeassistant.helpers.service_info": fake_info,
            "homeassistant.helpers.service_info.zeroconf": fake_zc,
            "voluptuous": fake_vol,
        })
        cls.patched_modules.start()
        cls.flow_module = importlib.import_module(PKG + ".config_flow")

    @classmethod
    def tearDownClass(cls):
        cls.patched_modules.stop()

    def make(self):
        flow = self.flow_module.KellieVoiceConfigFlow()
        flow.context = {}
        flow.unique = None
        return flow

    @staticmethod
    def discovery(*, props=None, ip="192.168.1.100", port=41230):
        if props is None:
            props = {
                "protocol": "3", "mode": "verified-code-only", "session": SID
            }
        return types.SimpleNamespace(
            properties=props, ip_address=ip, port=port,
        )

    async def test_real_v3_flow_never_creates_entry_or_reuses_v1(self):
        f = self.make()
        result = await f.async_step_zeroconf(self.discovery())
        self.assertEqual("v3_preview", result["step_id"])
        self.assertTrue(f.unique.startswith("v3-preview:"))
        self.assertIn("v3 TEST", f.context["title_placeholders"]["name"])
        with patch.object(self.flow_module, "HaV3WireAttempt", FakeAttempt):
            result = await f.async_step_v3_preview({"start_v3_test": True})
            self.assertEqual("v3_code", result["step_id"])
            self.assertEqual("87654321", result["description_placeholders"]["code"])
            result = await f.async_step_v3_code({
                "ha_code": "87654321", "numbers_match": True
            })
        self.assertEqual("abort", result["type"])
        self.assertEqual("v3_preview_verified", result["reason"])
        self.assertIsNone(f._v3_attempt)

    async def test_absent_tv_remote_stays_in_code_form_without_claiming_login(self):
        f = self.make()
        await f.async_step_zeroconf(self.discovery())
        with patch.object(self.flow_module, "HaV3WireAttempt", FakeAttempt):
            await f.async_step_v3_preview({"start_v3_test": True})
            f._v3_attempt.waiting = True
            result = await f.async_step_v3_code({
                "ha_code": "87654321", "numbers_match": True,
            })
        self.assertEqual("v3_code", result["step_id"])
        self.assertEqual("v3_waiting_for_tv", result["errors"]["base"])

    async def test_cancel_or_changed_code_refused_without_persisted_login(self):
        for data, expected in (
            ({"numbers_match": False, "ha_code": "87654321"}, "v3_cancelled"),
            ({"numbers_match": True, "ha_code": "00000000"}, "v3_code_mismatch"),
        ):
            with self.subTest(data=data):
                f = self.make()
                await f.async_step_zeroconf(self.discovery())
                with patch.object(self.flow_module, "HaV3WireAttempt", FakeAttempt):
                    await f.async_step_v3_preview({"start_v3_test": True})
                    result = await f.async_step_v3_code(data)
                self.assertEqual(expected, result["reason"])
                self.assertIsNone(f._v3_attempt)

    async def test_untrusted_or_remote_address_never_accepted(self):
        for props, ip, port in (
            ({"protocol": "3", "mode": "bad", "session": SID}, "192.168.1.100", 40000),
            ({"protocol": "3", "mode": "verified-code-only", "session": "bad"}, "192.168.1.100", 40000),
            ({"protocol": "3", "mode": "verified-code-only", "session": SID}, "8.8.8.8", 40000),
            ({"protocol": "3", "mode": "verified-code-only", "session": SID}, "192.168.1.100", 0),
        ):
            f = self.make()
            result = await f.async_step_zeroconf(self.discovery(
                props=props, ip=ip, port=port))
            self.assertEqual("v3_invalid_discovery", result["reason"])
            self.assertIsNone(f._v3_attempt)

    async def test_stable_existing_v1_discovery_is_unchanged(self):
        f = self.make()
        result = await f.async_step_zeroconf(self.discovery(props={
            "id": UUID, "name": "Livingroom TV",
        }))
        self.assertEqual("confirm", result["step_id"])
        self.assertEqual(UUID, f.unique)
        self.assertEqual("Livingroom TV", f._name)
        self.assertIsNone(f._v3_attempt)


if __name__ == "__main__":
    unittest.main()
