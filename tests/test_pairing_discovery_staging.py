"""Staging safeguards: #193 discovery-only preview cannot become a production login."""

import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/example/tvvoiceprobe"


class PairingDiscoverySourceTests(unittest.TestCase):
    def test_no_token_upload_or_new_auth_handler(self):
        listener = (APP / "PairingDiscoveryWindow.java").read_text()
        screen = (APP / "PairingSetupActivity.java").read_text()
        self.assertIn('setAttribute("mode", "discovery-only")', listener)
        self.assertIn('info.setPort(server.getLocalPort())', listener)
        self.assertIn('new ServerSocket(0, 4)', listener)
        self.assertIn('public void close()', listener)
        self.assertIn("onStop()", screen)
        self.assertIn('stopWindow()', screen)
        self.assertIn('PairingDiscoveryWindow.start(this', screen)
        for forbidden in ("CredentialStore", "HaAssistClient", "setToken(",
                          "Authorization: Bearer", 'POST /', "grant_type="):
            self.assertNotIn(forbidden, listener)
            self.assertNotIn(forbidden, screen)

    def test_preview_hidden_from_stable(self):
        activity = (APP / "VoiceActivity.java").read_text()
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertIn("boolean pairingPreview = pairingPreviewBuild()", activity)
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", activity)
        self.assertIn('version.contains("-dev")', activity)
        self.assertIn('version.contains("-qa")', activity)
        self.assertIn('if (pairingPreview) {', activity)
        self.assertIn('android:name=".PairingSetupActivity" android:exported="false"', manifest)

    def test_ha_must_not_claim_capability_before_auth_is_available(self):
        manifest = json.loads((ROOT / "custom_components/kellies_voice/manifest.json").read_text())
        self.assertNotIn("_kellies-pair._tcp.local.", manifest.get("zeroconf", []))
        probe = (ROOT / "custom_components/kellies_voice/pairing_probe.py").read_text()
        self.assertIn('"authorized": False', probe)
        self.assertNotIn("async_create_entry", probe)


if __name__ == "__main__":
    unittest.main()
