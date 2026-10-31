"""Regression: Home Assistant style choices must match the six Compose skin IDs."""
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
    def test_all_six_styles_are_exposed(self):
        self.assertEqual(
            const.STYLES,
            ["Copper Glass", "Compact Charcoal", "Night Signal",
             "Aurora Glass", "Neon Obsidian", "Jade Terminal"],
        )
        controls = {key: options for key, _title, options, _icon in const.SELECTS}
        self.assertEqual(controls["style"], const.STYLES)

    def test_java_can_publish_and_read_new_skins(self):
        prefs = (ANDROID / "OverlayPreferences.java").read_text(encoding="utf-8")
        layout = (ANDROID / "HaLayout.java").read_text(encoding="utf-8")
        self.assertIn('return "Neon Obsidian"', prefs)
        self.assertIn('case "Neon Obsidian":', layout)
        self.assertIn('return "Jade Terminal"', prefs)
        self.assertIn('case "Jade Terminal":', layout)
        self.assertIn('SKIN_TERMINAL.equals(value)', prefs)
        # Community themes resolve their names dynamically; the six built-in
        # labels remain available through the explicit fallback.
        self.assertIn("return skinLabelFor(prefs);", layout)
        self.assertIn("private static String skinLabelFor(OverlayPreferences prefs)", layout)
        self.assertIn("return OverlayPreferences.skinLabel(id);", layout)

    def test_compose_and_overlay_select_jade_terminal(self):
        compose = (ANDROID / "ui/settings/ComposeSettingsActivity.kt").read_text(encoding="utf-8")
        theme = (ANDROID / "ui/theme/KellieTheme.kt").read_text(encoding="utf-8")
        voice = (ANDROID / "VoiceOverlayService.java").read_text(encoding="utf-8")
        wave = (ANDROID / "VoiceWaveView.java").read_text(encoding="utf-8")
        self.assertIn("KellieSkins.ALL.forEach", compose)
        self.assertIn("val ALL = listOf(COPPER, MINIMAL, AURORA, NIGHT, OBSIDIAN, TERMINAL)", theme)
        self.assertIn("LOOK_TERMINAL", voice)
        self.assertIn("terminal ? theme.accent", voice)
        self.assertIn("LOOK_TERMINAL = 4", wave)


if __name__ == "__main__":
    unittest.main()
