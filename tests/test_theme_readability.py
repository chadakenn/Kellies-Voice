"""Prevent TV theme regressions that make D-pad focus or text unreadable.

Source-level Compose palette test: native Android/Compose tests also compile and
exercise theme mappings, but contrast is cheap to verify on every PR without a TV.
These checks are not a substitute for an actual 10-foot display/accessibility QA.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/ui/theme/KellieTheme.kt").read_text(
    encoding="utf-8"
)
EXPECTED = ("COPPER", "MINIMAL", "AURORA", "NIGHT", "OBSIDIAN", "TERMINAL")


def rgb(color: str) -> list[float]:
    value = int(color[-6:], 16)
    return [((value >> shift) & 255) / 255.0 for shift in (16, 8, 0)]


def luminance(color: str) -> float:
    parts = [channel / 12.92 if channel <= 0.04045 else
             ((channel + 0.055) / 1.055) ** 2.4 for channel in rgb(color)]
    return parts[0] * 0.2126 + parts[1] * 0.7152 + parts[2] * 0.0722


def contrast(color: str, background: str) -> float:
    a, b = luminance(color), luminance(background)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def palette(name: str) -> dict[str, str]:
    pattern = r"val " + re.escape(name) + r" = KellieSkin\("
    match = re.search(pattern, SOURCE)
    if not match:
        raise AssertionError("Skin not in Kotlin catalog: " + name)
    block = SOURCE[match.end():]
    # The colors block must end before the next typography definition.
    block = block.split("typography = Typography()", 1)[0]
    return dict(re.findall(r"([a-zA-Z]+)\s*=\s*Color\(0x([0-9a-fA-F]{8})\)", block))


class TvThemeContrastTests(unittest.TestCase):
    def test_all_six_skins_have_legible_text_and_visible_focus(self):
        for name in EXPECTED:
            with self.subTest(skin=name):
                colors = palette(name)
                for key in ("panel", "text", "textMuted", "focus", "cardFocused"):
                    self.assertIn(key, colors)
                    self.assertEqual("FF", colors[key][:2].upper(),
                                     name + " " + key + " must be fully opaque")
                self.assertGreaterEqual(
                    contrast(colors["text"], colors["panel"]), 7.0,
                    name + " ordinary text on settings panel should be AAA readable"
                )
                self.assertGreaterEqual(
                    contrast(colors["textMuted"], colors["panel"]), 4.5,
                    name + " secondary text on settings panel is too faint"
                )
                self.assertGreaterEqual(
                    contrast(colors["focus"], colors["cardFocused"]), 3.0,
                    name + " remote-focus border is not distinguishable"
                )

    def test_editor_text_and_cursor_follow_skin_instead_of_default_copper(self):
        compose = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/ui/settings/"
                   "ComposeSettingsActivity.kt").read_text(encoding="utf-8")
        for attr in ("focusedTextColor = skin.colors.text",
                     "unfocusedTextColor = skin.colors.text",
                     "focusedContainerColor = skin.colors.card",
                     "unfocusedContainerColor = skin.colors.card",
                     "focusedBorderColor = skin.colors.focus",
                     "cursorColor = skin.colors.accent"):
            with self.subTest(attr=attr):
                self.assertGreaterEqual(compose.count(attr), 2,
                    "Both Frigate URL and camera entity editors must be themed")


if __name__ == "__main__":
    unittest.main()
