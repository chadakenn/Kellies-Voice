"""Frigate replay selection policy tests. No network or HA install required."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "custom_components" / "kellies_voice" / "frigate_replay.py"
spec = importlib.util.spec_from_file_location("test_frigate_replay_policy", PATH)
assert spec and spec.loader
import sys
policy = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = policy
spec.loader.exec_module(policy)

NOW = 1_731_000_000
CAMERA = "front_door"


def event(eid="1730999999.123456-abcde1", camera=CAMERA, label="person",
          when=NOW-20, **extra):
    item = {
        "id": eid, "camera": camera, "label": label, "start_time": when,
        "end_time": when+10, "has_clip": True, "false_positive": False,
    }
    item.update(extra)
    return item


class FrigateReplaySelectionTests(unittest.TestCase):
    def recent(self, events, **kwargs):
        return policy.recent_recorded_events(
            events, frigate_camera=CAMERA, now=NOW, **kwargs
        )

    def test_realistic_id_and_camera_validation(self):
        self.assertTrue(policy.valid_event_id("1730999999.123456-abcde1"))
        self.assertTrue(policy.valid_camera("back_garage"))
        for bad in ("", "../etc/passwd", "https://example/clip.mp4",
                    "/api/events/123", "%2e%2e", "abc..def", "abc/def",
                    "abc?def", "abc#def", "abc\\def", "abc\nxyz",
                    "a", None, 123):
            self.assertFalse(policy.valid_event_id(bad), bad)
        for bad in ("", "../front", "front/door", "front.door", None,
                    "http://camera.local", "cam?token=secret"):
            self.assertFalse(policy.valid_camera(bad), bad)

    def test_returns_latest_completed_clips_for_only_mapped_camera(self):
        events = [
            event("1730999999.123456-abcde1", when=NOW-80),
            event("1730999999.123456-abcde2", camera="driveway", when=NOW-2),
            event("1730999999.123456-abcde3", when=NOW-50),
            event("1730999999.123456-abcde4", when=NOW-20),
        ]
        chosen = self.recent(events)
        self.assertEqual(
            ["1730999999.123456-abcde4", "1730999999.123456-abcde3",
             "1730999999.123456-abcde1"],
            [x.event_id for x in chosen],
        )
        self.assertEqual([CAMERA] * 3, [x.camera for x in chosen])

    def test_explicit_event_cannot_fall_back_to_wrong_clip(self):
        a = event("1730999999.123456-abcde1", camera=CAMERA)
        b = event("1730999999.123456-abcde2", camera="driveway")
        self.assertEqual([], self.recent([a, b],
                         requested_event_id="1730999999.123456-abcde2"))
        self.assertEqual([], self.recent([a],
                         requested_event_id="1730999999.123456-abcde9"))
        self.assertEqual("1730999999.123456-abcde1",
                         self.recent([a], requested_event_id=a["id"])[0].event_id)

    def test_missing_unfinished_false_positive_stale_and_invalid_media(self):
        sources = [
            event("1730999999.123456-aaaaa1", has_clip=False),
            event("1730999999.123456-aaaaa2", end_time=None),
            event("1730999999.123456-aaaaa3", false_positive=True),
            event("1730999999.123456-aaaaa4", when=NOW-100_000),
            event("1730999999.123456-aaaaa5", when=NOW+130),
            event("1730999999.123456-aaaaa6", start_time="yesterday"),
            event("1730999999.123456-aaaaa7", start_time=float("nan")),
            event("1730999999.123456-aaaaa8", label="http://evil/clip"),
            event("1730999999.123456-aaaaa9", has_clip=1),
        ]
        self.assertEqual([], self.recent(sources))
        self.assertEqual([], self.recent([event(end_time=NOW-100)]))

    def test_sanitized_preview_and_no_credential_data(self):
        source = event(label="package",
                       url="https://untrusted.invalid/token",
                       password="private",
                       thumbnail="base64-secret",
                       zones=["public", "private"])
        result = self.recent([source])
        self.assertEqual(1, len(result))
        self.assertEqual("package", result[0].label)
        self.assertEqual(
            {"event_id", "camera", "label", "started_at", "ended_at"},
            set(vars(result[0]).keys()),
        )
        self.assertNotIn("url", str(result[0]))
        self.assertNotIn("thumbnail", str(result[0]))

    def test_bounded_dedupe_and_rejects_invalid_input(self):
        duplicate = event()
        self.assertEqual(1, len(self.recent([duplicate, duplicate])))
        self.assertEqual(2, len(self.recent([
            duplicate, event("1730999999.123456-abcde2", when=NOW-25),
            event("1730999999.123456-abcde3", when=NOW-30)
        ], max_results=2)))
        for kwargs in ({"frigate_camera": "../bad"},
                       {"max_results": 0}, {"max_results": 11},
                       {"lookback_seconds": -1}, {"lookback_seconds": 999999999},
                       {"requested_event_id": "https://evil"}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                params = {"frigate_camera": CAMERA, "now": NOW, **kwargs}
                policy.recent_recorded_events([event()], **params)
        for broken in (None, {}, "[]", [event()] * 101):
            with self.assertRaises(ValueError):
                self.recent(broken)


if __name__ == "__main__":
    unittest.main()
