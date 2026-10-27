"""Offline, session-scoped popup diagnostic tests (no HA installed)."""

import importlib.util
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
sys.modules.setdefault("custom_components", types.ModuleType("custom_components"))
pkg = types.ModuleType("custom_components.kellies_voice")
pkg.__path__ = [str(ROOT)]
sys.modules["custom_components.kellies_voice"] = pkg

for part in ("const", "alert_diagnostics"):
    spec = importlib.util.spec_from_file_location(
        f"custom_components.kellies_voice.{part}", ROOT / f"{part}.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)

from custom_components.kellies_voice.alert_diagnostics import TvDiagnosticTracker


class TvDiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.time = [100.0]
        self.tracker = TvDiagnosticTracker(clock=lambda: self.time[0])
        self.sock1, self.sock2 = object(), object()
        self.s1, self.s2 = object(), object()
        self.tracker.subscribe("tv-1", self.s1, self.sock1)
        self.tracker.subscribe("tv-2", self.s2, self.sock2)

    def test_unpaired_and_wrong_socket_cannot_spoof_report(self):
        receipt = self.tracker.issue("tv-1", self.s1, "kellies_voice_announcement")
        self.assertEqual(24, len(receipt))
        self.assertFalse(self.tracker.report("tv-1", self.sock2, "received", receipt=receipt))
        self.assertFalse(self.tracker.report("tv-2", self.sock2, "received", receipt=receipt))
        self.assertFalse(self.tracker.report("mistyped", self.sock1, "hello",
                                             version="0.16.12", code=50))
        self.assertTrue(self.tracker.report("tv-1", self.sock1, "received", receipt=receipt))
        self.assertEqual("Received by TV", self.tracker.snapshot("tv-1")["last_result"])
        self.assertEqual({}, self.tracker.snapshot("tv-2"))

    def test_received_then_created_but_never_claims_display_pixels(self):
        receipt = self.tracker.issue("tv-1", self.s1, "kellies_voice_live_camera")
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "popup_created", receipt=receipt))
        self.assertTrue(self.tracker.report("tv-1", self.sock1, "received", receipt=receipt))
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "received", receipt=receipt))
        self.assertTrue(self.tracker.report("tv-1", self.sock1, "popup_created", receipt=receipt))
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "popup_created", receipt=receipt))
        row = self.tracker.snapshot("tv-1")
        self.assertEqual("Popup created on TV", row["last_result"])
        self.assertEqual("live camera", row["last_kind"])
        self.assertIn("last_received_at", row)
        self.assertIn("last_popup_created_at", row)
        self.assertNotIn("last_text", row)
        self.assertNotIn("camera_id", row)

    def test_nonce_expires_and_cannot_survive_socket_loss(self):
        expired = self.tracker.issue("tv-1", self.s1, "kellies_voice_announcement")
        self.time[0] += 120
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "received", receipt=expired))
        fresh = self.tracker.issue("tv-1", self.s1, "kellies_voice_camera_popup")
        self.tracker.unsubscribe("tv-1", self.s1)
        self.assertFalse(self.tracker.active("tv-1", self.sock1))
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "received", receipt=fresh))
        self.assertTrue(self.tracker.active("tv-2", self.sock2))

    def test_version_only_reports_from_connected_socket(self):
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "hello",
                                             version="invalid", code=50))
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "hello",
                                             version="0.16.12", code=True))
        self.assertFalse(self.tracker.report("tv-1", self.sock1, "hello",
                                             version="0.16.12-dev.12", code=0))
        self.assertTrue(self.tracker.report("tv-1", self.sock1, "hello",
                                            version="0.16.12-dev.12", code=52))
        self.assertEqual(52, self.tracker.snapshot("tv-1")["app_code"])
        self.assertEqual("0.16.12-dev.12", self.tracker.snapshot("tv-1")["app_version"])
        self.assertFalse(self.tracker.report("tv-2", self.sock1, "hello",
                                             version="0.16.12", code=50))

    def test_bounds_and_forbidden_nonvisual_event_types(self):
        self.assertIsNone(self.tracker.issue("tv-1", self.s1, "kellies_voice_hide_camera"))
        self.assertIsNone(self.tracker.issue("tv-1", self.s1, "kellies_voice_timer"))
        for _ in range(50):
            self.tracker.issue("tv-1", self.s1, "kellies_voice_announcement")
        pending = self.tracker._sessions["tv-1"][self.s1][1]
        self.assertEqual(16, len(pending))


if __name__ == "__main__":
    unittest.main()
