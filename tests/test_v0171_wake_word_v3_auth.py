"""Prevent v0.17.0's Compose-only manual-token wake-word regression.

These source contracts run with Home Assistant CI as well as Android JUnit.
No real microphone, credentials or Home Assistant network calls are involved.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe"


class WakeV3GateContractTests(unittest.TestCase):
    def test_both_settings_screens_accept_v3_renewable_login(self):
        compose = (ROOT / "ui/settings/ComposeSettingsActivity.kt").read_text()
        voice = (ROOT / "VoiceActivity.java").read_text()
        gate = (ROOT / "WakeWordLoginGate.java").read_text()
        self.assertIn("WakeWordLoginGate.hasSavedLogin(context)", compose)
        self.assertIn("WakeWordLoginGate.hasSavedLogin(this)", voice)
        self.assertNotIn("if (settings == null) {\n            status(\"Configure Home Assistant before enabling the wake word.", voice)
        self.assertIn("vault.blocksManualFallback()", gate)
        self.assertIn("vault.hasActiveLogin()", gate)
        self.assertIn("new CredentialStore(context).load()", gate)

    def test_service_and_startup_use_exact_same_auth_boundary(self):
        listener = (ROOT / "WakeWordService.java").read_text()
        startup = (ROOT / "WakeWordStartup.java").read_text()
        self.assertIn("WakeWordLoginGate.hasSavedLogin(this)", listener)
        self.assertIn("WakeWordLoginGate.hasSavedLogin(activity)", startup)
        gate = (ROOT / "WakeWordLoginGate.java").read_text()
        self.assertIn("return renewableSelected ? renewableActive : manualCredentialsStored;", gate)
        self.assertNotIn("renewableSelected || manualCredentialsStored", gate)


if __name__ == "__main__":
    unittest.main()
