"""#213 provisional config-flow unit contract, without HA installed.

This deliberately tests only step logic. A separate, installed-HA-Core
integration test must prove real config entry creation and cleanup.
"""
import ast
from pathlib import Path
import time
import unittest
from unittest.mock import MagicMock

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
TREE = ast.parse((ROOT / "config_flow.py").read_text())
FLOW = next(n for n in TREE.body
            if isinstance(n, ast.ClassDef) and n.name == "KellieVoiceConfigFlow")
METHOD = next(n for n in FLOW.body
              if isinstance(n, ast.AsyncFunctionDef)
              and n.name == "async_step_provisional_create")
NS = {"DOMAIN": "kellies_voice", "time": time}
exec(compile(ast.Module(body=[METHOD], type_ignores=[]),
             "provisional_flow_test", "exec"), NS)


class FakeFlow:
    async_step_provisional_create = NS["async_step_provisional_create"]

    def __init__(self):
        self.hass = MagicMock()
        self.hass.config_entries.async_entries.return_value = []
        self.created = []

    def async_create_entry(self, **kwargs):
        self.created.append(kwargs)
        return {"type": "created", **kwargs}

    def async_abort(self, **kwargs):
        return {"type": "abort", **kwargs}


class TestProvisionalCreateStep(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.flow = FakeFlow()

    async def test_create_with_valid_owner(self):
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "tv-1", "owner_user_id": "user-1"
        })
        self.assertEqual("created", result["type"])
        self.assertTrue(result["data"]["provisional"])
        self.assertEqual("user-1", result["data"]["owner_user_id"])
        self.assertEqual("tv-1", result["data"]["device_id"])
        self.assertNotIn("access_token", result["data"])
        self.assertNotIn("refresh_token", result["data"])

    async def test_unknown_owner_rejected(self):
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "tv-1", "owner_user_id": "unknown"
        })
        self.assertEqual("abort", result["type"])
        self.assertEqual("provisional_owner_required", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_existing_entry_rejected(self):
        existing = MagicMock()
        existing.data = {"device_id": "tv-1"}
        self.flow.hass.config_entries.async_entries.return_value = [existing]
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "tv-1", "owner_user_id": "user-1"
        })
        self.assertEqual("abort", result["type"])
        self.assertEqual("provisional_exists", result["reason"])
        self.assertEqual([], self.flow.created)

    async def test_missing_owner_or_tv_rejected(self):
        for payload, reason in [
            (None, "provisional_incomplete"),
            ({"owner_user_id": "user-1"}, "provisional_incomplete"),
            ({"tv_uuid": "tv-1"}, "provisional_owner_required"),
        ]:
            with self.subTest(payload=payload):
                result = await self.flow.async_step_provisional_create(payload)
                self.assertEqual("abort", result["type"])
                self.assertEqual(reason, result["reason"])
        self.assertEqual([], self.flow.created)


if __name__ == "__main__":
    unittest.main()
