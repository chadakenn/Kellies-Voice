"""#213: tests for the provisional paired TV entry.

Uses a fake hass so no full Home Assistant runtime is required.
"""

import time
import unittest
from unittest.mock import AsyncMock, MagicMock

from custom_components.kellies_voice.pairing_provisional import (
    create_provisional_entry,
    rollback_provisional_entry,
    is_provisional_allowed,
    ProvisionalTVEntry,
)


class TestProvisionalEntry(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.hass = MagicMock()
        self.hass.config_entries = MagicMock()
        self.hass.config_entries.async_entries.return_value = []
        self.hass.config_entries.async_add = AsyncMock(
            return_value=MagicMock(entry_id="prov-1")
        )
        self.hass.config_entries.async_get_entry.return_value = MagicMock(
            entry_id="prov-1",
            data={
                "provisional": True,
                "device_id": "tv-1",
                "owner_user_id": "user-1",
                "started": time.time(),
            },
        )
        self.hass.config_entries.async_remove = AsyncMock()

    async def test_create_and_rollback(self):
        prov = await create_provisional_entry(
            self.hass, tv_uuid="tv-1", owner_user_id="user-1"
        )
        self.assertIsNotNone(prov)
        self.assertEqual(prov.tv_uuid, "tv-1")
        removed = await rollback_provisional_entry(self.hass, prov)
        self.assertTrue(removed)
        self.hass.config_entries.async_remove.assert_awaited_once()

    async def test_missing_owner_rejected(self):
        prov = await create_provisional_entry(
            self.hass, tv_uuid="tv-1", owner_user_id=""
        )
        self.assertIsNone(prov)

    async def test_is_provisional_allowed_only_for_owner(self):
        entry = MagicMock()
        entry.data = {
            "device_id": "tv-1",
            "provisional": True,
            "owner_user_id": "user-1",
            "started": time.time(),
        }
        self.hass.config_entries.async_entries.return_value = [entry]
        user = MagicMock()
        user.id = "user-1"
        self.assertTrue(is_provisional_allowed(self.hass, "tv-1", user))
        other = MagicMock()
        other.id = "user-2"
        self.assertFalse(is_provisional_allowed(self.hass, "tv-1", other))
        self.assertFalse(is_provisional_allowed(self.hass, "tv-1", None))

    async def test_expired_provisional_rejected(self):
        entry = MagicMock()
        entry.data = {
            "device_id": "tv-1",
            "provisional": True,
            "owner_user_id": "user-1",
            "started": time.time() - 200,
        }
        self.hass.config_entries.async_entries.return_value = [entry]
        user = MagicMock()
        user.id = "user-1"
        self.assertFalse(is_provisional_allowed(self.hass, "tv-1", user))

    async def test_rollback_after_expiry_still_removes(self):
        prov = ProvisionalTVEntry("prov-1", "tv-1", "user-1", time.time() - 200)
        removed = await rollback_provisional_entry(self.hass, prov)
        self.assertTrue(removed)
        self.hass.config_entries.async_remove.assert_awaited_once()

    async def test_rollback_refuses_non_provisional(self):
        self.hass.config_entries.async_get_entry.return_value = MagicMock(
            data={"provisional": False, "device_id": "tv-1", "owner_user_id": "user-1"}
        )
        prov = ProvisionalTVEntry("prov-1", "tv-1", "user-1", 0.0)
        removed = await rollback_provisional_entry(self.hass, prov)
        self.assertFalse(removed)
        self.hass.config_entries.async_remove.assert_not_awaited()

    async def test_create_refuses_existing_permanent(self):
        permanent = MagicMock()
        permanent.data = {"device_id": "tv-1", "provisional": False}
        self.hass.config_entries.async_entries.return_value = [permanent]
        prov = await create_provisional_entry(
            self.hass, tv_uuid="tv-1", owner_user_id="user-1"
        )
        self.assertIsNone(prov)
