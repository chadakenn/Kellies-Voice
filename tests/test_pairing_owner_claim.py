"""Verified TV proof → authenticated HA owner claim. No token operations."""
from __future__ import annotations

from pathlib import Path
import importlib
import sys
import types
import unittest
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
PACKAGE = "kv_authenticated_owner_claim_tests"
mod = types.ModuleType(PACKAGE)
mod.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = mod
claims = importlib.import_module(PACKAGE + ".pairing_owner_claim")
TV_UUID = "123e4567-e89b-42d3-a456-426614174000"
OTHER_TV = "223e4567-e89b-42d3-a456-426614174000"
SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"


def user(id="owner", owner=True, active=True):
    return types.SimpleNamespace(id=id, is_owner=owner, is_active=active)


class OwnerClaimRegistryTests(unittest.TestCase):
    def setUp(self):
        self.registry = claims.OwnerClaimRegistry()

    def begin(self, flow="flow-verified-1", now=100):
        self.registry.begin(flow_id=flow, session=SESSION, tv_uuid=TV_UUID, now=now)

    def test_cannot_claim_without_verified_remote_tv_proof(self):
        self.assertFalse(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=101))
        self.assertIsNone(self.registry.consume(
            flow_id="flow-verified-1", session=SESSION, tv_uuid=TV_UUID, now=102))

    def test_real_owner_claim_consumed_exactly_once(self):
        self.begin()
        self.assertTrue(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=102))
        self.assertEqual("owner", self.registry.consume(
            flow_id="flow-verified-1", session=SESSION, tv_uuid=TV_UUID, now=103))
        self.assertIsNone(self.registry.consume(
            flow_id="flow-verified-1", session=SESSION, tv_uuid=TV_UUID, now=104))
        self.assertFalse(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=105))

    def test_nonowner_inactive_unknown_user_and_wrong_session_rejected(self):
        self.begin()
        for individual in (None, user(owner=False), user(active=False),
                           user(id=""), user(id="unknown")):
            with self.subTest(individual=individual):
                self.assertFalse(self.registry.claim(
                    flow_id="flow-verified-1", session=SESSION,
                    user=individual, now=110))
        self.assertFalse(self.registry.claim(
            flow_id="flow-verified-1", session="AQIDBAUGBwgJCgsMDQ4PEQ",
            user=user(), now=111))
        self.assertTrue(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=112))
        self.assertFalse(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(id="other"), now=113))

    def test_wrong_flow_or_tv_cannot_finish_and_burns_mismatched_attempt(self):
        self.begin()
        self.assertTrue(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=110))
        self.assertIsNone(self.registry.consume(
            flow_id="flow-verified-1", session=SESSION, tv_uuid=OTHER_TV, now=111))
        self.assertIsNone(self.registry.consume(
            flow_id="flow-verified-1", session=SESSION, tv_uuid=TV_UUID, now=112))

    def test_expiry_cancellation_and_duplicate_registration(self):
        self.begin()
        with self.assertRaises(ValueError):
            self.begin(now=101)
        self.assertFalse(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=220))
        self.begin(now=221)
        self.registry.cancel("flow-verified-1")
        self.assertFalse(self.registry.claim(
            flow_id="flow-verified-1", session=SESSION, user=user(), now=222))

    def test_rejects_unverified_or_noncanonical_identifiers(self):
        for invalid in (
            dict(flow_id="bad", session=SESSION, tv_uuid=TV_UUID),
            dict(flow_id="flow-verified-1", session="wrong", tv_uuid=TV_UUID),
            dict(flow_id="flow-verified-1", session=SESSION, tv_uuid=TV_UUID.upper()),
            dict(flow_id="flow-verified-1", session=SESSION, tv_uuid="bad"),
        ):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                self.registry.begin(**invalid, now=100)

    def test_per_instance_registry_isolation(self):
        hass1, hass2 = types.SimpleNamespace(data={}), types.SimpleNamespace(data={})
        a = claims.owner_claims(hass1)
        b = claims.owner_claims(hass2)
        self.assertIsNot(a, b)
        a.begin(flow_id="flow-verified-1", session=SESSION,
                tv_uuid=TV_UUID, now=100)
        self.assertFalse(b.claim(flow_id="flow-verified-1",
                                 session=SESSION, user=user(), now=101))


class OwnerClaimWebSocketTests(unittest.TestCase):
    def setUp(self):
        fake_api = types.ModuleType("homeassistant.components.websocket_api")
        fake_api.websocket_command = lambda _schema: lambda f: f
        fake_api.ERR_UNAUTHORIZED = "unauthorized"
        fake_api.async_register_command = MagicMock()
        fake_components = types.ModuleType("homeassistant.components")
        fake_components.websocket_api = fake_api
        fake_core = types.ModuleType("homeassistant.core")
        fake_core.HomeAssistant = object
        fake_core.callback = lambda f: f
        fake_homeassistant = types.ModuleType("homeassistant")
        fake_homeassistant.__path__ = []
        fake_probatio = types.ModuleType("probatio")
        fake_probatio.Required = lambda name: name
        self.mock_modules = patch.dict(sys.modules, {
            "homeassistant": fake_homeassistant,
            "homeassistant.components": fake_components,
            "homeassistant.components.websocket_api": fake_api,
            "homeassistant.core": fake_core,
            "probatio": fake_probatio,
        })
        self.mock_modules.start()
        self.addCleanup(self.mock_modules.stop)
        # Force a fresh module so the mocks are used in import decorators.
        module_name = PACKAGE + ".pairing_owner_ws"
        sys.modules.pop(module_name, None)
        self.addCleanup(lambda: sys.modules.pop(module_name, None))
        self.ws = importlib.import_module(module_name)
        self.hass = types.SimpleNamespace(data={})
        claims.owner_claims(self.hass).begin(
            flow_id="flow-verified-1", session=SESSION, tv_uuid=TV_UUID)
        self.api = fake_api

    def call(self, person):
        connection = types.SimpleNamespace(
            user=person, send_result=MagicMock(), send_error=MagicMock())
        self.ws.websocket_claim_verified_owner(
            self.hass, connection,
            {"id": 42, "type": "kellies_voice/pair/claim_verified_owner",
             "flow_id": "flow-verified-1", "tv_session": SESSION,
             "user_id": "fake-user-supplied-by-attacker"})
        return connection

    def test_uses_connection_identity_never_body_user_id(self):
        self.call(user(id="owner", owner=False)).send_result.assert_not_called()
        connection = self.call(user(id="authenticated-owner"))
        connection.send_result.assert_called_once_with(42, {"owner_verified": True})
        self.assertEqual("authenticated-owner",
                         claims.owner_claims(self.hass).consume(
                             flow_id="flow-verified-1", session=SESSION,
                             tv_uuid=TV_UUID))
        again = self.call(user(id="authenticated-owner"))
        again.send_result.assert_not_called()

    def test_register_once_and_reject_unsigned_owners(self):
        conn = self.call(user(owner=False))
        conn.send_error.assert_called_once()
        self.api.async_register_command.assert_not_called()
        self.ws.async_register_owner_claim(self.hass)
        self.ws.async_register_owner_claim(self.hass)
        self.api.async_register_command.assert_called_once()


if __name__ == "__main__":
    unittest.main()
