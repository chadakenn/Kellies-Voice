"""Local HTTP viewer regression tests. No credentials, HA instance or TV needed."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
HA = ROOT / "custom_components/kellies_voice"

def source(n):
    return (SRC / n).read_text()


class LanHttpViewerGuardTests(unittest.TestCase):
    def test_off_by_default_and_never_grants_ha_token(self):
        prefs = source("OverlayPreferences.java")
        self.assertIn('KEY_SCREENSAVER_LAN_HTTP = "screensaver_lan_http_only"', prefs)
        self.assertIn('return preferences.getBoolean(KEY_SCREENSAVER_LAN_HTTP, false)', prefs)
        board = source("ScreensaverBoard.java")
        self.assertIn("prefs.screensaverLanHttpOnly()", board)
        self.assertIn('LanHttpDashboardPolicy.isStrictLanHttp(page)', board)
        self.assertIn('else showPage(page, null)', board)
        self.assertIn('if (prefs.screensaverHaExternalAuth()) showAuthenticatedHaPage(page)', board)
        policy = source("LanHttpDashboardPolicy.java")
        self.assertNotIn("access_token", policy)
        self.assertNotIn("refresh_token", policy)
        self.assertNotIn("CredentialStore", policy)
        self.assertNotIn("TvRenewableLoginVault", policy)

    def test_webview_filters_best_effort_without_insecure_auth_fallback(self):
        board = source("ScreensaverBoard.java")
        self.assertIn("strictLocalHttp", board)
        self.assertIn("LanHttpDashboardPolicy.allowedNavigation(page, next)", board)
        self.assertIn("new WebResourceResponse(", board)
        self.assertIn("LanHttpDashboardPolicy.allowedNavigation(page, request.getUrl().toString())", board)
        self.assertNotIn("setWebContentsDebuggingEnabled", board)
        self.assertNotIn("addJavascriptInterface(", board)

    def test_ha_switch_and_tv_setting_sync_same_per_device_route(self):
        switches = (HA / "switch.py").read_text()
        self.assertIn("KellieLanHttpViewerSwitch(entry)", switches)
        self.assertIn('self._setting = "screensaver_lan_http_only"', switches)
        self.assertIn('self._attr_unique_id = f"{self._device_id}_screensaver_lan_http_only"', switches)
        self.assertIn("self._attr_is_on = False", switches)
        layout = source("HaLayout.java")
        self.assertIn('SCREENSAVER_LAN_HTTP = "screensaver_lan_http_only"', layout)
        self.assertIn("SCREENSAVER_LAN_HTTP.equals(setting)", layout)
        self.assertIn('return prefs.screensaverLanHttpOnly() ? "on" : "off";', layout)
        self.assertIn("prefs.setScreensaverLanHttpOnly(privateLanViewer)", layout)
        compose = source("ui/settings/ComposeSettingsActivity.kt")
        self.assertIn('title = "Restrict local HTTP dashboard to private LAN address"', compose)
        self.assertIn("prefs.setScreensaverLanHttpOnly(enabled)", compose)
        self.assertIn("HaLayout.SCREENSAVER_LAN_HTTP", compose)


if __name__ == "__main__":
    unittest.main()
