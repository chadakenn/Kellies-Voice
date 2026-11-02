"""Source-level guardrails for the local-only Android TV cat Easter egg."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main"
CAT = APP / "java/com/example/tvvoiceprobe/EasterEggActivity.java"
SETTINGS = APP / "java/com/example/tvvoiceprobe/ui/settings/ComposeSettingsActivity.kt"
MANIFEST = APP / "AndroidManifest.xml"


class CatEasterEggTests(unittest.TestCase):
    def test_hidden_version_five_press_trigger_only(self):
        settings = SETTINGS.read_text()
        self.assertIn("versionPresses >= 5", settings)
        self.assertIn("SettingsChoice(", settings)
        self.assertIn('title = "Installed version:', settings)
        self.assertIn("EasterEggActivity::class.java", settings)
        self.assertNotIn("HaAssistClient", CAT.read_text())

    def test_own_private_fullscreen_activity_without_overlay_services(self):
        manifest = MANIFEST.read_text()
        self.assertRegex(
            manifest,
            r'<activity android:name="\.EasterEggActivity"\s+'
            r'android:exported="false"\s+'
            r'android:excludeFromRecents="true"',
        )
        code = CAT.read_text()
        for token in ("Canvas", "postInvalidateOnAnimation", "FLAG_FULLSCREEN",
                      "void pause()", "void resume()", "drawCat(", "updateConfetti("):
            self.assertIn(token, code)
        self.assertIn("return super.onKeyUp(code, event);", code)
        for forbidden in ("WebSocket", "http://", "https://", "Microphone",
                          "AudioRecord", "MediaPlayer", "startService(",
                          "startForegroundService(", "Settings.canDrawOverlays"):
            self.assertNotIn(forbidden, code)

    def test_party_has_both_modes_and_no_persistent_state(self):
        code = CAT.read_text()
        self.assertIn("mode = (mode + 1) % 2;", code)
        self.assertIn("CAT-ASTROPHIC DIAGNOSTICS", code)
        self.assertIn("CONFETTI CONTAINMENT FAILURE", code)
        self.assertNotIn("SharedPreferences", code)
        self.assertNotIn("Thread.sleep", code)


if __name__ == "__main__":
    unittest.main()
