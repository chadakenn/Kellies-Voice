"""Community theme colors must reach the Android TV main screen, floating
voice overlay and Home Assistant camera / forecast popup surfaces.
Offline regression: no device, credentials or document picker needed.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe"


def src(name):
    return (ROOT / name).read_text()


class CommunityThemePaletteRouteTests(unittest.TestCase):
    def test_main_tv_uses_context_for_theme_file_lookup(self):
        self.assertIn(
            "return AppPalette.forSkin(this, new OverlayPreferences(this).skin());",
            src("VoiceActivity.java"))

    def test_voice_overlay_uses_context_aware_theme_palette(self):
        self.assertEqual(src("VoiceOverlayService.java").count(
            "AppPalette.forSkin(this, preferences.skin())"), 2)

    def test_tv_alert_and_forecast_share_community_colors(self):
        popup = src("TvPopupPalette.java")
        alerts = src("TvEventsService.java")
        self.assertIn("forSkin(android.content.Context context, String skin)", popup)
        self.assertIn("CommunityThemeStore.get(context, skin)", popup)
        self.assertIn("AppPalette.forSkin(context, skin)", popup)
        self.assertIn("return forSkin(skin)", popup)  # Existing built-in look remains.
        self.assertEqual(alerts.count(
            "TvPopupPalette.forSkin(this, placement.skin())"), 2)


if __name__ == "__main__":
    unittest.main()
