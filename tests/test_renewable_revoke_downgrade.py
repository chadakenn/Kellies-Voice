"""No renewable TV-login revoke may reactivate legacy owner credentials.

Source-contract checks run offline; signed onn QA is still required.
The marker is a non-sensitive boolean, never a stored token.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / (
    "app/src/main/java/com/example/tvvoiceprobe"
)

def source(name):
    return (ROOT / name).read_text()


class RenewableRevocationNoDowngradeTests(unittest.TestCase):
    def test_mode_is_sticky_and_marked_before_active_credential_write(self):
        code = source("TvRenewableLoginVault.java")
        self.assertIn('KEY_RENEWABLE_MODE = "owner_approved_renewable_mode"', code)
        self.assertIn('saved.getBoolean(KEY_RENEWABLE_MODE, false) || saved.contains(KEY)', code)
        self.assertIn("r.active = true;\n        // Fail closed", code)
        self.assertLess(code.index("rememberRenewableMode();\n        write(r);"),
                        code.index("synchronized String renewOnWorker()"))
        self.assertIn('rememberRenewableMode();\n        long now', code)
        self.assertIn('if (response.code() == 400 || response.code() == 403)', code)
        self.assertIn('clear();\n                throw new IllegalStateException', code)

    def test_revoke_clears_credential_only_and_preserves_sticky_mode(self):
        code = source("TvRenewableLoginVault.java")
        clear_body = code.split("synchronized void clear() {", 1)[1]
        self.assertIn("prefs().edit().remove(KEY).commit()", clear_body)
        self.assertNotIn("remove(KEY_RENEWABLE_MODE)", clear_body)
        self.assertNotIn("putBoolean(KEY_RENEWABLE_MODE, false)", code)
        self.assertNotIn("new CredentialStore(", code)
        self.assertNotIn("CredentialStore.load(", code)

    def test_every_legacy_entry_checks_mode_not_just_active_token(self):
        checks = {
            "ScreensaverBoard.java": "vault.blocksManualFallback()",
            "VoiceOverlayService.java": "vault.blocksManualFallback()",
            "VoiceActivity.java": "new TvRenewableLoginVault(this).blocksManualFallback()",
            "TvEventsService.java": "new TvRenewableLoginVault(this).blocksManualFallback()",
            "TvEventsRestartReceiver.java": "if (!vault.blocksManualFallback())",
        }
        for file, expected in checks.items():
            with self.subTest(file=file):
                self.assertIn(expected, source(file))

    def test_policy_does_not_convert_revocation_to_manual_authorization(self):
        code = source("TvOverlayLoginPolicy.java")
        self.assertIn("mayUseManual(boolean renewableModeSelected)", code)
        self.assertIn("return !renewableModeSelected;", code)
        self.assertIn("connectionOnWorker()", source("ScreensaverBoard.java"))
        self.assertIn('TV login unavailable. Open SETTINGS', source("ScreensaverBoard.java"))


if __name__ == "__main__":
    unittest.main()
