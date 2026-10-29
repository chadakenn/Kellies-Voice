"""Regressions: v3 QA must not modify or depend on installed Kellie's Voice."""
from __future__ import annotations

import importlib
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice_v3_qa"
PKG = "kv_v3_qa_isolated_test"
module = types.ModuleType(PKG)
module.__path__ = [str(ROOT)]
sys.modules[PKG] = module

class MockFlow:
    def __init_subclass__(cls, domain=None, **kwargs):
        return super().__init_subclass__(**kwargs)
    async def async_set_unique_id(self, value):
        self.unique_id = value
    def _abort_if_unique_id_configured(self):
        pass
    def async_show_form(self, **kwargs):
        return {"type": "form", **kwargs}
    def async_abort(self, *, reason):
        return {"type": "abort", "reason": reason}
    def async_create_entry(self, **kwargs):
        raise AssertionError("QA cannot create HA device entry")

class Vol:
    Required = staticmethod(lambda key, default=None: key)
    Schema = staticmethod(lambda shape: shape)

class FakeAttempt:
    def __init__(self, session):
        self.session = session
        self.closed = False
        self.code = "12345678"
    async def start(self, transport):
        return self.code
    async def confirmed_by_tv_remote(self, transport):
        return types.SimpleNamespace(session=self.session, tv_uuid="123e4567-e89b-42d3-a456-426614174000")
    def close(self):
        self.closed = True

class IsolatedV3FlowTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        ha=types.ModuleType("homeassistant")
        ha.__path__=[]
        entries=types.ModuleType("homeassistant.config_entries")
        entries.ConfigFlow=MockFlow
        helpers=types.ModuleType("homeassistant.helpers")
        helpers.__path__=[]
        si=types.ModuleType("homeassistant.helpers.service_info")
        si.__path__=[]
        zc=types.ModuleType("homeassistant.helpers.service_info.zeroconf")
        zc.ZeroconfServiceInfo=type("ZeroconfServiceInfo",(),{})
        vol=types.ModuleType("voluptuous")
        vol.Required,vol.Schema=Vol.Required,Vol.Schema
        cls.patches=patch.dict(sys.modules, {
            "homeassistant":ha,"homeassistant.config_entries":entries,
            "homeassistant.helpers":helpers,
            "homeassistant.helpers.service_info":si,
            "homeassistant.helpers.service_info.zeroconf":zc,
            "voluptuous":vol
        })
        cls.patches.start()
        cls.mod=importlib.import_module(PKG+".config_flow")

    @classmethod
    def tearDownClass(cls):
        cls.patches.stop()

    def flow(self):
        obj=self.mod.KelliesVoiceV3QaConfigFlow()
        obj.context={}
        return obj

    def discovery(self, **extra):
        base={"protocol":"3","mode":"verified-code-only",
              "session":"AQIDBAUGBwgJCgsMDQ4PEA"}
        base.update(extra)
        return types.SimpleNamespace(properties=base,ip_address="192.168.1.100",port=43121)

    async def test_discovery_is_isolated_and_user_cannot_type_credentials(self):
        flow=self.flow()
        a=await flow.async_step_user()
        self.assertEqual("start_on_tv",a["reason"])
        a=await flow.async_step_zeroconf(self.discovery())
        self.assertEqual("compare_start",a["step_id"])
        self.assertTrue(flow.unique_id.startswith("kv-v3-qa:"))
        self.assertNotIn("kellies_voice:",flow.unique_id)

    async def test_full_code_and_remote_proof_is_verification_not_login(self):
        flow=self.flow()
        await flow.async_step_zeroconf(self.discovery())
        with patch.object(self.mod,"HaV3WireAttempt",FakeAttempt):
            x=await flow.async_step_compare_start({"start":True})
            self.assertEqual("compare_code",x["step_id"])
            self.assertEqual("12345678",x["description_placeholders"]["code"])
            x=await flow.async_step_compare_code({
                "ha_code":"12345678","numbers_match":True})
        self.assertEqual({"type":"abort","reason":"verified_test_only"},x)

    async def test_mismatched_code_cannot_authorize_remote(self):
        flow=self.flow()
        await flow.async_step_zeroconf(self.discovery())
        with patch.object(self.mod,"HaV3WireAttempt",FakeAttempt):
            await flow.async_step_compare_start({"start":True})
            prior=flow._attempt
            x=await flow.async_step_compare_code({"ha_code":"99999999","numbers_match":True})
        self.assertEqual("mismatch",x["reason"])
        self.assertTrue(prior.closed)

    async def test_invalid_discovery_version_or_session_or_mode_denied(self):
        for value in ({"protocol":"2"},{"mode":"unknown"},{"session":"not-a-session"}):
            flow=self.flow()
            x=await flow.async_step_zeroconf(self.discovery(**value))
            self.assertEqual("invalid_tv",x["reason"])

    def test_independent_namespace_and_no_credential_handlers(self):
        import json
        manifest=json.loads((ROOT/"manifest.json").read_text())
        self.assertEqual("kellies_voice_v3_qa",manifest["domain"])
        self.assertNotEqual("kellies_voice",manifest["domain"])
        self.assertEqual(["_kellies-pair._tcp.local."],manifest["zeroconf"])
        entry=(ROOT/"config_flow.py").read_text()
        assert "async_create_entry" not in entry
        for forbidden in ("async_create_refresh_token","auth/token","refresh_token",
                          "CredentialStore", "password", "client_secret"):
            self.assertNotIn(forbidden,entry)

if __name__=="__main__":
    unittest.main()
