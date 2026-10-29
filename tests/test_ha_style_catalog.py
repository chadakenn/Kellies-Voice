"""Regression: Home Assistant style choices must match the five Compose skin IDs."""
from pathlib import Path
import importlib.util
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONST = ROOT / "custom_components" / "kellies_voice" / "const.py"
ANDROID = ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "tvvoiceprobe"
spec = importlib.util.spec_from_file_location("kellies_voice_style_const", CONST)
assert spec is not None and spec.loader is not None
const = importlib.util.module_from_spec(spec)
spec.loader.exec_module(const)


class TestHaStyleCatalog(unittest.TestCase):
    def test_all_five_styles_are_exposed(self):
        self.assertEqual(
            const.STYLES,
            ["Copper Glass", "Compact Charcoal", "Night Signal",
             "Aurora Glass", "Neon Obsidian"],
        )
        controls = {key: options for key, _title, options, _icon in const.SELECTS}
        self.assertEqual(controls["style"], const.STYLES)

    def test_java_can_publish_and_read_neon_obsidian(self):
        prefs = (ANDROID / "OverlayPreferences.java").read_text(encoding="utf-8")
        layout = (ANDROID / "HaLayout.java").read_text(encoding="utf-8")
        self.assertIn('return "Neon Obsidian"', prefs)
        self.assertIn('case "Neon Obsidian":', layout)
        self.assertIn('return OverlayPreferences.skinLabel(prefs.skin())', layout)


if __name__ == "__main__":
    unittest.main()
