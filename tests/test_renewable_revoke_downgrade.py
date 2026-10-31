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
            "WakeWordService.java": "WakeWordLoginGate.hasSavedLogin(this)",
            "WakeAssistConnection.java": "blocksManualFallback()) return;",
            "OverlayShortcutActivity.java": "if (!vault.blocksManualFallback())",
            "WakeWordStartup.java": "WakeWordLoginGate.hasSavedLogin(activity)",
            "ScreensaverSettingsSync.java": "if (vault.blocksManualFallback())",
        }
        for file, expected in checks.items():
            with self.subTest(file=file):
                self.assertIn(expected, source(file))
        # Shared helper preserves the exact same fail-closed rule: after
        # renewable mode is selected, revoked v3 cannot use legacy tokens.
        gate = source("WakeWordLoginGate.java")
        self.assertIn("vault.blocksManualFallback()", gate)
        self.assertIn("return eligible(true, vault.hasActiveLogin(), false);", gate)
        self.assertIn("return renewableSelected ? renewableActive : manualCredentialsStored;", gate)

    def test_camera_setting_sync_renews_on_worker_before_using_websocket(self):
        code = source("ScreensaverSettingsSync.java")
        self.assertIn("vault.connectionOnWorker()", code)
        self.assertIn('new Thread(() -> {', code)
        self.assertIn('"KelliesScreensaverSettingsRenewal"', code)
        self.assertIn("main.post(() -> {", code)
        self.assertIn("thisGeneration != connectionGeneration", code)
        self.assertIn("scheduleReconnect(null)", code)
        self.assertIn("connectWith(ready)", code)
        self.assertIn("if (vault.blocksManualFallback())", code)

    def test_policy_does_not_convert_revocation_to_manual_authorization(self):
        code = source("TvOverlayLoginPolicy.java")
        self.assertIn("mayUseManual(boolean renewableModeSelected)", code)
        self.assertIn("return !renewableModeSelected;", code)
        self.assertIn("connectionOnWorker()", source("ScreensaverBoard.java"))
        self.assertIn('TV login unavailable. Open SETTINGS', source("ScreensaverBoard.java"))


if __name__ == "__main__":
    unittest.main()
