"""Prevent beta APK from overwriting stable installation or implying v3 login."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MANIFEST = (APP / "src/main/AndroidManifest.xml").read_text()
GRADLE = (APP / "build.gradle").read_text()
VOICE = (APP / "src/main/java/com/example/tvvoiceprobe/VoiceActivity.java").read_text()
READINESS = (APP / "src/main/java/com/example/tvvoiceprobe/FullAppBetaReadiness.java").read_text()


class BetaInstallSafetyTests(unittest.TestCase):
    def test_parallel_debug_package_and_distinct_launcher_title(self):
        self.assertIn("applicationIdSuffix '.debug'", GRADLE)
        self.assertIn('manifestPlaceholders = [appLabel: "Kellie\'s Voice Beta"]', GRADLE)
        self.assertIn('manifestPlaceholders = [appLabel: "Kellie\'s Voice"]', GRADLE)
        self.assertIn('android:label="' + '$' + '{appLabel}"', MANIFEST)
        self.assertIn("versionNameSuffix '-test'", GRADLE)

    def test_beta_menu_is_debug_only_not_a_public_new_login(self):
        self.assertIn("Full app beta test center (DEBUG)", VOICE)
        self.assertIn("FLAG_DEBUGGABLE", VOICE)
        self.assertIn("showFullAppBetaTestCenter", VOICE)
        self.assertIn("PairingV3QaActivity.class", VOICE)
        self.assertIn("optional owner-approved dedicated TV login", READINESS)
        self.assertIn("owner receipt alone does NOT", READINESS)
        self.assertNotIn("setHaToken(", READINESS)
        self.assertNotIn("async_create_refresh_token", READINESS)

    def test_beta_status_checks_separate_assist_and_tv_alerts(self):
        self.assertIn("assist != null && assist.isConnected()", VOICE)
        self.assertIn("TvEventsRecovery.CONNECTED.equals", VOICE)
        self.assertIn("prefs.microphoneDisabled()", VOICE)
        self.assertIn("Settings.canDrawOverlays(this)", VOICE)

    def test_first_run_microphone_consent_is_explicit_and_never_auto_captures(self):
        self.assertIn("Android microphone permission:", VOICE)
        self.assertIn("requestMicrophoneSetupPermission()", VOICE)
        self.assertIn("SETUP_MIC_PERMISSION_REQUEST", VOICE)
        self.assertIn("requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}", VOICE)
        self.assertIn("No recording has started", VOICE)

    def test_alert_overlay_grant_completes_only_requested_enable(self):
        self.assertIn("TV_ALERT_OVERLAY_SETTINGS_REQUEST", VOICE)
        self.assertIn("pendingTvAlertsPermission = enableAlertsWhenGranted", VOICE)
        self.assertIn("if (pendingTvAlertsPermission && Settings.canDrawOverlays(this))", VOICE)
        self.assertIn("if (requestedEnable) enableTvAlertsFromUser()", VOICE)
        self.assertIn("case 7: chooseHaLayout(); break;", VOICE)
        self.assertIn("The separate v3 QA pairing does not register the alert device", VOICE)



if __name__ == "__main__":
    unittest.main()
