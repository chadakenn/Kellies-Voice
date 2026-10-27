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

EXPECTED = [
    ("Home Assistant connection", "configure"),
    ("Connect / reconnect", "connect"),
    ("Start new conversation", "newConversation"),
    ("Hardware microphone diagnostics", "startActivity"),
    ("Microphone sensitivity:", "chooseMicSensitivity"),
    ("Follow-up listening:", "chooseFollowUp"),
    ("Mic input detection:", "chooseMicrophoneDetection"),
    ("Open floating voice overlay", "launchOverlay"),
    ("Overlay appearance & position", "showOverlayPreferences"),
    ("Home Assistant layout:", "chooseHaLayout"),
    ("TV announcements & cameras:", "chooseTvNotifications"),
    ("Spoken announcements:", "toggleSpokenAnnouncements"),
    ("Announcement voice volume:", "chooseAnnouncementVolume"),
    ("Test spoken announcements locally", "testSpokenAnnouncements"),
    ("TV alert status", "showTvAlertStatus"),
    ("Live camera:", "chooseCameraMode"),
    ("Check for app updates:", "chooseUpdates"),
    ("Install downloaded update", "promptInstallUpdate"),
    ("Frigate screensaver:", "showScreensaverSettings"),
    ("Preview Frigate dashboard", "startActivity"),
    ("Forget saved connection", "confirmForget"),
    ("Assist pipeline:", "chooseAssistPipeline"),
]


class TvSettingsRoutingTest(unittest.TestCase):
    def test_all_displayed_settings_open_matching_actions(self):
        block = ACTIVITY.split("private void showSettingsMenu() {", 1)[1].split(
            "private void chooseAnnouncementVolume()", 1
        )[0]
        labels = block.split("String[] entries = new String[] {", 1)[1].split("};", 1)[0]
        # All top-level menu items are indented exactly 16 spaces.
        menu_labels = re.findall(r'^ {16}"([^"]+)"', labels, re.MULTILINE)
        self.assertEqual(
            len(EXPECTED), len(menu_labels),
            "Adding a settings row requires assigning its click handler",
        )
        dispatch = block.split("switch (which) {", 1)[1].split("default: break;", 1)[0]
        handlers = {
            int(index): method
            for index, method in re.findall(
                r"case\s+(\d+)\s*:\s*([A-Za-z]\w*)\s*\(", dispatch
            )
        }
        self.assertEqual(list(range(len(EXPECTED))), sorted(handlers))
        for index, (prefix, action) in enumerate(EXPECTED):
            with self.subTest(index=index, label=prefix):
                self.assertTrue(menu_labels[index].startswith(prefix))
                self.assertEqual(action, handlers[index])

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


if __name__ == "__main__":
    unittest.main()
