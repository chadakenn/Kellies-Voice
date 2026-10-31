"""QA guard: a launcher opened with an owner-approved v3 login must not
require the legacy/manual Home Assistant CredentialStore token.
No TV mic, device or live Home Assistant access is needed for this test.
"""
from pathlib import Path
import unittest

ROOT = (Path(__file__).resolve().parents[1] /
        "app/src/main/java/com/example/tvvoiceprobe")
MAIN = (ROOT / "VoiceActivity.java").read_text()
SERVICE = (ROOT / "VoiceOverlayService.java").read_text()
SHORTCUT = (ROOT / "OverlayShortcutActivity.java").read_text()
NAV = (ROOT / "TvHomeNavigation.java").read_text()


class OverlayAndQuickActionsQaTests(unittest.TestCase):
    def test_launcher_uses_v3_keystore_and_never_legacy_fallback(self):
        method = MAIN.split("private boolean canStartOverlayImmediately()", 1)[1].split(
            "private void startTransparentShortcutOverlay()", 1)[0]
        self.assertIn("vault.blocksManualFallback()", method)
        self.assertIn("vault.hasActiveLogin()", method)
        self.assertIn("if (vault.blocksManualFallback()) return vault.hasActiveLogin();", method)
        self.assertIn("new CredentialStore(this).load()", method)

    def test_explicit_overlay_does_not_require_manual_settings_with_v3(self):
        method = MAIN.split("private void launchOverlay()", 1)[1].split(
            "private void listenForOverlayThenBackground()", 1)[0]
        self.assertIn("selectedRenewable = vault.blocksManualFallback()", method)
        self.assertIn("selectedRenewable && !vault.hasActiveLogin()", method)
        self.assertIn("!selectedRenewable && settings == null", method)
        self.assertIn("startForegroundService(start)", method)

    def test_dedicated_star_shortcut_accepts_v3(self):
        self.assertIn("boolean configured = vault.hasActiveLogin()", SHORTCUT)
        self.assertIn("if (!vault.blocksManualFallback())", SHORTCUT)
        self.assertIn("new Intent(this, VoiceOverlayService.class)", SHORTCUT)
        self.assertIn("vault.connectionOnWorker()", SERVICE)
        self.assertIn("TvOverlayLoginPolicy.mayUseManual(renewableSession)", SERVICE)

    def test_remote_uses_two_row_navigation_with_real_shortcut_count(self):
        self.assertIn("TvHomeNavigation.down(selected, homeShortcutButtons.size())", MAIN)
        self.assertIn("if (n > 2 && (selected == 3 || selected == 4))", NAV)
        self.assertIn("if (n == 3) return 5", NAV)
        self.assertIn("return selected == 4 && n == 4 ? 6 : 5", NAV)


if __name__ == "__main__":
    unittest.main()
