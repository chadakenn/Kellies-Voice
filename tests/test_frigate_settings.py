"""Frigate HA controls reject unsafe URLs and noncamera entity IDs."""

from pathlib import Path
import importlib.util
import unittest


path = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice/frigate_settings.py"
spec = importlib.util.spec_from_file_location("frigate_settings", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
validate = module.validate_frigate_setting


class FrigateSettingsTest(unittest.TestCase):
    def test_local_url_and_blank(self):
        self.assertEqual(validate("screensaver_url", "http://192.168.1.38:5000/"),
                         "http://192.168.1.38:5000/")
        self.assertEqual(validate("screensaver_url", ""), "")
        for bad in ("javascript:alert(1)", "http://user:pass@host/",
                    "http://host/?token=abc", "https://host/#access_token=a",
                    "file:///etc/passwd"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate("screensaver_url", bad)

    def test_cameras_are_bounded_and_cleaned(self):
        self.assertEqual(validate("screensaver_cameras",
                                  "camera.driveway, camera.doorbell"),
                         "camera.driveway,camera.doorbell")
        self.assertEqual(validate("screensaver_cameras", ""), "")
        for bad in ("sensor.driveway", "camera.a,camera.b,camera.c,camera.d,camera.e",
                    "camera.X", "camera.one;camera.two"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate("screensaver_cameras", bad)

    def test_integrations_register_all_entities(self):
        integration = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
        self.assertIn('"text"', (integration / "const.py").read_text())
        switch = (integration / "switch.py").read_text()
        self.assertIn("KellieScreensaverSwitch(entry)", switch)
        text = (integration / "text.py").read_text()
        self.assertIn("screensaver_url", text)
        self.assertIn("screensaver_cameras", text)


if __name__ == "__main__":
    unittest.main()
