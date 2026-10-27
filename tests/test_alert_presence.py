"""Offline tests for event receiver presence; no Home Assistant required."""
import ast
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"


class ReceiverPresenceTests(unittest.TestCase):
    def setUp(self):
        source = ast.parse((ROOT / "alert_presence.py").read_text(encoding="utf-8"))
        cls = next(x for x in source.body if isinstance(x, ast.ClassDef)
                   and x.name == "PresenceTracker")
        from datetime import datetime, timezone
        namespace = {"datetime": datetime, "timezone": timezone}
        exec(compile(ast.Module(body=[cls], type_ignores=[]), "presence", "exec"), namespace)
        self.tracker = namespace["PresenceTracker"]()

    def test_pairing_alone_is_unverified(self):
        self.assertFalse(self.tracker.online("mistyped"))
        self.assertIsNone(self.tracker.last_seen("mistyped"))

    def test_real_connection_then_disconnect_preserves_last_seen(self):
        token = object()
        self.assertTrue(self.tracker.connected("real-tv", token))
        self.assertTrue(self.tracker.online("real-tv"))
        self.assertIsNotNone(self.tracker.last_seen("real-tv"))
        self.assertTrue(self.tracker.disconnected("real-tv", token))
        self.assertFalse(self.tracker.online("real-tv"))
        self.assertIsNotNone(self.tracker.last_seen("real-tv"))

    def test_multiple_tvs_and_same_tv_multiple_connections(self):
        a, b, c = object(), object(), object()
        self.tracker.connected("tv-1", a)
        self.tracker.connected("tv-1", b)
        self.tracker.connected("tv-2", c)
        self.assertFalse(self.tracker.disconnected("tv-1", a))
        self.assertTrue(self.tracker.online("tv-1"))
        self.assertTrue(self.tracker.online("tv-2"))
        self.assertTrue(self.tracker.disconnected("tv-1", b))
        self.assertFalse(self.tracker.online("tv-1"))
        self.assertTrue(self.tracker.online("tv-2"))
        self.assertFalse(self.tracker.disconnected("tv-1", a))


if __name__ == "__main__":
    unittest.main()
