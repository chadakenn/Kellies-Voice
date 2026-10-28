"""Guard the TV Settings menu entry-to-action contract.

This is a source-level regression test because VoiceActivity requires Android
UI classes to instantiate; the Android build also compiles the real source.
Adding a new row without shifting its switch case must not silently open
another settings page (as happened with Follow-up listening in dev.6).
"""

from pathlib import Path
import re
import unittest


ACTIVITY = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/com/example/tvvoiceprobe/VoiceActivity.java"
).read_text(encoding="utf-8")

EXPECTED_CATEGORIES = (
    "HOME", "VOICE", "OVERLAY", "ALERTS", "SCREEN", "SYSTEM",
)

# These are existing entry points. Redesign must not silently orphan a setting.
EXPECTED_ACTIONS = {
    "HOME": ("connect", "configure", "newConversation", "chooseHaLayout"),
    "VOICE": ("chooseMicSensitivity", "chooseFollowUp",
              "chooseMicrophoneDetection", "startActivity", "chooseWakeWord"),
    "OVERLAY": ("launchOverlay", "showOverlayPreferences"),
    "ALERTS": ("chooseTvNotifications", "toggleSpokenAnnouncements",
               "chooseAnnouncementVolume", "testSpokenAnnouncements",
               "showTvAlertStatus", "chooseCameraMode"),
    "SCREEN": ("showScreensaverSettings", "startActivity"),
    "SYSTEM": ("chooseUpdates", "promptInstallUpdate", "confirmForget"),
}


class TvSettingsRoutingTest(unittest.TestCase):
    def test_settings_hub_is_grouped_and_accessible_by_remote(self):
        root = ACTIVITY.split("private void showSettingsDashboard() {", 1)[1].split(
            "private int menuDp(", 1)[0]
        self.assertIn("activeSettingsSection = null;", root)
        self.assertIn("SettingsSection.values()", root)
        self.assertIn("settingsCategoryCard(section, colors)", root)
        self.assertIn("first.requestFocus()", root)
        self.assertIn("Close settings", root)
        self.assertIn("setContentDescription(", ACTIVITY)
        enum = ACTIVITY.split("private enum SettingsSection {", 1)[1].split(
            "final String title;", 1)[0]
        names = re.findall(r"^        ([A-Z]+)[(]", enum, re.MULTILINE)
        self.assertEqual(list(EXPECTED_CATEGORIES), names)
        self.assertIn("startActivity(new Intent(this, SettingsActivity.class))", ACTIVITY)
        self.assertIn("SettingsActivity", ACTIVITY)

    def test_each_original_settings_action_has_a_destination(self):
        category = ACTIVITY.split("private void showSettingsSection(SettingsSection section) {", 1)[1].split(
            "private void chooseAnnouncementVolume()", 1
        )[0]
        # Find dispatch (the second switch on section, after menu labels).
        dispatch = category.split("entries, which -> {", 1)[1]
        matches = re.findall(
            r"^                        case ([A-Z]+):\s*(.*?)"
            r"(?=^                        case [A-Z]+:|^                        default:)",
            dispatch, re.MULTILINE | re.DOTALL
        )
        self.assertEqual(list(EXPECTED_CATEGORIES), [x[0] for x in matches])
        for section, code in matches:
            for action in EXPECTED_ACTIONS[section]:
                with self.subTest(section=section, action=action):
                    self.assertIn(action + "(", code)
        self.assertIn("this::showSettingsDashboard", category)
        self.assertIn('"Wake word: " + (!prefs.wakeWord()', category)

    def test_child_menu_back_returns_to_selected_category(self):
        self.assertIn("activeSettingsSection == null ? null : this::showSettingsMenu", ACTIVITY)
        self.assertIn("dialog.setOnCancelListener", ACTIVITY)
        self.assertIn("onBack.run();", ACTIVITY)
        self.assertIn("showSettingsSection(activeSettingsSection);", ACTIVITY)

    def test_frigate_settings_exposes_all_configuration_routes(self):
        block = ACTIVITY.split("private void showScreensaverSettings() {", 1)[1].split(
            "private EditText screensaverInput(", 1
        )[0]
        for option in (
            "Frigate web URL:",
            "Home Assistant cameras:",
            "Dashboard size:",
            "Frigate page layout:",
            "Frigate page fit:",
            "HA camera grid playback:",
            "Choose Android screensaver",
            "Preview Frigate dashboard",
            "Screensaver status & permissions",
            "Test Frigate as TV overlay",
        ):
            with self.subTest(option=option):
                self.assertIn(option, block)
        for method in (
            "editScreensaverUrl",
            "editScreensaverCameras",
            "chooseScreensaverSize",
            "toggleScreensaverDesktopPage",
            "chooseScreensaverFit",
            "chooseScreensaverLiveMode",
            "openAndroidScreensaverList",
            "showScreensaverDiagnostics",
            "testScreensaverOverlay",
        ):
            with self.subTest(method=method):
                self.assertIn(method + "();", block)

    def test_screensaver_has_explicit_ambient_diagnostics_and_recovery(self):
        root = ACTIVITY.split("private void showScreensaverDiagnostics()", 1)[1]
        self.assertIn("ScreensaverWatchStatus.lastEvent()", root)
        self.assertIn("ScreensaverWatchStatus.lastOverlay()", root)
        self.assertIn("ScreensaverWatchService.ACTION_TEST_OVERLAY", root)
        receiver = (
            Path(__file__).resolve().parents[1]
            / "app/src/main/java/com/example/tvvoiceprobe/TvEventsRestartReceiver.java"
        ).read_text(encoding="utf-8")
        self.assertIn("ScreensaverWatchService.startIfEnabled(context);", receiver)
        service = (
            Path(__file__).resolve().parents[1]
            / "app/src/main/java/com/example/tvvoiceprobe/ScreensaverWatchService.java"
        ).read_text(encoding="utf-8")
        self.assertIn("return START_STICKY;", service)
        self.assertIn("ACTION_TEST_OVERLAY", service)
        self.assertIn("ScreensaverWatchStatus.event", service)

    def test_follow_up_is_opt_in_and_has_a_separate_mic_routing_menu(self):
        self.assertIn('prefs.setFollowUp(false)', ACTIVITY)
        self.assertIn('prefs.setFollowUp(true)', ACTIVITY)
        self.assertIn('private void chooseFollowUp()', ACTIVITY)
        self.assertIn('private void chooseMicrophoneDetection()', ACTIVITY)


    def test_premium_fullscreen_settings_activity_exists(self):
        settings = (
            Path(__file__).resolve().parents[1]
            / "app/src/main/java/com/example/tvvoiceprobe/SettingsActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("class SettingsActivity", settings)
        self.assertIn("Credentials", settings)
        self.assertIn("Wake word", settings)
        self.assertIn("TV alerts", settings)
        self.assertIn("D-pad", settings)
        self.assertIn("AppPalette", settings)
        self.assertIn("Switch", settings)
        self.assertIn("class SettingsActivity", settings)
        # Persistent host: must not start VoiceActivity for option selection
        self.assertNotIn("FLAG_ACTIVITY_CLEAR_TOP", settings)
        self.assertNotIn("startActivity(intent)", settings)
        self.assertIn("request_connect", settings)
        manifest = (
            Path(__file__).resolve().parents[1]
            / "app/src/main/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        self.assertIn("SettingsActivity", manifest)
        # Action parity: the dispatcher must call the real VoiceActivity methods
        self.assertIn("newConversation()", ACTIVITY)
        self.assertIn("returnToPremiumSettingsIfNeeded", ACTIVITY)
        self.assertNotIn("assist.newConversation()", ACTIVITY)



if __name__ == "__main__":
    unittest.main()
