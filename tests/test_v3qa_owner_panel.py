"""HA owner-pairing custom panel registration; no browser or real HA needed."""
from __future__ import annotations

import asyncio
import importlib
import hashlib
from pathlib import Path
import sys
import types
from unittest.mock import AsyncMock, patch
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice_v3qa"
PACKAGE = "kv_v3qa_owner_panel_test"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package


class OwnerPanelTests(unittest.IsolatedAsyncioTestCase):
    async def test_registers_only_once_with_admin_ui_and_bundled_asset(self):
        panel_custom = types.ModuleType("homeassistant.components.panel_custom")
        panel_custom.async_register_panel = AsyncMock()
        http = types.ModuleType("homeassistant.components.http")
        http.StaticPathConfig = lambda url, filename, cache_headers: (
            url, filename, cache_headers
        )
        components = types.ModuleType("homeassistant.components")
        components.panel_custom = panel_custom
        components.http = http
        modules = {
            "homeassistant": types.ModuleType("homeassistant"),
            "homeassistant.components": components,
            "homeassistant.components.panel_custom": panel_custom,
            "homeassistant.components.http": http,
        }
        with patch.dict(sys.modules, modules):
            module = importlib.import_module(PACKAGE + ".owner_panel")
            hass = types.SimpleNamespace(data={}, async_add_executor_job=lambda fn, *args: asyncio.to_thread(fn, *args))
            hass.http = types.SimpleNamespace(async_register_static_paths=AsyncMock())
            await module.async_register_pairing_approval_panel(hass)
            await module.async_register_pairing_approval_panel(hass)
            self.assertEqual(1, hass.http.async_register_static_paths.await_count)
            self.assertEqual(1, panel_custom.async_register_panel.await_count)
            args = panel_custom.async_register_panel.await_args.kwargs
            self.assertEqual("kellies-voice-v3qa-owner-panel", args["webcomponent_name"])
            self.assertEqual("kellies-voice-v3qa-approval", args["frontend_url_path"])
            self.assertIs(args["require_admin"], True)
            self.assertEqual("kellies_voice_v3qa", args["config_panel_domain"])
            asset_bytes = (ROOT / "frontend" /
                           "kellies-voice-v3qa-owner-panel.js").read_bytes()
            expected_digest = hashlib.sha256(asset_bytes).hexdigest()[:12]
            self.assertEqual(
                "/api/kellies_voice_v3qa/owner-panel.js?v=" + expected_digest,
                args["module_url"])
            self.assertIs(args["embed_iframe"], False)
            static = hass.http.async_register_static_paths.await_args.args[0][0]
            self.assertEqual("/api/kellies_voice_v3qa/owner-panel.js", static[0])
            self.assertFalse(static[2])
            self.assertTrue(Path(static[1]).is_file())

    async def test_frontend_contains_no_credential_or_local_storage_operations(self):
        js = (ROOT / "frontend" / "kellies-voice-v3qa-owner-panel.js").read_text()
        self.assertIn("kellies_voice_v3qa/pair/list_verified", js)
        self.assertIn("kellies_voice_v3qa/pair/claim_verified_owner", js)
        self.assertIn("sendMessagePromise", js)
        self.assertIn("textContent", js)
        self.assertIn("pair.comparison_code", js)
        self.assertIn("/^[0-9]{8}$/", js)
        for forbidden in (
            "localStorage", "sessionStorage", '"/auth/token"',
            "refresh_token:", "access_token:", "document.cookie",
            "innerHTML = pair", "fetch(",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, js)


if __name__ == "__main__":
    unittest.main()
