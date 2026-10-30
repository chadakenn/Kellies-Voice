"""Guard the main TV screen refresh without touching microphone semantics.

Source-level checks are paired with Android/JVM builds and real remote QA.
The UI must always expose exactly the original TALK / STOP / SETTINGS actions.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/example/tvvoiceprobe/VoiceActivity.java").read_text(
    encoding="utf-8"
)


class TvHomeRefreshTests(unittest.TestCase):
    def test_main_screen_retains_one_action_for_each_original_button(self):
        first = ACTIVITY.index("LinearLayout root = new LinearLayout(this);", ACTIVITY.index("@Override public void onCreate"))
        last = ACTIVITY.index("// A short, focus-safe welcome view", first)
        home = ACTIVITY[first:last]
        buttons = re.findall(r'addButton\(toolbar, "([^"]+)"', home)
        self.assertEqual(["TALK", "STOP", "SETTINGS"], buttons)
        self.assertIn('v -> startTalk()', home)
        self.assertIn('v -> assist.finishTalking()', home)
        self.assertIn('BuildConfig.DEBUG || BuildConfig.IS_QA', home)
        self.assertIn('ComposeSettingsActivity.class', home)
        self.assertIn('showSettingsDashboard();', home)
        self.assertIn("root.addView(scroller", home)

    def test_voice_and_reply_updates_reuse_original_text_fields(self):
        self.assertIn('recognized = label("Nothing yet"', ACTIVITY)
        self.assertIn('response = label("Your answer will appear here."', ACTIVITY)
        self.assertIn('recognized.setText(value)', ACTIVITY)
        self.assertIn('response.setText(value)', ACTIVITY)
        self.assertIn('if (recognized != null) recognized.setText("Listening...")', ACTIVITY)
        self.assertIn('if (status != null) status.setText(message)', ACTIVITY)

    def test_all_home_elements_recolor_when_skin_changes(self):
        palette = ACTIVITY.split("private void applyMainPalette()", 1)[1].split(
            "private void addButton(", 1
        )[0]
        for name in ("mainTitle", "homeSubtitle", "homePrivacyLabel", "connection",
                     "status", "answerHeading", "response", "transcriptHeading", "recognized",
                     "homeAnswerCard", "homeTranscriptCard", "homeStatusCard", "homeConnectionCard"):
            with self.subTest(name=name):
                self.assertIn(name, palette)
        self.assertIn("b.hasFocus()", palette)
        self.assertIn("currentPalette()", palette)
        self.assertIn("homeActionBackground", palette)

    def test_status_card_hides_ha_address_and_no_new_auto_listening(self):
        block = ACTIVITY.split("private void updateConnectionLabel()", 1)[1].split(
            "private void showCopperMenu(", 1
        )[0]
        self.assertNotIn("settings.url", block)
        self.assertIn('"CONNECTED"', block)
        self.assertIn('"DISCONNECTED"', block)
        first = ACTIVITY.index("LinearLayout root = new LinearLayout(this);", ACTIVITY.index("@Override public void onCreate"))
        last = ACTIVITY.index("// A short, focus-safe welcome view", first)
        home = ACTIVITY[first:last]
        for forbidden in ("WakeWordService.start", "new HaAssistClient(",
                          "requestPermissions(", "startForegroundService("):
            self.assertNotIn(forbidden, home)

    def test_original_remote_shortcut_and_splash_survive(self):
        self.assertIn("KellieSplashView splash = new KellieSplashView(this)", ACTIVITY)
        self.assertIn("startTransparentShortcutOverlay", ACTIVITY)
        self.assertIn("shouldLaunchOverlayFromLauncher", ACTIVITY)
        self.assertIn("KEYCODE_DPAD_CENTER", ACTIVITY)
        self.assertIn("KEYCODE_DPAD_LEFT", ACTIVITY)


if __name__ == "__main__":
    unittest.main()
