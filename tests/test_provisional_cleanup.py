"""#213: tests for provisional entry expiry cleanup.

Uses a fake hass so no full Home Assistant runtime is required.
"""

import time
import unittest
from unittest.mock import AsyncMock, MagicMock

from custom_components.kellies_voice.pairing_provisional import (
    cleanup_expired_provisional_entries,
)


class TestProvisionalCleanup(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.hass = MagicMock()
        self.hass.config_entries = MagicMock()
        self.removed = []

        async def _remove(entry_id):
            self.removed.append(entry_id)

        self.hass.config_entries.async_remove = _remove

    def _entry(self, entry_id, data):
        e = MagicMock()
        e.entry_id = entry_id
        e.data = data
        return e

    async def test_removes_expired_and_invalid_keeps_permanent(self):
        now = time.time()
        expired = self._entry("e1", {
            "provisional": True, "device_id": "tv-1", "started": now - 200,
        })
        invalid = self._entry("e2", {
            "provisional": True, "device_id": "tv-2", "started": "not-a-number",
        })
        fresh = self._entry("e3", {
            "provisional": True, "device_id": "tv-3", "started": now - 10,
        })
        permanent = self._entry("e4", {
            "provisional": False, "device_id": "tv-4", "started": now - 200,
        })
        self.hass.config_entries.async_entries.return_value = [
            expired, invalid, fresh, permanent,
        ]
        count = await cleanup_expired_provisional_entries(self.hass)
        self.assertEqual(count, 2)
        self.assertEqual(sorted(self.removed), ["e1", "e2"])
