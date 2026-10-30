"""Token-free v3 remote-OK to signed-in HA owner consent flow (synthetic HA)."""
from __future__ import annotations
import importlib
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch, AsyncMock

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice_v3qa"
PKG = "kv_v3qa_flow_owner_tests"
SID = "AQIDBAUGBwgJCgsMDQ4PEA"
TV = "123e4567-e89b-42d3-a456-426614174000"


class FakeConfigFlow:
    def __init_subclass__(cls, domain=None, **kwargs):
        super().__init_subclass__(**kwargs)

    async def async_set_unique_id(self, value):
        self.unique_id = value

    def _abort_if_unique_id_configured(self):
        pass

    def async_show_form(self, **kwargs):
        return {"type": "form", **kwargs}

    def async_abort(self, *, reason):
        return {"type": "abort", "reason": reason}

    def async_create_entry(self, **kwargs):
        raise AssertionError("QA flow may never create a config entry")


class FakeAttempt:
    def __init__(self, session):
        self.session = session
        self.waiting = False
        self.invalid = False

    async def start(self, transport):
        assert transport._base.startswith("http://192.168.1.100:")
        return "87654321"

    async def confirmed_by_tv_remote(self, transport):
        if self.invalid:
            raise ValueError("Invalid physical TV proof")
        if self.waiting:
            return None
        return types.SimpleNamespace(session=self.session, tv_uuid=TV)

    def close(self):
        pass


class V3QaOwnerFlowTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        pkg = types.ModuleType(PKG)
        pkg.__path__ = [str(ROOT)]
        pkg.async_register_qa_owner_controls = AsyncMock()
        cls.pkg = pkg
        modules = {
            PKG: pkg,
            "homeassistant": types.ModuleType("homeassistant"),
            "homeassistant.config_entries": types.ModuleType("homeassistant.config_entries"),
            "homeassistant.helpers": types.ModuleType("homeassistant.helpers"),
            "homeassistant.helpers.service_info": types.ModuleType("homeassistant.helpers.service_info"),
            "homeassistant.helpers.service_info.zeroconf": types.ModuleType("homeassistant.helpers.service_info.zeroconf"),
            "voluptuous": types.ModuleType("voluptuous"),
        }
        modules["homeassistant"].__path__ = []
        modules["homeassistant.config_entries"].ConfigFlow = FakeConfigFlow
        modules["homeassistant.helpers"].__path__ = []
        modules["homeassistant.helpers.service_info"].__path__ = []
        modules["homeassistant.helpers.service_info.zeroconf"].ZeroconfServiceInfo = object
        vol = modules["voluptuous"]
        vol.Required = lambda key, default=None: key
        vol.Schema = lambda schema: schema
        cls.modules = patch.dict(sys.modules, modules)
        cls.modules.start()
        cls.flow_module = importlib.import_module(PKG + ".config_flow")
        cls.claims = importlib.import_module(PKG + ".owner_claim")

    @classmethod
    def tearDownClass(cls):
        cls.modules.stop()
        for suffix in ("config_flow", "owner_claim", "pairing_key_commitment",
                       "pairing_v3_wire", "pairing_v3_verified", "validation", "const"):
            sys.modules.pop(PKG + "." + suffix, None)

    def make(self):
        flow = self.flow_module.KelliesVoiceV3QaConfigFlow()
        flow.flow_id = "flow-verified-1"
        flow.context = {}
        flow.hass = types.SimpleNamespace(
            data={}, auth=types.SimpleNamespace(async_get_user=AsyncMock(
                return_value=types.SimpleNamespace(
                    id="actual-owner", is_owner=True, is_active=True))))
        self.pkg.async_register_qa_owner_controls.reset_mock()
        return flow

    async def verified(self, flow):
        info = types.SimpleNamespace(
            properties={"protocol": "3", "mode": "verified-code-only", "session": SID},
            ip_address="192.168.1.100", port=40301,
        )
        discovered = await flow.async_step_zeroconf(info)
        self.assertEqual(discovered["step_id"], "v3_preview")
        with patch.object(self.flow_module, "HaV3WireAttempt", FakeAttempt):
            started = await flow.async_step_v3_preview({"start_test": True})
            self.assertEqual(started["step_id"], "v3_code")
            return await flow.async_step_v3_code(
                {"ha_code": "87654321", "numbers_match": True})

    async def test_tv_then_owner_then_success_no_config_entry(self):
        flow = self.make()
        result = await self.verified(flow)
        self.assertEqual(result["step_id"], "owner_approval")
        self.pkg.async_register_qa_owner_controls.assert_awaited_once()
        registry = self.claims.owner_claims(flow.hass)
        person = types.SimpleNamespace(id="actual-owner", is_owner=True, is_active=True)
        # Premature Continue cannot burn the owner consent.
        too_early = await flow.async_step_owner_approval(
            {"ha_code": "87654321", "owner_approved": True})
        self.assertEqual(too_early["errors"]["base"], "owner_pending")
        self.assertEqual(len(registry.pending(user=person)), 1)
        self.assertTrue(registry.claim(flow_id=flow.flow_id, session=SID, user=person))
        success = await flow.async_step_owner_approval(
            {"ha_code": "87654321", "owner_approved": True})
        self.assertEqual(success["reason"], "owner_verified_no_login")
        self.assertIsNone(flow._owner_tv)
        self.assertEqual([], registry.pending(user=person))

    async def test_bad_tv_proof_does_not_offer_owner_approval(self):
        flow = self.make()
        info = types.SimpleNamespace(properties={
            "protocol": "3", "mode": "verified-code-only", "session": SID,
        }, ip_address="192.168.1.100", port=40301)
        await flow.async_step_zeroconf(info)
        with patch.object(self.flow_module, "HaV3WireAttempt", FakeAttempt):
            await flow.async_step_v3_preview({"start_test": True})
            flow._attempt.invalid = True
            result = await flow.async_step_v3_code({
                "ha_code": "87654321", "numbers_match": True})
        self.assertEqual(result["reason"], "expired")
        self.pkg.async_register_qa_owner_controls.assert_not_awaited()

    async def test_nonowner_and_expired_attempt_denied(self):
        flow = self.make()
        await self.verified(flow)
        registry = self.claims.owner_claims(flow.hass)
        admin = types.SimpleNamespace(id="admin", is_owner=False, is_active=True)
        self.assertFalse(registry.claim(flow_id=flow.flow_id, session=SID, user=admin))
        flow._owner_deadline = 0
        result = await flow.async_step_owner_approval(
            {"ha_code": "87654321", "owner_approved": True})
        self.assertEqual(result["reason"], "owner_expired")

    async def test_different_owner_id_rejected_even_if_other_owner_active(self):
        flow = self.make()
        await self.verified(flow)
        original = types.SimpleNamespace(id="owner", is_owner=True, is_active=True)
        self.assertTrue(self.claims.owner_claims(flow.hass).claim(
            flow_id=flow.flow_id, session=SID, user=original))
        flow.hass.auth.async_get_user.return_value = types.SimpleNamespace(
            id="another-owner", is_owner=True, is_active=True)
        result = await flow.async_step_owner_approval(
            {"ha_code": "87654321", "owner_approved": True})
        self.assertEqual(result["reason"], "owner_denied")

    async def test_owner_privileges_are_rechecked_at_end(self):
        flow = self.make()
        await self.verified(flow)
        owner = types.SimpleNamespace(id="owner", is_owner=True, is_active=True)
        self.assertTrue(self.claims.owner_claims(flow.hass).claim(
            flow_id=flow.flow_id, session=SID, user=owner))
        flow.hass.auth.async_get_user.return_value = types.SimpleNamespace(
            id="owner", is_owner=False, is_active=True)
        result = await flow.async_step_owner_approval(
            {"ha_code": "87654321", "owner_approved": True})
        self.assertEqual(result["reason"], "owner_denied")


if __name__ == "__main__":
    unittest.main()
