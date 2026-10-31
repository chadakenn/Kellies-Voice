"""Issue #261: protect tokens in the HA screensaver WebView.
Source-level safety check; real Home Assistant dashboard requires TV hardware QA.
"""
from pathlib import Path
import unittest

ROOT = (Path(__file__).resolve().parents[1] /
        "app/src/main/java/com/example/tvvoiceprobe")

def source(name):
    return (ROOT / name).read_text()


class HaDashboardExternalAuthTests(unittest.TestCase):
    def test_explicit_optin_default_off(self):
        prefs = source("OverlayPreferences.java")
        self.assertIn('KEY_SCREENSAVER_HA_AUTH = "screensaver_ha_external_auth_opt_in"', prefs)
        self.assertIn("return preferences.getBoolean(KEY_SCREENSAVER_HA_AUTH, false);", prefs)
        settings = source("ui/settings/ComposeSettingsActivity.kt")
        self.assertIn('title = "Sign in to Home Assistant dashboard"', settings)
        self.assertIn("prefs.setScreensaverHaExternalAuth(enabled)", settings)

    def test_v2_only_no_insecure_global_javascript_interface(self):
        bridge = source("HaDashboardExternalAuthBridge.java")
        self.assertIn("WebViewFeature.WEB_MESSAGE_LISTENER", bridge)
        self.assertIn('WebViewCompat.addWebMessageListener(web, "externalAppV2"', bridge)
        self.assertIn("Collections.singleton(HaDashboardAuthPolicy.origin(haOrigin))", bridge)
        self.assertIn("isMainFrame", bridge)
        self.assertIn("view.getUrl()", bridge)
        self.assertIn("callbackAllowed(type, callback)", bridge)
        self.assertNotIn("addJavascriptInterface(", bridge)
        self.assertNotIn("setWebContentsDebuggingEnabled", bridge)

    def test_token_and_logout_flow_are_off_ui_thread_and_fail_closed(self):
        bridge = source("HaDashboardExternalAuthBridge.java")
        self.assertIn("vault.connectionOnWorker()", bridge)
        self.assertIn("vault.accessRemainingSeconds()", bridge)
        self.assertIn("vault.revokeOnWorker()", bridge)
        self.assertIn('new Thread(() -> {', bridge)
        self.assertIn('window.externalAuthSetToken(true,{', bridge)
        self.assertIn('window.externalAuthSetToken(false);', bridge)
        self.assertIn('window.externalAuthRevokeToken(', bridge)
        self.assertIn("JSONObject.quote(ready.token)", bridge)
        vault = source("TvRenewableLoginVault.java")
        self.assertIn('.encodedPath("/auth/revoke").query(null).build()', vault)
        self.assertIn('clear(); // Persistent mode marker remains', vault)

    def test_same_origin_https_approved_tv_grant_required_before_webview(self):
        board = source("ScreensaverBoard.java")
        self.assertIn("prefs.screensaverHaExternalAuth()", board)
        self.assertIn("vault.blocksManualFallback() || !vault.hasActiveLogin()", board)
        self.assertIn("HaDashboardAuthPolicy.authenticatedUrl(page, authorized.url)", board)
        self.assertIn("showPage(readyUrl, ready.url)", board)
        self.assertIn("new HaDashboardExternalAuthBridge(web, page", board)
        self.assertIn("if (stopped || current != generation) return;", board)
        self.assertIn("root.addView(web, 0, params)", board)
        policy = source("HaDashboardAuthPolicy.java")
        self.assertIn('"https".equalsIgnoreCase(uri.getScheme())', policy)
        self.assertIn('"external_auth=1"', policy)
        self.assertNotIn("access_token=", policy)
        self.assertNotIn("refresh_token=", policy)


if __name__ == "__main__":
    unittest.main()
