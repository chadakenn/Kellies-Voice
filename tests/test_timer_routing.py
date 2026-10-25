"""Unit tests for the timer routing prototype. No Home Assistant required."""

import importlib.util
import unittest
from pathlib import Path

_PATH = Path(__file__).parent.parent / "custom_components" / "kellies_voice" / "timer_routing.py"
_SPEC = importlib.util.spec_from_file_location("timer_routing", _PATH)
timer_routing = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(timer_routing)


class TimerRoutingTests(unittest.TestCase):
    def test_start_defaults_and_clamps(self):
        payload = timer_routing.map_start("tv-1", None, 0)
        self.assertEqual(payload["name"], "Timer")
        self.assertEqual(payload["duration_seconds"], 1)
        self.assertEqual(payload["device_id"], "tv-1")

        payload = timer_routing.map_start("tv-1", "Pizza", 300)
        self.assertEqual(payload["name"], "Pizza")
        self.assertEqual(payload["duration_seconds"], 300)

        payload = timer_routing.map_start("tv-1", "Long", 999999)
        self.assertEqual(payload["duration_seconds"], 86400)

    def test_cancel_and_dismiss(self):
        self.assertEqual(timer_routing.map_cancel("tv-1", "Pizza")["operation"], "cancel")
        self.assertEqual(timer_routing.map_cancel("tv-1", None)["name"], "Timer")
        self.assertEqual(timer_routing.map_dismiss("tv-1")["operation"], "dismiss")

    def test_event_for(self):
        event_type, data = timer_routing.event_for(timer_routing.map_start("tv-1", "Pizza", 60))
        self.assertEqual(event_type, "kellies_voice_timer")
        self.assertEqual(data["name"], "Pizza")


if __name__ == "__main__":
    unittest.main()
