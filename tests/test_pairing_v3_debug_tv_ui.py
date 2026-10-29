"""Only the separately installed Android DEBUG APK exposes v3 pairing QA.

The stock launcher, currently connected TV credentials, normal HA settings,
mic, wake word and public release menu must stay unchanged.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/com/example/tvvoiceprobe/PairingV3QaActivity.java"
VOICE = ROOT / "app/src/main/java/com/example/tvvoiceprobe/VoiceActivity.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


class V3DebugTvOnboardingContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = ACTIVITY.read_text()
        cls.voice = VOICE.read_text()
        cls.manifest = MANIFEST.read_text()

    def test_debug_only_entry_nonexported_and_never_launcher(self):
        self.assertIn("FLAG_DEBUGGABLE", self.activity)
        self.assertIn("FLAG_DEBUGGABLE", self.voice)
        self.assertIn("Secure pairing v3 (DEBUG TEST ONLY, no login)", self.voice)
        self.assertIn('android:name=".PairingV3QaActivity"', self.manifest)
        # Isolated activity is registered without Android launcher intent filters.
        self.assertRegex(self.manifest,
            r'android:name="\.PairingV3QaActivity"\s+android:exported="false"')
        self.assertIn("case 4:", self.voice)
        self.assertIn("new Intent(this, PairingV3QaActivity.class)", self.voice)

    def test_listener_starts_only_after_tv_remote_and_cancels_on_lifecycle(self):
        self.assertIn("start.setOnClickListener(v -> startWindow())", self.activity)
        self.assertEqual(1, self.activity.count("PairingV3LanWindow.start("))
        self.assertIn("onStop()", self.activity)
        self.assertIn("onDestroy()", self.activity)
        self.assertIn("onBackPressed()", self.activity)
        self.assertIn("existing.close()", self.activity)
        self.assertIn("attempt != generation", self.activity)
        self.assertIn("start.setEnabled(true)", self.activity)

    def test_no_account_login_or_automatic_microphone(self):
        for forbidden in (
            "CredentialStore(", "store.save(", "HaAssistClient(",
            "WakeWordService.", "AudioRecord(", "startForegroundService(",
            "startListening(", "oauth/token", "/auth/token",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.activity)
        self.assertIn("V3 TEST MODE", self.activity)
        self.assertIn("does not sign in", self.activity)


if __name__ == "__main__":
    unittest.main()
