"""Frigate camera dropdown selection/persistence protocol without an HA runtime."""

import importlib
from pathlib import Path
import sys
import types
import unittest


folder = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
package = types.ModuleType("_kellie_picker_pure")
package.__path__ = [str(folder)]
sys.modules[package.__name__] = package
picker_module = importlib.import_module(package.__name__ + ".camera_picker")
Picker = picker_module.FrigateCameraPicker


class CameraPickerTests(unittest.TestCase):
    def test_four_slot_flow_and_canonical_text_wire(self):
        picker = Picker()
        seen = []
        picker.subscribe(0, lambda value: seen.append(value))
        picker.load("")
        self.assertEqual(seen, [""])
        self.assertEqual(picker.propose(0, "camera.driveway"), "camera.driveway")
        picker.load(picker.propose(0, "camera.driveway"))
        self.assertEqual(seen[-1], "camera.driveway")
        picker.load(picker.propose(1, "camera.doorbell"))
        picker.load(picker.propose(2, "camera.garage"))
        picker.load(picker.propose(3, "camera.living_room"))
        self.assertEqual([picker.at(i) for i in range(4)], [
            "camera.driveway", "camera.doorbell", "camera.garage", "camera.living_room"
        ])
        self.assertEqual(picker.propose(1, ""), "camera.driveway,camera.garage,camera.living_room")
        picker.load(picker.propose(1, ""))
        self.assertEqual(picker.at(1), "camera.garage")
        self.assertEqual(picker.at(3), "")

    def test_cannot_skip_slots_or_repeat_camera(self):
        picker = Picker()
        picker.load("")
        with self.assertRaisesRegex(ValueError, "earlier camera"):
            picker.propose(3, "camera.doorbell")
        picker.load(picker.propose(0, "camera.driveway"))
        with self.assertRaisesRegex(ValueError, "already selected"):
            picker.propose(1, "camera.driveway")
        with self.assertRaisesRegex(ValueError, "camera"):
            picker.propose(1, "switch.light")
        with self.assertRaisesRegex(ValueError, "four"):
            picker.load("camera.a,camera.b,camera.c,camera.d,camera.e")

    def test_unsubscription_and_per_device_isolation(self):
        first = Picker()
        second = Picker()
        seen = []
        cb = lambda camera: seen.append(camera)
        first.subscribe(0, cb)
        first.load("camera.a")
        second.load("camera.z")
        self.assertEqual(first.at(0), "camera.a")
        self.assertEqual(second.at(0), "camera.z")
        first.unsubscribe(0, cb)
        first.load("camera.b")
        self.assertEqual(seen, ["camera.a"])

    def test_integration_uses_live_ha_camera_state_and_existing_text(self):
        select = (folder / "select.py").read_text()
        text = (folder / "text.py").read_text()
        self.assertIn('async_all("camera")', select)
        self.assertIn("FrigateCameraSelect(entry, slot)", select)
        self.assertIn("EVENT_STATE_CHANGED", select)
        self.assertIn("picker.propose", select)
        self.assertIn("await text_entity.async_set_value(proposed)", select)
        self.assertIn('picker.load(self._attr_native_value)', text)
        self.assertIn('get_camera_picker(self.hass, self._entry_id).load(checked)', text)


if __name__ == "__main__":
    unittest.main()
