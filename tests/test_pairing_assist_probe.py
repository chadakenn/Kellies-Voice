"""Pairing Assist proof must be independent of saved HA login and microphone.

Full network/Android TLS behavior still requires device instrumentation;
source contract checks do not replace the real connnection test.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe"
SOURCE = (ROOT / "PairingAssistConnectionProbe.java").read_text(encoding="utf-8")


class PairingAssistProbeSafetyTests(unittest.TestCase):
    def test_never_activates_or_persists_unverified_login(self):
        for forbidden in (
            "new CredentialStore(", "CredentialStore.save(", "saveActive(",
            "OAuthCredentialStagingStore(", "VoiceOverlayService",
            "startTalk(", "AudioRecord", "MediaRecorder", "startForeground",
            "WakeWordService", "startActivity(",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, SOURCE)

    def test_tls_only_and_no_redirects_or_insecure_trust_bypass(self):
        self.assertIn('!"https".equals(base.scheme())', SOURCE)
        self.assertIn('addPathSegments("api/websocket")', SOURCE)
        self.assertIn("new Request.Builder().url(endpoint).build()", SOURCE)
        for forbidden in ("hostnameVerifier(", "sslSocketFactory(", "TrustManager",
                          "acceptAll", "http://", "access_token", ""):
            # The code has one exact access_token JSON send, and that is expected.
            if forbidden.startswith("access_token"): continue
            self.assertNotIn(forbidden, SOURCE)

    def test_needs_auth_and_actual_assist_query_before_success(self):
        self.assertIn('"auth_required".equals(type)', SOURCE)
        self.assertIn('"auth_ok".equals(type)', SOURCE)
        self.assertIn('"assist_pipeline/pipeline/list"', SOURCE)
        self.assertIn("PairingAssistProofState.PIPELINES_REQUEST_ID", SOURCE)
        self.assertIn("gate.pipelineResult(", SOURCE)
        self.assertIn('"auth_invalid".equals(type)', SOURCE)
        self.assertIn("credentials.matches(expectedSession, expectedTvId", SOURCE)
        self.assertIn("TIMEOUT_MS = 15_000L", SOURCE)
        self.assertIn("old.cancel()", SOURCE)

    def test_no_secret_log_statements(self):
        self.assertNotIn("Log.", SOURCE)
        self.assertNotIn("System.out", SOURCE)
        self.assertNotIn("printStackTrace(", SOURCE)
        self.assertNotIn("getMessage()", SOURCE)


if __name__ == "__main__":
    unittest.main()
