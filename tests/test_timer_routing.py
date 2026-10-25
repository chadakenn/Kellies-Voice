"""Regression tests for prototype mapping; requires no Home Assistant install."""

import importlib.util
import unittest
from pathlib import Path

_PATH = Path(__file__).parent.parent / "custom_components" / "kellies_voice" / "timer_routing.py"
_SPEC = importlib.util.spec_from_file_location("timer_routing", _PATH)
timer_routing = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(timer_routing)

NOW = 1_798_000_000_500  # Injected clock: no flakes from wall-time


class TimerRoutingTests(unittest.TestCase):
    def test_start_contains_android_required_deadline_and_freshness(self):
        payload = timer_routing.map_start(" tv-1 ", "Pizza", 300, now_ms=NOW)
        self.assertEqual("start", payload["operation"])
        self.assertEqual("tv-1", payload["device_id"])
        self.assertEqual("Pizza", payload["name"])
        self.assertEqual(300, payload["duration_seconds"])
        self.assertEqual(NOW // 1000, payload["issued"])
        self.assertEqual(NOW + 300000, payload["ends_at_ms"])
        self.assertFalse(payload["speak_alert"])

    def test_start_defaults_clamps_and_validates(self):
        self.assertEqual("Timer", timer_routing.map_start(
            "tv-1", None, 0, now_ms=NOW)["name"])
        self.assertEqual(1, timer_routing.map_start(
            "tv-1", None, 0, now_ms=NOW)["duration_seconds"])
        self.assertEqual(86400, timer_routing.map_start(
            "tv-1", "Long", 999999, now_ms=NOW)["duration_seconds"])
        for seconds in (1.3, "30", None, True):
            with self.subTest(seconds=seconds), self.assertRaises(ValueError):
                timer_routing.map_start("tv-1", "Timer", seconds, now_ms=NOW)

    def test_cancel_and_dismiss_have_required_freshness(self):
        cancelled = timer_routing.map_cancel("tv-1", "Pizza", now_ms=NOW)
        self.assertEqual("cancel", cancelled["operation"])
        self.assertEqual("Pizza", cancelled["name"])
        self.assertEqual(NOW // 1000, cancelled["issued"])
        dismissed = timer_routing.map_dismiss("tv-1", now_ms=NOW)
        self.assertEqual("dismiss", dismissed["operation"])
        self.assertEqual(NOW // 1000, dismissed["issued"])

    def test_empty_target_or_unsupported_name_does_not_broadcast(self):
        for device in ("", "   ", None):
            with self.subTest(device=device), self.assertRaises(ValueError):
                timer_routing.map_start(device, "Timer", 60, now_ms=NOW)
        for name in ("🥐", "a" * 41, "bad\nname", "-leading"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                timer_routing.map_start("tv-1", name, 60, now_ms=NOW)
        with self.assertRaises(ValueError):
            timer_routing.map_cancel(" ", "Timer", now_ms=NOW)

    def test_event_for_rejects_missing_target_or_issue_time(self):
        payload = timer_routing.map_start("tv-1", "Pizza", 60, now_ms=NOW)
        event_type, data = timer_routing.event_for(payload)
        self.assertEqual("kellies_voice_timer", event_type)
        self.assertEqual(payload, data)
        with self.assertRaises(ValueError):
            timer_routing.event_for({"operation": "start", "device_id": "tv-1"})
        with self.assertRaises(ValueError):
            timer_routing.event_for({"operation": "cancel", "issued": NOW // 1000})


if __name__ == "__main__":
    unittest.main()
