"""Guard the TV first-run wizard and explicit-consent onboarding."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main"
WIZARD = (ROOT / "java/com/example/tvvoiceprobe/FirstRunSetupActivity.java").read_text()
VOICE = (ROOT / "java/com/example/tvvoiceprobe/VoiceActivity.java").read_text()
MANIFEST = (ROOT / "AndroidManifest.xml").read_text()


class BetaFirstRunSetupTests(unittest.TestCase):
    def test_production_activity_nonexported_and_existing_logins_preserved(self):
        self.assertIn('android:name=".FirstRunSetupActivity"', MANIFEST)
        self.assertIn('android:exported="false"', MANIFEST)
        self.assertNotIn("if (!BuildConfig.DEBUG && !BuildConfig.IS_QA)", WIZARD)
        self.assertIn("new CredentialStore(activity).load() != null", WIZARD)
        self.assertIn("new TvRenewableLoginVault(activity).hasActiveLogin()", WIZARD)
        self.assertIn("FirstRunSetupActivity.needsSetup(this)", VOICE)
        self.assertIn("state == null", VOICE)
        self.assertIn("startActivity(new Intent(this, FirstRunSetupActivity.class))", VOICE)
        self.assertIn("First-time setup wizard", VOICE)
        self.assertIn('getBoolean(COMPLETED, false)', WIZARD)
        self.assertIn('putBoolean(COMPLETED, true)', WIZARD)

    def test_tv_steps_are_explicit_and_skippable(self):
        for label in ("START SETUP", "OPEN SECURE TV PAIRING",
                      "GRANT MICROPHONE PERMISSION", "GRANT DISPLAY OVER OTHER APPS",
                      "ENABLE TV ALERTS (OPTIONAL)", "FINISH SETUP",
                      "SKIP SETUP FOR NOW"):
            self.assertIn(label, WIZARD)
        self.assertIn("PairingV3QaActivity.class", WIZARD)
        self.assertIn("REQUEST_MIC", WIZARD)
        self.assertIn("ACTION_MANAGE_OVERLAY_PERMISSION", WIZARD)
        self.assertIn("SHOW HOME ASSISTANT TV-DEVICE PAIRING STEPS", WIZARD)
        self.assertIn("onBackPressed()", WIZARD)
        self.assertIn("requestFocus()", WIZARD)

    def test_no_automatic_mic_or_alert_start_on_create_resume(self):
        self.assertNotIn("WakeWordService", WIZARD)
        self.assertNotIn("assist.talk()", WIZARD)
        self.assertNotIn("AudioRecord", WIZARD)
        self.assertIn('prefs.setTvEvents(true)', WIZARD)
        self.assertIn('private void enableAlerts()', WIZARD)
        self.assertIn('if (!Settings.canDrawOverlays(this))', WIZARD)
        self.assertIn('if (!hasTvLogin())', WIZARD)
        self.assertIn('new Intent(this, TvEventsService.class)', WIZARD)
        self.assertIn('setAction(TvEventsService.ACTION_RESUME)', WIZARD)
        self.assertIn("Never intercept the dedicated", VOICE)


if __name__ == "__main__":
    unittest.main()
