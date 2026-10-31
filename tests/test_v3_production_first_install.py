"""First-install owner approval contract for the *normal* HACS component.

Unlike isolated v3qa tests, these instantiate the actual production v3 module
with an empty hass.data and no config entry. No HA users or tokens are touched.
"""
from __future__ import annotations
import asyncio
import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

ROOT = Path(__file__).resolve().parents[1]
PARENT = ROOT / "custom_components/kellies_voice"
V3 = PARENT / "v3"
PKG = "kv_production_first_install_test"


class FirstInstallOwnerUiTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        package = types.ModuleType(PKG)
        package.__path__ = [str(V3)]
        ws = types.ModuleType(PKG + ".owner_ws")
        panel = types.ModuleType(PKG + ".owner_panel")
        leases = types.ModuleType(PKG + ".token_lease_journal")
        ws.async_register_owner_claim = MagicMock()
        panel.async_register_pairing_approval_panel = AsyncMock()
        journal = types.SimpleNamespace(
            load=AsyncMock(), recover_unfinished=AsyncMock(return_value={}))
        leases.TokenLeaseJournal = MagicMock(return_value=journal)
        self.panel, self.ws, self.leases, self.journal = panel, ws, leases, journal
        self.modules = patch.dict(sys.modules, {
            PKG: package, PKG + ".owner_ws": ws,
            PKG + ".owner_panel": panel,
            PKG + ".token_lease_journal": leases,
        })
        self.modules.start()
        self.addCleanup(self.modules.stop)
        spec = importlib.util.spec_from_file_location(
            PKG, V3 / "__init__.py", submodule_search_locations=[str(V3)])
        mod = importlib.util.module_from_spec(spec)
        sys.modules[PKG] = mod
        spec.loader.exec_module(mod)
        self.mod = mod
        self.hass = types.SimpleNamespace(
            data={}, config=types.SimpleNamespace(
                path=lambda *x: "/tmp/no-real-token-first-install.json")
        )

    async def test_first_discovery_without_config_entries_initializes_owner_panel(self):
        self.assertEqual(self.hass.data, {})
        self.assertFalse(self.mod.owner_controls_ready(self.hass))
        self.assertTrue(await self.mod.async_register_owner_ui(self.hass))
        self.assertTrue(self.mod.owner_controls_ready(self.hass))
        self.ws.async_register_owner_claim.assert_called_once_with(self.hass)
        self.panel.async_register_pairing_approval_panel.assert_awaited_once_with(self.hass)
        self.leases.TokenLeaseJournal.assert_called_once()
        self.journal.load.assert_awaited_once()
        self.journal.recover_unfinished.assert_awaited_once()

    async def test_concurrent_first_discoveries_register_once(self):
        async def slow_panel(hass):
            await asyncio.sleep(0)
        self.panel.async_register_pairing_approval_panel.side_effect = slow_panel
        outcomes = await asyncio.gather(*(
            self.mod.async_register_owner_ui(self.hass) for _ in range(12)))
        self.assertEqual(outcomes, [True] * 12)
        self.ws.async_register_owner_claim.assert_called_once()
        self.panel.async_register_pairing_approval_panel.assert_awaited_once()
        self.leases.TokenLeaseJournal.assert_called_once()

    async def test_panel_registration_failure_does_not_authorize_pairing(self):
        self.panel.async_register_pairing_approval_panel.side_effect = RuntimeError(
            "simulated HTTP or frontend route unavailable")
        with self.assertLogs(self.mod.__name__, level="ERROR"):
            self.assertFalse(await self.mod.async_register_owner_ui(self.hass))
        self.assertFalse(self.mod.owner_controls_ready(self.hass))
        self.assertNotIn("v3_token_journal", self.hass.data["kellies_voice"])

    async def test_token_recovery_failure_disables_new_pairing(self):
        self.journal.recover_unfinished.return_value = {"orphan": "retry_required"}
        with self.assertLogs(self.mod.__name__, level="ERROR"):
            self.assertFalse(await self.mod.async_register_owner_ui(self.hass))
        self.assertFalse(self.mod.owner_controls_ready(self.hass))
        self.assertNotIn("v3_token_journal", self.hass.data["kellies_voice"])

    def test_normal_hacs_flow_uses_lazy_first_discovery_setup(self):
        config = (PARENT / "config_flow.py").read_text()
        flow = (V3 / "flow.py").read_text()
        panel = (V3 / "owner_panel.py").read_text()
        manifest = (PARENT / "manifest.json").read_text()
        self.assertIn("V3OwnerPairingFlowMixin", config)
        self.assertIn("await async_register_owner_ui(self.hass)", flow)
        self.assertIn("owner_controls_ready(self.hass)", flow)
        self.assertIn("await hass.http.async_register_static_paths", panel)
        self.assertIn("require_admin=True", panel)
        self.assertIn('"panel_custom"', manifest)
        self.assertIn('"http"', manifest)
        self.assertNotIn("kellies_voice_v3qa", config)


if __name__ == "__main__":
    unittest.main()
