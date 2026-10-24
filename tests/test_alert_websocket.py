"""Scoped event protocol tests without depending on a live HA installation."""

import importlib.util
import pathlib
import sys
import types
import unittest
from unittest.mock import Mock
from types import SimpleNamespace
import ast

ROOT = pathlib.Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
# Package-less imports with only stable project files loaded.
package = types.ModuleType("custom_components")
package.__path__ = []
inner = types.ModuleType("custom_components.kellies_voice")
inner.__path__ = [str(ROOT)]
sys.modules.setdefault("custom_components", package)
sys.modules.setdefault("custom_components.kellies_voice", inner)
def load(name):
    fullname = "custom_components.kellies_voice." + name
    path = ROOT / (name + ".py")
    spec = importlib.util.spec_from_file_location(fullname, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[fullname] = module
    spec.loader.exec_module(module)
    return module
const = load("const")
transport = load("alert_transport")


class ScopedPayloadTests(unittest.TestCase):
    TV = "0b6c18b8-9a4a-4e7e-a0d0-521ebec949bd"
    OTHER = "1ca258fa-1afe-4cdb-8cd7-3a19b8d6e728"

    def test_announcement_is_scoped_and_scrubs_unknown_secrets(self):
        data = {"device_id": self.TV, "title": "Door",
                "text": "Open", "token": "secret",
                "url": "http://signed-url", "duration_seconds": 9}
        out = transport.sanitize_event(const.EVENT_ANNOUNCEMENT, data, self.TV)
        self.assertEqual({"device_id": self.TV, "title": "Door",
                          "text": "Open", "duration_seconds": 9}, out)
        self.assertIsNone(transport.sanitize_event(const.EVENT_ANNOUNCEMENT, data, self.OTHER))

    def test_broadcast_is_explicit_and_targeted_per_subscriber(self):
        data = {"title": "Laundry", "text": "Finished"}
        a = transport.sanitize_event(const.EVENT_ANNOUNCEMENT, data, self.TV)
        b = transport.sanitize_event(const.EVENT_ANNOUNCEMENT, data, self.OTHER)
        self.assertEqual(self.TV, a["device_id"])
        self.assertEqual(self.OTHER, b["device_id"])

    def test_forecast_and_timer_have_known_fields_only(self):
        weather = {"location": "Home", "units": "°F", "issued": 123,
                   "days": [{"label": "Monday", "high": 70, "password": "bad"}]}
        w = transport.sanitize_event(const.EVENT_FORECAST, weather, self.TV)
        self.assertEqual([{"label": "Monday", "high": "70"}], w["days"])
        timer = {"device_id": self.TV, "operation": "start", "name": "Pizza",
                 "issued": 100, "ends_at_ms": 111000, "speak_alert": False,
                 "alert_phrase": "Pizza ready", "extra": "drop"}
        t = transport.sanitize_event("kellies_voice_timer", timer, self.TV)
        self.assertEqual("Pizza", t["name"])
        self.assertNotIn("extra", t)

    def test_live_display_options_are_scoped_and_secret_free(self):
        result = transport.sanitize_event(const.EVENT_LIVE_CAMERA, {
            "device_id": self.TV, "entity_id": "camera.driveway",
            "title": "", "show_header": False, "image_fit": "fit",
            "refresh_seconds": 2, "duration_seconds": 6,
            "camera_url": "https://example.invalid/secret"
        }, self.TV)
        self.assertIsNotNone(result)
        self.assertEqual(False, result["show_header"])
        self.assertEqual("fit", result["image_fit"])
        self.assertEqual(2, result["refresh_seconds"])
        self.assertEqual(6, result["duration_seconds"])
        self.assertNotIn("camera_url", result)
        self.assertIsNone(transport.sanitize_event(const.EVENT_LIVE_CAMERA, {
            "device_id": self.OTHER, "entity_id": "camera.driveway"
        }, self.TV))

    def test_invalid_and_oversized_data_dropped(self):
        self.assertIsNone(transport.sanitize_event("evil", {}, self.TV))
        self.assertIsNone(transport.sanitize_event(const.EVENT_FORECAST, {"days": [{}]*8}, self.TV))
        self.assertLessEqual(len(transport.sanitize_event(
            const.EVENT_ANNOUNCEMENT, {"text": "🚀" * 10000}, self.TV
        )["text"]), transport.STRING_LIMITS["text"])
        self.assertIsNone(transport.sanitize_event("kellies_voice_timer",
                           {"ends_at_ms": "wrong"}, self.TV))


class PermissionsTests(unittest.TestCase):
    def setUp(self):
        source = ast.parse((ROOT / "websocket.py").read_text(encoding="utf-8"))
        node = next(item for item in source.body
                    if isinstance(item, ast.FunctionDef) and item.name == "_tv_can_read")
        # Production annotations are evaluated at definition time; HA is intentionally
        # not installed in this fast offline unit-test job.
        self.funcs = {"HomeAssistant": object}
        exec(compile(ast.Module(body=[node], type_ignores=[]), "permissions", "exec"), self.funcs)
        self.check = self.funcs["_tv_can_read"]
        self.id = "tv-one"
        self.state = SimpleNamespace(entity_id="select.tv_one_tv_alert_position",
                   attributes={"kellie_device_id": self.id, "kellie_setting": "alert_position"})
        self.hass = SimpleNamespace(
            config_entries=SimpleNamespace(async_entries=lambda domain: [
                SimpleNamespace(data={"device_id": self.id})]),
            states=SimpleNamespace(async_all=lambda domain: [self.state]))

    def user(self, read):
        return SimpleNamespace(is_admin=False, permissions=SimpleNamespace(
            access_all_entities=lambda policy: False,
            check_entity=lambda entity, policy: read))

    def test_nonadmin_allowed_only_with_entity_read(self):
        self.funcs["DOMAIN"] = "kellies_voice"
        self.funcs["POLICY_READ"] = "read"
        self.assertTrue(self.check(self.hass, self.user(True), self.id))
        self.assertFalse(self.check(self.hass, self.user(False), self.id))
        self.assertFalse(self.check(self.hass, self.user(True), "unpaired"))

    def test_per_tv_select_required(self):
        self.funcs["DOMAIN"] = "kellies_voice"
        self.funcs["POLICY_READ"] = "read"
        self.state.attributes["kellie_setting"] = "noise"
        self.assertFalse(self.check(self.hass, self.user(True), self.id))


if __name__ == "__main__":
    unittest.main()
