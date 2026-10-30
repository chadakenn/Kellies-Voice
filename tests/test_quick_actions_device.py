"""Native HA device-page Quick Actions controls, secure against bad targets."""

import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMP = ROOT / "custom_components/kellies_voice"
spec = importlib.util.spec_from_file_location("quick_actions_device_policy", COMP / "home_shortcuts.py")
assert spec and spec.loader
policy = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = policy
spec.loader.exec_module(policy)

device_code = (COMP / "quick_action_device.py").read_text()
text_code = (COMP / "text.py").read_text()
select_code = (COMP / "select.py").read_text()


class DeviceSettingsPolicyTests(unittest.TestCase):
    def test_partial_edits_do_not_create_executable_actions(self):
        options = policy.updated_device_option({}, 1, "label", "Office Lights")
        self.assertEqual([], policy.entries_from_options(options))
        options = policy.updated_device_option(options, 1, "entity", "light.office")
        self.assertEqual(
            [(1, "light.office", "turn_on")],
            [(item["slot"], item["entity_id"], item["service"])
             for item in policy.entries_from_options(options)],
        )
        options = policy.updated_device_option(options, 1, "action", "Toggle")
        self.assertEqual("toggle", policy.entries_from_options(options)[0]["service"])
        options = policy.updated_device_option(options, 1, "label", "")
        self.assertEqual([], policy.entries_from_options(options))
        options = policy.updated_device_option(options, 1, "label", "Office")
        options = policy.updated_device_option(options, 1, "entity", "")
        self.assertEqual([], policy.entries_from_options(options))

    def test_unsafe_targets_actions_and_slot_numbers_rejected(self):
        for target in ("lock.front_door", "switch.all", "cover.garage",
                       "script.foo;lock.unlock", "light.", "LIGHT.ROOM",
                       "homeassistant.restart", "camera.front"):
            with self.subTest(target=target), self.assertRaises(ValueError):
                policy.updated_device_option({}, 1, "entity", target)
        for slot in (-1, 0, 5, True, "1", None):
            with self.subTest(slot=slot), self.assertRaises(ValueError):
                policy.updated_device_option({}, slot, "label", "X")
        for value in ("Run custom service", "", "Unlock"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                policy.updated_device_option({}, 1, "action", value)
        for bad in ("x" * 27, "bad\nlabel", "bad\u007flabel"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                policy.updated_device_option({}, 2, "label", bad)

    def test_scene_and_script_actions_stay_turn_on_only(self):
        for target in ("scene.movie", "script.evening"):
            options = policy.updated_device_option({}, 1, "label", "Movie")
            options = policy.updated_device_option(options, 1, "entity", target)
            self.assertEqual("turn_on", policy.entries_from_options(options)[0]["service"])
            with self.assertRaises(ValueError):
                policy.updated_device_option(options, 1, "action", "Toggle")
            with self.assertRaises(ValueError):
                policy.updated_device_option(options, 1, "action", "Turn off")
            self.assertEqual("Turn on", options.get("shortcut_1_action", "Turn on"))

    def test_one_tv_and_slot_cannot_edit_another(self):
        original = {
            "native_timer_intents": True,
            "shortcut_2_label": "TV 2",
            "shortcut_2_entity": "scene.tv_two",
            "shortcut_2_action": "Turn on",
        }
        updated = policy.updated_device_option(original, 1, "label", "My TV")
        self.assertEqual("TV 2", original["shortcut_2_label"])
        self.assertEqual("TV 2", updated["shortcut_2_label"])
        self.assertEqual(True, updated["native_timer_intents"])
        self.assertNotIn("shortcut_1_label", original)

    def test_entities_are_native_config_controls_on_device_page(self):
        self.assertIn("EntityCategory.CONFIG", device_code)
        self.assertIn('f"Quick Action {slot} · {name}"', device_code)
        self.assertIn("quick_action_labels(entry)", text_code)
        self.assertIn("quick_action_selects(entry)", select_code)
        self.assertIn("self.hass.config_entries.async_update_entry(", device_code)
        self.assertIn("updated_device_option(", device_code)
        self.assertIn("self.hass.auth.async_get_user(user_id)", device_code)
        self.assertIn("user is None or not user.is_admin", device_code)
        self.assertIn("await self._set(value)", device_code)
        self.assertIn("TARGET_DOMAINS = (\"light\", \"scene\", \"script\")", device_code)
        self.assertNotIn("hass.services.async_call(", device_code)


if __name__ == "__main__":
    unittest.main()
