"""Static regression checks for v0.17.4 scoped dashboard controls.

Android/HACS runtime behavior must additionally be checked on physical TVs.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
HA = ROOT / "custom_components/kellies_voice"


class DashboardActionSourceTests(unittest.TestCase):
    def test_ha_actions_target_one_tv_without_forwarding_urls(self):
        services = (HA / "services.py").read_text()
        transport = (HA / "alert_transport.py").read_text()
        const = (HA / "const.py").read_text()
        self.assertIn('vol.Required("device_id"): cv.string', services)
        self.assertIn("SERVICE_SHOW_DASHBOARD", services)
        self.assertIn("SERVICE_HIDE_DASHBOARD", services)
        self.assertIn('_event_targets(call, requested)', services)
        self.assertIn('EVENT_SHOW_DASHBOARD: ("device_id",)', transport)
        self.assertIn('EVENT_HIDE_DASHBOARD: ("device_id",)', transport)
        self.assertIn('SERVICE_SHOW_DASHBOARD = "show_dashboard"', const)
        self.assertIn('SERVICE_HIDE_DASHBOARD = "hide_dashboard"', const)
        section = services.split("async def _async_show_dashboard(", 1)[1].split(
            "def _saved_weather_entity", 1
        )[0]
        self.assertNotIn('call.data.get("url")', section)
        self.assertNotIn("access_token", section)

    def test_android_commands_use_scoped_receiver_and_existing_overlay(self):
        events = (APP / "TvEventsService.java").read_text()
        watch = (APP / "ScreensaverWatchService.java").read_text()
        self.assertIn('subscription == SCOPED_ALERTS_ID\n                    && (TvEventPayload.SHOW_DASHBOARD', events)
        self.assertIn("ScreensaverWatchService.ACTION_SHOW_DASHBOARD", events)
        self.assertIn("ScreensaverWatchService.ACTION_HIDE_DASHBOARD", events)
        self.assertIn("if (onDemand) hide();", watch)
        self.assertIn("if (!ScreensaverSignals.ourDream() && board == null)", watch)
        self.assertIn("showIfWanted(true)", watch)
        self.assertIn("Settings.canDrawOverlays(this)", watch)
        self.assertNotIn("settings put system screen_off_timeout", watch)

    def test_ha_loading_hides_login_until_saved_path_is_ready(self):
        board = (APP / "ScreensaverBoard.java").read_text()
        policy = (APP / "ScreensaverDashboard.java").read_text()
        self.assertIn("dashboardLoading = cover", board)
        self.assertIn("dashboardReadyScript(expectedPage)", board)
        self.assertIn("dashboardReadySinceMs", board)
        self.assertIn("dashboardLoading.setVisibility(View.GONE)", board)
        self.assertIn("location.pathname", policy)
        self.assertIn("document.readyState", policy)
        self.assertIn("document.querySelector('home-assistant')", policy)
        self.assertNotIn("addJavascriptInterface(", board)


if __name__ == "__main__":
    unittest.main()
