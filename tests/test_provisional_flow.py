"""#213: tests for the supported provisional config-flow step.

Uses a minimal fake flow so no full Home Assistant runtime is required.
"""

import unittest
from unittest.mock import MagicMock

from custom_components.kellies_voice.config_flow import KellieVoiceConfigFlow


class TestProvisionalCreateStep(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.flow = KellieVoiceConfigFlow()
        self.flow.hass = MagicMock()
        self.flow.hass.config_entries = MagicMock()
        self.flow.hass.config_entries.async_entries.return_value = []
        self.created = []

        def _create(**kwargs):
            self.created.append(kwargs)
            return {"type": "created", **kwargs}

        self.flow.async_create_entry = _create
        self.flow.async_abort = lambda **k: {"type": "abort", **k}

    async def test_create_with_valid_owner(self):
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "tv-1",
            "owner_user_id": "user-1",
        })
        self.assertEqual(result["type"], "created")
        self.assertTrue(result["data"]["provisional"])
        self.assertEqual(result["data"]["owner_user_id"], "user-1")
        self.assertEqual(result["data"]["device_id"], "tv-1")

    async def test_unknown_owner_rejected(self):
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "tv-1",
            "owner_user_id": "unknown",
        })
        self.assertEqual(result["type"], "abort")
        self.assertEqual(result["reason"], "provisional_owner_required")
        self.assertEqual(self.created, [])

    async def test_existing_entry_rejected(self):
        existing = MagicMock()
        existing.data = {"device_id": "tv-1"}
        self.flow.hass.config_entries.async_entries.return_value = [existing]
        result = await self.flow.async_step_provisional_create({
            "tv_uuid": "tv-1",
            "owner_user_id": "user-1",
        })
        self.assertEqual(result["type"], "abort")
        self.assertEqual(result["reason"], "provisional_exists")
        self.assertEqual(self.created, [])
