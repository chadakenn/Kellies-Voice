"""The release APK must offer a theme install path on Android TV without DocumentsUI.

These checks protect the bundled sample list, source JSON parity, and the
app-owned ADB import fallback. Real TV focus/Android picker behavior still
requires hardware QA.
"""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SAMPLES = (
    "midnight-ocean.json",
    "warm-ember.json",
    "forest-canopy.json",
    "twilight-violet.json",
    "soft-slate.json",
    "example-copper-night.json",
)


class PickerlessThemeTests(unittest.TestCase):
    def test_sample_json_identical_to_public_community_files(self):
        for name in SAMPLES:
            with self.subTest(name=name):
                original = ROOT / "themes" / "community" / name
                bundled = ROOT / "app" / "src" / "main" / "assets" / "community-themes" / name
                self.assertTrue(bundled.is_file(), f"Missing APK theme: {name}")
                self.assertEqual(bundled.read_bytes(), original.read_bytes())
                doc = json.loads(bundled.read_text(encoding="utf-8"))
                self.assertEqual(doc["format"], 1)
                self.assertTrue(doc["id"].startswith("community."))
                self.assertLessEqual(bundled.stat().st_size, 16 * 1024)

    def test_no_picker_flow_and_adb_scoped_storage_fallback(self):
        ui = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/ui/settings/"
              "ComposeSettingsActivity.kt").read_text(encoding="utf-8")
        store = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/"
                 "CommunityThemeStore.java").read_text(encoding="utf-8")
        for name in SAMPLES:
            self.assertIn(f'"{name}"', ui)
        self.assertIn("Browse included community themes (6)", ui)
        self.assertIn("ActivityNotFoundException", ui)
        self.assertIn("importTheme(context, stream)", ui)
        self.assertIn("Scan ADB theme import folder", ui)
        self.assertIn("importDroppedTheme(context, fileName)", ui)
        self.assertIn('getExternalFilesDir(null)', store)
        self.assertIn('new File(external, "theme-import")', store)
        self.assertIn("getCanonicalFile()", store)
        self.assertNotIn("MANAGE_EXTERNAL_STORAGE", ui)


if __name__ == "__main__":
    unittest.main()
