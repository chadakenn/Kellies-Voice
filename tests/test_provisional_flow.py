"""#213 fail-closed provisional config-flow contract, without HA runtime.

Real Home Assistant Core tests are still required before enabling this
feature. Critically, the discovery *session* is not a TV UUID.
"""
import ast
from pathlib import Path
import time
import types
import unittest
from unittest.mock import MagicMock
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
TREE = ast.parse((ROOT / "config_flow.py").read_text())
FLOW = next(n for n in TREE.body
            if isinstance(n, ast.ClassDef) and n.name == "KellieVoiceConfigFlow")
METHOD = next(n for n in FLOW.body
              if isinstance(n, ast.AsyncFunctionDef)
              and n.name == "async_step_provisional_create")


def clean_tv_device_id(value):
    parsed = UUID(value)
    if parsed.version != 4 or parsed.variant != "specified in RFC 4122":
        raise ValueError("not a TV UUID")
    return str(parsed)


NS = {"DOMAIN": "kellies_voice", "time": time,
      "clean_tv_device_id": clean_tv_device_id}
exec(compile(ast.Module(body=[METHOD], type_ignores=[]),
             "provisional_flow_test", "exec"), NS)
TV = "123e4567-e89b-42d3-a456-426614174000"
SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"


class FakeFlow:
    async_step_provisional_create = NS["async_step_provisional_create"]

    def __init__(self):
        self.hass = MagicMock()
        self.hass.config_entries.async_entries.return_value = []
        self.created = []
        self.context = {}
        self._preview_candidate = None
        self._provisional_tv_uuid = None
        self._provisional_owner = None
        self._preview_tv_ok_confirmed = False
        self._verified_tv_session = None

    def async_create_entry(self, **kwargs):
        self.created.append(kwargs)
        return {"type": "created", **kwargs}

    def async_abort(self, **kwargs):
        return {"type": "abort", **kwargs}


class TestProvisionalCreateStep(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.flow = FakeFlow()

    def verified(self):
        # Only simulated server-side results; the live crypto and HA APIs
        # require separate integration tests.
        self.flow.context["user_id"] = "user-1"
        self.flow._preview_candidate = types.SimpleNamespace(session=SESSION)
        self.flow._verified_tv_session = SESSION
        self.flow._preview_tv_ok_confirmed = True
        self.flow._provisional_tv_uuid = TV

    async def test_create_with_authenticated_owner_and_verified_tv(self):
        self.verified()
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("created", result["type"])
        self.assertTrue(result["data"]["provisional"])
        self.assertEqual("user-1", result["data"]["owner_user_id"])
        self.assertEqual(TV, result["data"]["device_id"])
        self.assertNotEqual(SESSION, result["data"]["device_id"])
        self.assertNotIn("access_token", result["data"])
        self.assertNotIn("refresh_token", result["data"])

    async def test_missing_authenticated_owner_rejected(self):
        self.verified()
        self.flow.context.clear()
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("abort", result["type"])
        self.assertEqual("provisional_owner_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_unknown_owner_rejected(self):
        self.verified()
        self.flow.context["user_id"] = "unknown"
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("provisional_owner_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_discovery_without_physical_tv_ok_fails_closed(self):
        self.flow.context["user_id"] = "owner"
        self.flow._preview_candidate = types.SimpleNamespace(session=SESSION)
        self.flow._provisional_tv_uuid = TV
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("provisional_tv_proof_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_matching_remote_proof_without_real_tv_uuid_rejected(self):
        self.verified()
        self.flow._provisional_tv_uuid = None
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("provisional_tv_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_session_nonce_cannot_be_used_as_tv_uuid(self):
        self.verified()
        self.flow._provisional_tv_uuid = SESSION
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("provisional_tv_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_wrong_verified_session_rejected(self):
        self.verified()
        self.flow._verified_tv_session = "AQIDBAUGBwgJCgsMDQ4PEQ"
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("provisional_tv_proof_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_existing_entry_rejected(self):
        self.verified()
        existing = MagicMock()
        existing.data = {"device_id": TV}
        self.flow.hass.config_entries.async_entries.return_value = [existing]
        result = await self.flow.async_step_provisional_create(None)
        self.assertEqual("provisional_exists", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_user_input_owner_tv_cannot_override_verified_context(self):
        self.verified()
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "223e4567-e89b-42d3-a456-426614174000",
            "owner_user_id": "spoofed-owner",
            "verified": True,
        })
        self.assertEqual("created", result["type"])
        self.assertEqual("user-1", result["data"]["owner_user_id"])
        self.assertEqual(TV, result["data"]["device_id"])


if __name__ == "__main__":
    unittest.main()
