"""#193 pending OAuth staging must not overwrite the existing working login.

The Java credential value class has JVM tests. AndroidKeyStore itself requires
instrumentation/real device QA and is deliberately never mocked into passing.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/tvvoiceprobe"
STAGING = (ROOT / "OAuthCredentialStagingStore.java").read_text(encoding="utf-8")
EXISTING = (ROOT / "CredentialStore.java").read_text(encoding="utf-8")


class PendingOAuthStagingContract(unittest.TestCase):
    def test_staging_uses_own_isolated_keystore_and_no_active_login(self):
        self.assertIn("kellies_voice_pending_oauth_v1", STAGING)
        self.assertIn("kellies_voice_oauth_staging", STAGING)
        self.assertNotIn("tv_voice_local_settings", STAGING)
        self.assertNotIn("encrypted_connection", STAGING)
        self.assertIn('private static final String PENDING = "encrypted_pending"', STAGING)
        self.assertIn("AndroidKeyStore", STAGING)
        self.assertIn("AES/GCM/NoPadding", STAGING)
        self.assertIn("cipher.updateAAD", STAGING)
        self.assertIn("credentials.matches(expectedSession, expectedTvId, nowMillis)", STAGING)
        self.assertIn("credentials.matches(session, tvId, nowMillis)", STAGING)
        self.assertIn("public synchronized void discard()", STAGING)
        self.assertIn("store.deleteEntry(KEY_ALIAS)", STAGING)
        self.assertNotIn("public void promote", STAGING)
        self.assertNotIn("public void saveActive", STAGING)
        self.assertNotIn("new CredentialStore(", STAGING)
        self.assertNotIn("HaAssistClient", STAGING.replace(
            "HaAssistClient.\n", ""))  # prose comment is not runtime code

    def test_legacy_storage_is_unchanged_by_pending_component(self):
        self.assertIn('private static final String SECRET = "encrypted_connection"', EXISTING)
        self.assertIn('private static final String ALIAS = "tv_voice_assist_credentials_v1"', EXISTING)
        self.assertIn("public void save(String url, String token)", EXISTING)
        self.assertIn("public Connection load()", EXISTING)
        self.assertNotIn("encrypted_pending", EXISTING)

    def test_no_debug_credentials_or_new_network_listeners(self):
        self.assertNotIn("Log.", STAGING)
        self.assertNotIn("System.out", STAGING)
        self.assertNotIn("HttpURLConnection", STAGING)
        self.assertNotIn("ServerSocket", STAGING)
        self.assertNotIn("addService", STAGING)
        self.assertNotIn("startForeground", STAGING)


if __name__ == "__main__":
    unittest.main()
