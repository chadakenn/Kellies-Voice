"""Regression guard: v3 renewable-only screensaver camera authentication.

This source-contract test runs in CI without an Android device. Physical
dream/preview credential refresh still needs a signed QA onn test.
"""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1] / "app" / "src" / "main" /
          "java" / "com" / "example" / "tvvoiceprobe" /
          "ScreensaverBoard.java").read_text()


class ScreensaverRenewableLoginTests(unittest.TestCase):
    def test_keystore_v3_connection_on_worker_and_manual_policy(self):
        self.assertIn("TvRenewableLoginVault vault = new TvRenewableLoginVault(context);", SOURCE)
        self.assertIn("vault.hasActiveLogin()", SOURCE)
        self.assertIn("!TvOverlayLoginPolicy.mayUseManual(renewable)", SOURCE)
        self.assertIn("vault.connectionOnWorker()", SOURCE)
        self.assertIn('new Thread(() -> {', SOURCE)
        self.assertIn('"KelliesScreensaverRenewableLogin"', SOURCE)
        self.assertIn("main.post(() -> {", SOURCE)

    def test_no_legacy_fallback_on_failed_renew_and_lifecycle_gate(self):
        self.assertIn('if (authorized == null) {', SOURCE)
        self.assertIn("return;\n                    }\n                    root.removeView(signInNotice)", SOURCE)
        self.assertIn('if (stopped || current != generation) return;', SOURCE)
        self.assertIn('if (stopped || current != generation || connection == null) return;', SOURCE)

    def test_rotates_access_token_before_expiry_and_retains_manual_mode(self):
        self.assertIn('RENEWABLE_RELOAD_MS = 20 * 60_000L', SOURCE)
        self.assertIn('main.postDelayed(() -> {', SOURCE)
        self.assertIn('}, RENEWABLE_RELOAD_MS)', SOURCE)
        self.assertIn('CredentialStore.Connection manual = signedIn();', SOURCE)
        self.assertIn('beginCameraStreams(manual, cameras, photos', SOURCE)

    def test_camera_token_does_not_enter_url_or_logs(self):
        self.assertIn('.header("Authorization", "Bearer " + connection.token)', SOURCE)
        self.assertNotIn('.addQueryParameter("token"', SOURCE)
        self.assertNotIn('Log.d(', SOURCE)
        self.assertNotIn('Log.e(', SOURCE)


if __name__ == "__main__":
    unittest.main()
