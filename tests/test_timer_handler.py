"""Safety tests for the native Assist timer bridge, without importing HA."""

import importlib.util
import pathlib
import sys
import types
import unittest
from types import SimpleNamespace

ROOT = pathlib.Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
pkg = sys.modules.setdefault("kellies_voice_timers_test", types.ModuleType("kellies_voice_timers_test"))
pkg.__path__ = [str(ROOT)]

def load(short_name):
    name = f"kellies_voice_timers_test.{short_name}"
    spec = importlib.util.spec_from_file_location(name, ROOT / f"{short_name}.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module

routing = load("timer_routing")
handler = load("timer_handler")


class TimerBridgeTests(unittest.TestCase):
    def setUp(self):
        self.events = []
        self.clock = [1_800_000_000_000]
        self.bridge = handler.TimerBridge(
            "ha-device-a", "paired-tv-a",
            lambda event, payload: self.events.append((event, payload)),
            now_ms=lambda: self.clock[0],
        )

    def timer(self, id="native-1", name="Pizza", seconds=300, active=True, device="ha-device-a"):
        return SimpleNamespace(
            id=id, name=name, seconds=600,
            seconds_left=seconds, is_active=active, device_id=device
        )

    def test_start_uses_remaining_seconds_and_pairs_exact_tv(self):
        self.bridge.handle(handler.STARTED, self.timer(seconds=298))
        self.assertEqual(1, len(self.events))
        payload = self.events[0][1]
        self.assertEqual("paired-tv-a", payload["device_id"])
        self.assertEqual(298, payload["duration_seconds"])
        self.assertEqual(self.clock[0] + 298000, payload["ends_at_ms"])
        self.assertTrue(payload["name"].startswith("HA Pizza "))
        self.assertLessEqual(len(payload["name"]), 40)
        self.bridge.handle(handler.STARTED, self.timer(seconds=294))
        self.assertEqual(1, len(self.events), "Duplicate start may not double-register")

    def test_updates_use_time_left_not_original(self):
        self.bridge.handle(handler.STARTED, self.timer())
        self.clock[0] += 4000
        self.bridge.handle(handler.UPDATED, self.timer(seconds=119))
        self.assertEqual(119, self.events[-1][1]["duration_seconds"])
        self.assertEqual(self.events[0][1]["name"], self.events[1][1]["name"])

    def test_pause_stops_countdown_and_unpause_restores_remaining(self):
        self.bridge.handle(handler.STARTED, self.timer(seconds=200))
        name = self.events[0][1]["name"]
        self.bridge.handle(handler.UPDATED, self.timer(seconds=180, active=False))
        self.assertEqual("cancel", self.events[-1][1]["operation"])
        self.assertEqual(name, self.events[-1][1]["name"])
        count = len(self.events)
        self.bridge.handle(handler.UPDATED, self.timer(seconds=150, active=False))
        self.assertEqual(count, len(self.events), "Paused time adjustment must not restart")
        self.bridge.handle(handler.UPDATED, self.timer(seconds=140, active=True))
        self.assertEqual("start", self.events[-1][1]["operation"])
        self.assertEqual(name, self.events[-1][1]["name"])
        self.assertEqual(140, self.events[-1][1]["duration_seconds"])

    def test_two_identically_named_timers_have_unique_slots(self):
        self.bridge.handle(handler.STARTED, self.timer("t1", name="Pizza"))
        self.bridge.handle(handler.STARTED, self.timer("t2", name="Pizza"))
        first, second = self.events[0][1]["name"], self.events[1][1]["name"]
        self.assertNotEqual(first, second)
        self.bridge.handle(handler.CANCELLED, self.timer("t1", name="Pizza"))
        self.assertEqual("cancel", self.events[2][1]["operation"])
        self.assertEqual(first, self.events[2][1]["name"])
        self.bridge.handle(handler.UPDATED, self.timer("t2", name="Pizza", seconds=12))
        self.assertEqual(second, self.events[-1][1]["name"])

    def test_paused_cancel_does_not_cancel_other_timer(self):
        self.bridge.handle(handler.STARTED, self.timer("t1"))
        self.bridge.handle(handler.STARTED, self.timer("t2"))
        self.bridge.handle(handler.UPDATED, self.timer("t1", active=False))
        count = len(self.events)
        self.bridge.handle(handler.CANCELLED, self.timer("t1", active=False, seconds=0))
        self.assertEqual(count, len(self.events))

    def test_finish_never_emits_a_duplicate_chime(self):
        self.bridge.handle(handler.STARTED, self.timer(seconds=5))
        self.bridge.handle(handler.FINISHED, self.timer(seconds=0, active=False))
        self.bridge.handle(handler.CANCELLED, self.timer(seconds=0))
        self.assertEqual(1, len(self.events))

    def test_cross_tv_event_is_ignored(self):
        self.bridge.handle(handler.STARTED, self.timer(device="other-device"))
        self.assertEqual([], self.events)
        self.bridge.handle(handler.CANCELLED, self.timer(device="other-device"))
        self.assertEqual([], self.events)

    def test_unicode_name_is_compatible_with_tv_name_schema(self):
        for idx, name in enumerate(("🥐💖", "Café", "a" * 100, "bad!@# name", None)):
            native_id = f"t{idx}"
            self.bridge.handle(handler.STARTED, self.timer(native_id, name))
            display = self.events[-1][1]["name"]
            self.assertLessEqual(len(display), 40)
            self.assertTrue(routing._NAME.fullmatch(display))

    def test_bad_events_cannot_break_native_timer_manager(self):
        self.bridge.handle(handler.STARTED, self.timer("", seconds=30))
        self.bridge.handle(handler.STARTED, self.timer("invalid", seconds=-5))
        self.bridge.handle(handler.STARTED, self.timer("oversized", seconds=86401))
        self.bridge.handle(handler.STARTED, self.timer("malformed", seconds="300"))
        self.assertEqual([], self.events)

    def test_two_tvs_remain_isolated(self):
        b = handler.TimerBridge(
            "ha-device-b", "paired-tv-b",
            lambda event, payload: self.events.append((event, payload)),
            now_ms=lambda: self.clock[0],
        )
        self.bridge.handle(handler.STARTED, self.timer())
        b.handle(handler.STARTED, self.timer(device="ha-device-b", id="native-2"))
        self.assertEqual(
            {"paired-tv-a", "paired-tv-b"},
            {payload["device_id"] for _, payload in self.events},
        )

    def test_unregister_clears_bridge_and_does_not_cancel_tv_timer(self):
        registered = {}
        registry = handler.TimerHandlerRegistry()
        def install(device, callback):
            registered[device] = callback
            return lambda: registered.pop(device)
        registry.enable(self.bridge, install)
        self.assertEqual(["ha-device-a"], registry.enabled_ids)
        registered["ha-device-a"](handler.STARTED, self.timer())
        registry.disable_all()
        self.assertEqual([], registry.enabled_ids)
        self.assertEqual({}, registered)
        self.assertEqual(1, len(self.events))
        self.bridge.handle(handler.CANCELLED, self.timer())
        self.assertEqual(1, len(self.events))

    def test_registration_requires_distinct_identifiers(self):
        with self.assertRaises(ValueError):
            handler.TimerBridge("same", "same", lambda *a: None)

    def test_native_bus_failure_does_not_escape_handler(self):
        bridge = handler.TimerBridge(
            "ha-device-a", "paired-tv-a", lambda *_: 1 / 0,
            now_ms=lambda: self.clock[0],
        )
        bridge.handle(handler.STARTED, self.timer())
        bridge.handle(handler.CANCELLED, self.timer())


if __name__ == "__main__":
    unittest.main()
