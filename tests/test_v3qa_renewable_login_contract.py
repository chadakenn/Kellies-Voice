"""Safety invariants: native renewable login never autouses owner's token."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TV = (ROOT / "app/src/main/java/com/example/tvvoiceprobe"
      / "TvRenewableLoginVault.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/example/tvvoiceprobe"
          / "TvRenewableLoginPolicy.java").read_text()
ISSUER = (ROOT / "custom_components/kellies_voice_v3qa"
          / "scoped_login_issuer.py").read_text()
FLOW = (ROOT / "custom_components/kellies_voice_v3qa"
        / "config_flow.py").read_text()
INIT = (ROOT / "custom_components/kellies_voice_v3qa"
        / "__init__.py").read_text()


class RenewableScopeContracts(unittest.TestCase):
    def test_vault_does_not_read_manual_login_or_install_silently(self):
        self.assertNotIn("new CredentialStore(", TV)
        self.assertNotIn("CredentialStore.load(", TV)
        self.assertNotIn("TvRenewableLoginVault", FLOW)
        self.assertNotIn("scoped_login_issuer", FLOW)
        self.assertNotIn("scoped_login_issuer", INIT)
        self.assertNotIn("async_create_refresh_token", INIT)

    def test_only_https_token_refresh_with_client_id_and_no_redirect(self):
        self.assertIn('!"https".equals(parsed.getScheme())', POLICY)
        self.assertIn('"/auth/token"', POLICY)
        self.assertIn('"refresh_token"', TV)
        self.assertIn('"client_id"', TV)
        self.assertIn(".followRedirects(false)", TV)
        self.assertIn(".followSslRedirects(false)", TV)
        self.assertIn("cachedAccess", TV)
        self.assertIn("No completed restricted TV login", TV)

    def test_credential_encryption_is_distinct_and_activation_is_gated(self):
        self.assertIn("AndroidKeyStore", TV)
        self.assertIn("AES/GCM/NoPadding", TV)
        self.assertIn("setRandomizedEncryptionRequired(true)", TV)
        self.assertIn("r.active = false", TV)
        self.assertIn("activateAfterVerifiedServerCommit", TV)
        self.assertIn("!r.active", TV)
        self.assertIn("if (r == null || !r.active)", TV)
        self.assertIn("prefs().edit().remove(KEY).commit()", TV)

    def test_backend_refuses_default_broad_users_and_never_exposes_issuer(self):
        self.assertIn("check_restricted_tv_scope(", ISSUER)
        self.assertIn("async_get_user(owner_id)", ISSUER)
        self.assertIn("async_create_refresh_token", ISSUER)
        self.assertIn("journal.begin_intent(", ISSUER)
        self.assertIn("journal.record_id(", ISSUER)
        self.assertIn("async_remove_refresh_token", ISSUER)
        self.assertNotIn("async_register_command(", ISSUER)
        self.assertNotIn("websocket_api", ISSUER)
        self.assertNotIn("async_register_static_paths", ISSUER)


if __name__ == "__main__":
    unittest.main()
