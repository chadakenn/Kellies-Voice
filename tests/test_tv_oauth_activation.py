"""Source-contract safeguards for the #193 TV activation boundary.

Pure JVM tests exercise PairingActivationGate itself. Android Keystore
round-trip, disk-failure semantics and resumed pairing need device QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe"
GATE = (ROOT / "PairingActivationGate.java").read_text()
STORE = (ROOT / "OAuthVerifiedCredentialStore.java").read_text()
LEGACY = (ROOT / "CredentialStore.java").read_text()
STAGING = (ROOT / "OAuthCredentialStagingStore.java").read_text()


class PairingActivationContract(unittest.TestCase):
    def test_two_independent_proofs_and_authenticated_ack_required(self):
        self.assertIn("capabilitiesVerified(", GATE)
        self.assertIn("authenticatedHaAck(", GATE)
        self.assertIn("WAIT_HA_ACK", GATE)
        self.assertIn("READY_TO_COMMIT", GATE)
        self.assertIn("SystemClock.elapsedRealtime()", GATE)
        self.assertIn("WINDOW_MILLIS = 120_000L", GATE)
        self.assertIn("validContext(expectedSession, expectedTvId, nowElapsedMillis)", GATE)
        self.assertIn("recordWriteResult(boolean persistedSuccessfully)", GATE)

    def test_verified_credential_store_is_isolated_and_encrypted(self):
        self.assertIn('kellies_voice_verified_oauth_v1', STORE)
        self.assertIn('kellies_voice_oauth_verified', STORE)
        self.assertIn('encrypted_verified', STORE)
        self.assertIn("AES/GCM/NoPadding", STORE)
        self.assertIn("cipher.updateAAD(AAD)", STORE)
        self.assertIn("Arrays.fill(plain", STORE)
        self.assertIn("Arrays.fill(plaintext", STORE)
        self.assertIn('candidate.serverUrl.startsWith("https://")', STORE)
        self.assertIn("if (prefs().contains(SECRET))", STORE)
        self.assertIn(".commit()", STORE)
        self.assertIn("synchronized (gate)", STORE)
        self.assertIn("gate.recordWriteResult(true)", STORE)
        self.assertNotIn('tv_voice_local_settings', STORE)
        self.assertNotIn('tv_voice_assist_credentials_v1', STORE)
        self.assertNotIn("new CredentialStore(", STORE)
        self.assertNotIn("startTalk(", STORE)

    def test_preexisting_staging_and_manual_login_not_modified(self):
        self.assertIn('private static final String ALIAS = "tv_voice_assist_credentials_v1"', LEGACY)
        self.assertIn('private static final String SECRET = "encrypted_connection"', LEGACY)
        self.assertIn('kellies_voice_pending_oauth_v1', STAGING)
        self.assertIn('encrypted_pending', STAGING)
        self.assertNotIn('kellies_voice_verified_oauth_v1', LEGACY + STAGING)

    def test_no_auto_activation_or_secret_logs(self):
        for bad in ("Log.", "System.out", "printStackTrace", "ServerSocket",
                    "HttpURLConnection", "VoiceOverlayService",
                    "WakeWordService", "startForeground", "startActivity"):
            self.assertNotIn(bad, GATE + STORE)
        self.assertNotIn("CredentialStore.save", GATE + STORE)
        self.assertNotIn("new TvEventsService(", GATE + STORE)


if __name__ == "__main__":
    unittest.main()
