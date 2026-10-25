"""Tests for the opt-in native timer bridge. No Home Assistant required."""

import importlib.util
import unittest
from pathlib import Path
from types import SimpleNamespace


import sys
_PKG = Path(__file__).parent.parent / "custom_components" / "kellies_voice"
sys.path.insert(0, str(_PKG))

def _load(name):
    path = _PKG / f"{name}.py"
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

routing = _load("timer_routing")
handler_mod = _load("timer_handler")
TimerBridge = handler_mod.TimerBridge
TimerHandlerRegistry = handler_mod.TimerHandlerRegistry


class FakeRegister:
    """Simulates async_register_timer_handler return value."""

    def __init__(self):
        self.registered = {}

    def __call__(self, device_id, callback):
        self.registered[device_id] = callback
        def unregister():
            self.registered.pop(device_id, None)
        return unregister


class TimerHandlerTests(unittest.TestCase):
    def setUp(self):
        self.events = []
        self.clock = [1_798_000_000_000]
        self.reg = FakeRegister()
        self.registry = TimerHandlerRegistry()

    def _bridge(self, registry_id, pairing_id):
        return TimerBridge(
            registry_id, pairing_id,
            fire=lambda et, data: self.events.append((et, data)),
            now_ms=lambda: self.clock[0],
        )

    def test_rejects_identical_ids(self):
        with self.assertRaises(ValueError):
            TimerBridge("same", "same", fire=lambda *a: None)

    def test_start_cancel_update_and_ignore_finished(self):
        bridge = self._bridge("ha-reg-1", "pairing-uuid-1")
        self.registry.enable(bridge, self.reg)
        self.assertTrue(self.registry.is_enabled("ha-reg-1"))

        info = SimpleNamespace(id="t1", name="Pizza", seconds=300)
        bridge.handle("started", info)
        self.assertEqual(1, len(self.events))
        self.assertEqual("start", self.events[0][1]["operation"])
        self.assertEqual("pairing-uuid-1", self.events[0][1]["device_id"])
        self.assertEqual("Pizza", self.events[0][1]["name"])

        # Update replaces; no second independent countdown.
        self.clock[0] += 1000
        info.seconds = 240
        bridge.handle("updated", info)
        self.assertEqual(2, len(self.events))
        self.assertEqual("start", self.events[1][1]["operation"])
        self.assertEqual(240, self.events[1][1]["duration_seconds"])

        # Finished does not emit (TV owns the chime).
        bridge.handle("finished", info)
        self.assertEqual(2, len(self.events))

        # Stale cancel after finish is ignored.
        bridge.handle("cancelled", info)
        self.assertEqual(2, len(self.events))

    def test_stale_cancel_from_unknown_id_is_ignored(self):
        bridge = self._bridge("ha-reg-1", "pairing-uuid-1")
        unknown = SimpleNamespace(id="old", name="Pizza", seconds=60)
        bridge.handle("cancelled", unknown)
        self.assertEqual([], self.events)

    def test_two_tvs_are_isolated(self):
        a = self._bridge("ha-reg-a", "pair-a")
        b = self._bridge("ha-reg-b", "pair-b")
        self.registry.enable(a, self.reg)
        self.registry.enable(b, self.reg)
        a.handle("started", SimpleNamespace(id="ta", name="Pizza", seconds=60))
        b.handle("started", SimpleNamespace(id="tb", name="Laundry", seconds=120))
        targets = {e[1]["device_id"] for e in self.events}
        self.assertEqual({"pair-a", "pair-b"}, targets)
        # Cancel on A does not affect B.
        a.handle("cancelled", SimpleNamespace(id="ta", name="Pizza", seconds=0))
        cancelled = [e for e in self.events if e[1]["operation"] == "cancel"]
        self.assertEqual(1, len(cancelled))
        self.assertEqual("pair-a", cancelled[0][1]["device_id"])

    def test_unregister_on_unload_and_reconnect(self):
        bridge = self._bridge("ha-reg-1", "pairing-uuid-1")
        self.registry.enable(bridge, self.reg)
        self.assertIn("ha-reg-1", self.reg.registered)
        self.registry.disable_all()
        self.assertEqual([], self.registry.enabled_ids)
        self.assertNotIn("ha-reg-1", self.reg.registered)
        # Reconnect: enable again.
        self.registry.enable(bridge, self.reg)
        self.assertTrue(self.registry.is_enabled("ha-reg-1"))

    def test_expiration_does_not_double_ring(self):
        bridge = self._bridge("ha-reg-1", "pairing-uuid-1")
        info = SimpleNamespace(id="t1", name="Timer", seconds=5)
        bridge.handle("started", info)
        self.clock[0] += 5000
        bridge.handle("finished", info)
        # Only the original start event; no extra ring or dismiss.
        self.assertEqual(1, len(self.events))
        self.assertEqual("start", self.events[0][1]["operation"])


if __name__ == "__main__":
    unittest.main()
