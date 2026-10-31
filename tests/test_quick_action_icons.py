"""TV icon contract: local MDI names only, strict overrides and no URL leaks."""
import importlib.util
from pathlib import Path
import sys
import unittest

COMP = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
spec = importlib.util.spec_from_file_location("quick_action_icons_test", COMP / "quick_action_icons.py")
assert spec and spec.loader
icons = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = icons
spec.loader.exec_module(icons)


class IconContractTests(unittest.TestCase):
    def test_home_assistant_mdi_icon_wins_in_auto(self):
        self.assertEqual("mdi:ceiling-light",
                         icons.resolve_shortcut_icon("Auto", "mdi:ceiling-light", "light"))
        self.assertEqual("mdi:movie-open",
                         icons.resolve_shortcut_icon("Auto", None, "scene"))

    def test_ha_user_override_wins_over_entity_icon(self):
        self.assertEqual("mdi:fan",
                         icons.resolve_shortcut_icon("Fan", "mdi:lightbulb", "light"))
        self.assertEqual("mdi:garage",
                         icons.resolve_shortcut_icon("Garage", "mdi:star", "script"))

    def test_reject_images_urls_scripts_and_unsafe_payloads(self):
        for value in ("https://x.example/icon.png", "<img>", "javascript:alert(1)",
                      "mdi:abc/def", "mdi:" + "a" * 100, "mdi:icon_here",
                      "mdi:ICON", "\nmdi:lightbulb", None):
            with self.subTest(value=value):
                self.assertEqual(
                    "mdi:lightbulb",
                    icons.resolve_shortcut_icon("Auto", value, "light")
                )
        for value in (None, "", "mdi:custom", "anything", "Unknown"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                icons.validate_override(value)

    def test_all_approved_choices_yield_safe_mdi(self):
        for value in icons.ICON_CHOICES:
            icon = icons.resolve_shortcut_icon(
                icons.validate_override(value), "mdi:ceiling-light", "light"
            )
            self.assertRegex(icon, r"^mdi:[a-z][a-z0-9-]+$")
