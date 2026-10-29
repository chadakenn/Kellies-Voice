"""Prevent remote-key and microphone regressions in the TV's home shortcuts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/example/tvvoiceprobe"
HA = ROOT / "custom_components/kellies_voice"


class FullscreenShortcutsContractTests(unittest.TestCase):
    def test_fullscreen_only_and_original_controls_preserved(self):
        home = (APP / "VoiceActivity.java").read_text()
        client = (APP / "HaAssistClient.java").read_text()
        overlay = (APP / "VoiceOverlayService.java").read_text()
        self.assertIn("homeShortcutRow = new LinearLayout(this)", home)
        self.assertIn("homeShortcutButtons", home)
        self.assertIn('addButton(toolbar, "TALK", v -> startTalk())', home)
        self.assertIn('addButton(toolbar, "STOP", v -> assist.finishTalking())', home)
        self.assertIn('addButton(toolbar, "SETTINGS"', home)
        self.assertIn("homeShortcutButtons.get(selected - buttons.size()).performClick()", home)
        self.assertIn("onFocusChangeListener", home)
        self.assertIn("KEYCODE_DPAD_CENTER", home)
        self.assertIn("void homeShortcuts(JSONArray slots)", client)
        self.assertIn('"kellies_voice/press_home_shortcut"', client)
        self.assertIn('"kellies_voice/get_home_shortcuts"', client)
        self.assertIn("public synchronized boolean pressHomeShortcut(int slot)", client)
        self.assertIn("requestHomeShortcuts()", client)
        self.assertNotIn("homeShortcutRow", overlay)
        self.assertNotIn("pressHomeShortcut", overlay)

    def test_no_client_control_over_target_service_or_entity(self):
        client = (APP / "HaAssistClient.java").read_text()
        action = client.split("public synchronized boolean pressHomeShortcut(int slot)", 1)[1].split(
            "public synchronized boolean talk()", 1
        )[0]
        self.assertIn('request.put("slot", slot)', action)
        self.assertNotIn('request.put("entity_id"', action)
        self.assertNotIn('request.put("service"', action)
        self.assertNotIn('request.put("domain"', action)
        self.assertNotIn("record", action.lower())
        self.assertNotIn("AudioRecord", action)

    def test_invalid_ha_actions_never_bypass_server_checks(self):
        api = (HA / "home_shortcuts_ws.py").read_text()
        self.assertIn("_tv_can_read(hass, connection.user, tv_id)", api)
        self.assertIn("_authorized_slot(connection.user, shortcut)", api)
        self.assertIn("selection(entry.options, msg[\"slot\"])", api)
        self.assertIn("Context(user_id=connection.user.id)", api)
        self.assertIn("blocking=True", api)
        self.assertNotIn('msg["entity_id"]', api)
        self.assertNotIn('msg["service"]', api)

    def test_user_edits_config_in_home_assistant_not_tv(self):
        flow = (HA / "config_flow.py").read_text()
        self.assertIn("validate_slot(label, entity, action)", flow)
        self.assertIn("self.config_entry.options", flow)
        self.assertIn("updated = dict(current)", flow)
        self.assertIn("self.async_create_entry(title=\"\", data=updated)", flow)
        for slot in range(1, 5):
            self.assertIn(f"shortcut_{slot}", (HA / "strings.json").read_text())


if __name__ == "__main__":
    unittest.main()
