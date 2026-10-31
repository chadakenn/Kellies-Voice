"""Production HA v3 config-flow contract. Runs offline; no credentials minted.

The isolated v3qa flow still has deep synthetic owner/crypto tests. These
checks ensure the normal HACS integration actually exposes that reviewed
owner-approval path instead of returning the old verification-only abort.
"""
import ast
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
HA = ROOT / "custom_components/kellies_voice"
UI = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
FLOW = (HA / "config_flow.py").read_text(encoding="utf-8")
OWNER = (HA / "v3/flow.py").read_text(encoding="utf-8")
SETUP = (HA / "__init__.py").read_text(encoding="utf-8")


class ProductionPairingFlowTests(unittest.TestCase):
    def test_stable_and_v3_discovery_both_exist(self):
        tree = ast.parse(FLOW)
        handler = next(x for x in tree.body if isinstance(x, ast.ClassDef)
                       and x.name == "KellieVoiceConfigFlow")
        self.assertIn("V3OwnerPairingFlowMixin", [x.id for x in handler.bases])
        methods = {x.name for x in handler.body if isinstance(x, ast.AsyncFunctionDef)}
        self.assertIn("async_step_user", methods)
        self.assertIn("async_step_manual_confirm", methods)
        self.assertIn("async_step_zeroconf", methods)
        self.assertIn("async_step_confirm", methods)
        self.assertIn("async_step_v3_preview", OWNER)
        self.assertIn("async_step_v3_code", OWNER)
        self.assertIn("async_step_owner_approval", OWNER)
        self.assertIn('if _text(discovery_info.properties, "protocol") == "3"', FLOW)
        self.assertIn('"v3-preview:" + session', FLOW)

    def test_normal_hacs_enrolls_only_after_owner_and_tv_ack(self):
        # Credential issuance requires an approved, authenticated owner and
        # physical-TV proof; no success entry in preview/code steps.
        owner_tree = ast.parse(OWNER)
        mixin = next(x for x in owner_tree.body if isinstance(x, ast.ClassDef)
                     and x.name == "V3OwnerPairingFlowMixin")
        methods = {x.name: x for x in mixin.body
                   if isinstance(x, ast.AsyncFunctionDef)}
        self.assertIn("async_step_owner_approval", methods)
        approval = ast.get_source_segment(OWNER, methods["async_step_owner_approval"])
        self.assertLess(approval.index("finish_owner_receipt("),
                        approval.index("finish_renewable_login("))
        self.assertLess(approval.index("finish_renewable_login("),
                        approval.index("async_create_entry("))
        self.assertIn("approved_owner_login(", approval)
        self.assertIn("owner_controls_ready(self.hass)", OWNER)
        self.assertIn('self.hass.data[DOMAIN]["v3_token_journal"]', approval)
        self.assertIn("self._abort_if_unique_id_configured()", approval)
        self.assertNotIn("return self.async_abort(reason=\"v3_preview_verified\")", FLOW)
        self.assertNotIn("vol.Required(\"qa_instructions\"", OWNER)

    def test_owner_ui_is_part_of_standard_hacs_not_second_yaml(self):
        self.assertIn("await async_register_owner_ui(hass)", SETUP)
        self.assertIn("async_register_owner_claim(hass)", (HA/"v3/__init__.py").read_text())
        self.assertIn("async_register_pairing_approval_panel(hass)", (HA/"v3/__init__.py").read_text())
        panel = (HA/"v3/owner_panel.py").read_text()
        self.assertIn("kellies-voice-approval", panel)
        self.assertIn("require_admin=True", panel)
        ws = (HA/"v3/owner_ws.py").read_text()
        self.assertIn("connection.user", ws)
        self.assertIn("kellies_voice/pair/claim_verified_owner", ws)
        self.assertNotIn("kellies_voice_v3qa/pair/", ws)

    def test_tv_signed_release_can_start_no_typing_flow(self):
        voice = (UI/"VoiceActivity.java").read_text()
        activity = (UI/"PairingV3QaActivity.java").read_text()
        wizard = (UI/"FirstRunSetupActivity.java").read_text()
        self.assertIn("Secure Home Assistant pairing (no typing)", voice)
        self.assertIn("PairingV3LanWindow.start(", activity)
        self.assertNotIn("if (!BuildConfig.DEBUG && !BuildConfig.IS_QA)", activity)
        self.assertNotIn("if (!BuildConfig.DEBUG && !BuildConfig.IS_QA)", wizard)
        self.assertIn("new CredentialStore(activity).load() != null", wizard)
        self.assertIn("new TvRenewableLoginVault(activity).hasActiveLogin()", wizard)
        self.assertNotIn("WakeWordService", wizard)

    def test_local_http_only_fails_closed_not_implicit_token_downgrade(self):
        # A secure non-paid local-only option is tracked separately in #262.
        # This candidate must not quietly send a renewable token over HTTP.
        https = (HA/"v3/ha_https.py").read_text()
        issuer = (HA/"v3/scoped_login_issuer.py").read_text()
        self.assertIn("require_ssl=True", https)
        self.assertIn('origin.startswith("https://")', issuer)
        self.assertIn('reason="https_required"', OWNER)

    def test_normal_english_translations_shipped(self):
        source = json.loads((HA/"strings.json").read_text())
        shipped = json.loads((HA/"translations/en.json").read_text())
        self.assertEqual(source, shipped)
        self.assertIn("owner_approval", source["config"]["step"])


if __name__ == "__main__":
    unittest.main()
