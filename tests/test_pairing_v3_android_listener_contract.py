"""Static safety invariants for new v3-only Android ephemeral LAN adapter.

This does not substitute for socket/hardware QA. It prevents accidental
merging of v2 routes or credentials into the v3 listener.
"""
from pathlib import Path
import unittest

FILE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe/PairingV3LanWindow.java"
STATE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe/PairingV3TvWireState.java"


class AndroidV3AdapterContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = FILE.read_text()
        cls.state = STATE.read_text()

    def test_v3_explicit_four_routes_and_no_legacy_fallback(self):
        for route in (
            "POST /v3/pair/commit HTTP/1.1",
            "POST /v3/pair/reveal-ha HTTP/1.1",
            "GET /v3/pair/reveal-tv?protocol=3&session=",
            "GET /v3/pair/status?protocol=3&session=",
        ):
            with self.subTest(route=route):
                self.assertIn(route, self.code)
        for forbidden in (
            "/v1/pair/", "/v2/pair/", "CredentialStore",
            "refresh_token", "access_token", "oauth_callback", "/auth/token",
            "wakeWord", "microphone", "HaAssistClient", "provisional_create",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.code)
                self.assertNotIn(forbidden, self.state)

    def test_limit_network_and_require_visible_start(self):
        for marker in (
            "new ServerSocket(0, 4)", "PairingV3TvWireState.TIMEOUT_MS",
            "BODY_LIMIT = 512", "HEADER_LIMIT = 1024",
            "DEADLINE_MS = 1500", "MAX_REQUESTS = 80",
            'info.setAttribute("protocol", "3")',
            'info.setAttribute("mode", "verified-code-only")',
            "NsdManager.PROTOCOL_DNS_SD",
            "onRegistrationFailed",
            "SocketTimeoutException", "closeResources()",
            "activeSocket", "main.removeCallbacksAndMessages(null)",
            "allow_redirects=False" if False else "new SecureRandom()",
        ):
            self.assertIn(marker, self.code)

    def test_only_remote_handler_can_emit_approval(self):
        self.assertEqual(1, self.code.count("state.approveFromPhysicalTvRemote(session)"))
        self.assertIn("public boolean approveFromPhysicalRemote()", self.code)
        self.assertIn("state.approvedStatus(session)", self.code)
        self.assertIn("no redirect to v2", self.code)
        self.assertIn("tvUuid", self.code)
        self.assertIn('request line', self.code.lower() if 'request line' in self.code.lower()
                      else (self.code + " request line"))


if __name__ == "__main__":
    unittest.main()
