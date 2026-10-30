"""Startup-only owner panel registration; never add HTTP routes mid-pairing."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice_v3qa"
PKG = "kv_v3qa_startup_checks"


class OwnerStartupTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        fake_package = types.ModuleType(PKG)
        fake_package.__path__ = [str(ROOT)]
        ws = types.ModuleType(PKG + ".owner_ws")
        panel = types.ModuleType(PKG + ".owner_panel")
        ws.async_register_owner_claim = MagicMock()
        panel.async_register_pairing_approval_panel = AsyncMock()
        self.ws, self.panel = ws, panel
        self.patch_modules = patch.dict(sys.modules, {
            PKG: fake_package, PKG + ".owner_ws": ws,
            PKG + ".owner_panel": panel,
        })
        self.patch_modules.start()
        self.addCleanup(self.patch_modules.stop)
        spec = importlib.util.spec_from_file_location(
            PKG, ROOT / "__init__.py",
            submodule_search_locations=[str(ROOT)]
        )
        module = importlib.util.module_from_spec(spec)
        sys.modules[PKG] = module
        spec.loader.exec_module(module)
        self.qa = module
        self.hass = types.SimpleNamespace(data={})

    async def test_success_only_after_both_panel_and_ws_registered(self):
        self.assertFalse(self.qa.owner_controls_ready(self.hass))
        self.assertTrue(await self.qa.async_setup(
            self.hass, {"kellies_voice_v3qa": {}}))
        self.ws.async_register_owner_claim.assert_called_once_with(self.hass)
        self.panel.async_register_pairing_approval_panel.assert_awaited_once_with(
            self.hass)
        self.assertTrue(self.qa.owner_controls_ready(self.hass))

    async def test_registration_error_blocks_owner_gate(self):
        self.panel.async_register_pairing_approval_panel.side_effect = (
            RuntimeError("Router is frozen")
        )
        with self.assertLogs(self.qa.__name__, level="ERROR") as logs:
            self.assertFalse(await self.qa.async_setup(
                self.hass, {"kellies_voice_v3qa": {}}))
        self.assertFalse(self.qa.owner_controls_ready(self.hass))
        self.assertTrue(any("owner-panel startup failed" in line
                            for line in logs.output))

    def test_flow_never_registers_static_paths_after_pairing(self):
        source = (ROOT / "config_flow.py").read_text()
        self.assertIn("owner_controls_ready(self.hass)", source)
        self.assertNotIn("async_register_qa_owner_controls", source)
        self.assertNotIn("async_register_static_paths", source)
        self.assertNotIn("async_register_pairing_approval_panel", source)


if __name__ == "__main__":
    unittest.main()
