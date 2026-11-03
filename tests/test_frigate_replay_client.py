"""Authenticated Frigate integration API adapter, no live HA/Frigate required."""
import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
sys.modules.setdefault("custom_components", types.ModuleType("custom_components"))
pkg = sys.modules.setdefault("custom_components.kellies_voice",
                             types.ModuleType("custom_components.kellies_voice"))
pkg.__path__ = [str(ROOT)]


def load(name):
    fq = "custom_components.kellies_voice." + name
    spec = importlib.util.spec_from_file_location(fq, ROOT / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[fq] = module
    spec.loader.exec_module(module)
    return module


sel = load("frigate_replay")
client = load("frigate_replay_client")
NOW = 1_731_000_000
ID = "1730999999.123456-a9b8c7"


def event(camera="doorbell", eid=ID, has_clip=True):
    return {"id": eid, "camera": camera, "label": "person",
            "start_time": NOW-30, "end_time": NOW-20,
            "has_clip": has_clip, "false_positive": False}


class FrigateReplayClientTests(unittest.IsolatedAsyncioTestCase):
    async def query(self, source, **kwargs):
        return await client.fetch_recorded_event_metadata(
            source, configured_cameras={"doorbell": {}, "driveway": {}},
            frigate_camera="doorbell", now=NOW, **kwargs
        )

    async def test_bounded_camera_query_and_completed_recording(self):
        source = types.SimpleNamespace(
            async_get_event=AsyncMock(),
            async_get_events=AsyncMock(return_value=[
                event(), event(camera="driveway", eid="1730999999.123456-b8c7d6")
            ]),
        )
        found = await self.query(source, limit=6)
        self.assertEqual([ID], [x.event_id for x in found])
        source.async_get_events.assert_awaited_once_with(
            cameras=["doorbell"], after=NOW-86400, limit=18, has_clip=True
        )
        source.async_get_event.assert_not_awaited()

    async def test_explicit_id_does_not_fall_back_or_search_other_cameras(self):
        source = types.SimpleNamespace(
            async_get_event=AsyncMock(return_value=event(camera="driveway")),
            async_get_events=AsyncMock(),
        )
        self.assertEqual([], await self.query(source, requested_event_id=ID))
        source.async_get_event.assert_awaited_once_with(ID)
        source.async_get_events.assert_not_awaited()

    async def test_failed_recording_and_offline_are_safe(self):
        source = types.SimpleNamespace(
            async_get_event=AsyncMock(side_effect=RuntimeError(
                "username:super-secret http://private-frigate.local")),
            async_get_events=AsyncMock(),
        )
        with self.assertRaises(client.FrigateReplayUnavailable) as e:
            await self.query(source, requested_event_id=ID)
        self.assertEqual("Frigate event lookup unavailable", str(e.exception))
        self.assertNotIn("private-frigate", str(e.exception))
        source.async_get_event = AsyncMock(return_value=event(has_clip=False))
        self.assertEqual([], await self.query(source, requested_event_id=ID))

    async def test_no_unmapped_cameras_malicious_ids_or_client_fallback(self):
        source = types.SimpleNamespace(
            async_get_event=AsyncMock(), async_get_events=AsyncMock())
        bad = [
            {"configured_cameras": {"driveway": {}}},
            {"frigate_camera": "../doorbell"},
            {"requested_event_id": "../../password"},
            {"requested_event_id": "http://camera.local"},
            {"limit": 0},
            {"lookback_seconds": 0},
        ]
        for setting in bad:
            with self.subTest(setting=setting), self.assertRaises(ValueError):
                args = {
                    "configured_cameras": {"doorbell": {}},
                    "frigate_camera": "doorbell", "now": NOW, **setting,
                }
                await client.fetch_recorded_event_metadata(source, **args)
        source.async_get_event.assert_not_awaited()
        source.async_get_events.assert_not_awaited()
        with self.assertRaises(client.FrigateReplayUnavailable):
            await self.query(types.SimpleNamespace())

    async def test_pages_remain_bounded(self):
        source = types.SimpleNamespace(
            async_get_event=AsyncMock(),
            async_get_events=AsyncMock(return_value=[event()] * 101)
        )
        with self.assertRaises(ValueError):
            await self.query(source, limit=10)


if __name__ == "__main__":
    unittest.main()
