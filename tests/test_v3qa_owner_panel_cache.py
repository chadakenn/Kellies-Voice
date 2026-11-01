"""Owner panel must cache-bust registered JS module when the file changes."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PANEL = (ROOT / "qa/ha_v3qa/kellies_voice_v3qa/owner_panel.py").read_text()
JS = (ROOT / "qa/ha_v3qa/kellies_voice_v3qa/frontend/kellies-voice-v3qa-owner-panel.js").read_text()

class CacheTests(unittest.TestCase):
    def test_ha_registers_unique_asset_url_and_does_not_cache_static_path(self):
        self.assertIn("sha256(file_path.read_bytes()).hexdigest()[:12]", PANEL)
        self.assertIn('module_url=f"{_ASSET_PATH}?v={panel_version}"', PANEL)
        self.assertIn("cache_headers=False", PANEL)
        self.assertIn('standard_user_login: loginOptIn.checked', JS)
        self.assertNotIn("QA-ONLY OWNER PROOF. This does NOT sign in", JS)

if __name__ == "__main__":
    unittest.main()
