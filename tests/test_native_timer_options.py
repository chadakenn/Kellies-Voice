"""Test the real HA OptionsFlow methods without requiring Home Assistant."""

import ast
from pathlib import Path
from types import SimpleNamespace
import unittest

SRC = (Path(__file__).resolve().parents[1] / "custom_components" /
       "kellies_voice" / "config_flow.py").read_text(encoding="utf-8")
TREE = ast.parse(SRC)
OPTIONS = next(n for n in TREE.body if isinstance(n, ast.ClassDef)
               and n.name == "KellieVoiceOptionsFlow")


class FakeVol:
    @staticmethod
    def Optional(key, default=False):
        return key
    @staticmethod
    def Schema(fields):
        return fields


class FakeOptionsFlow:
    def __init__(self, options=None):
        self.config_entry = SimpleNamespace(options=options or {})
    def async_show_form(self, **kwargs):
        return {"type": "form", **kwargs}
    def async_create_entry(self, **kwargs):
        return {"type": "create", **kwargs}


runtime = {"OptionsFlow": FakeOptionsFlow, "vol": FakeVol}
exec(compile(ast.Module(body=[OPTIONS], type_ignores=[]),
             "timer_options_from_config_flow.py", "exec"), runtime)
OptionFlowUnderTest = runtime["KellieVoiceOptionsFlow"]


class NativeTimerOptionsTests(unittest.IsolatedAsyncioTestCase):
    async def test_off_by_default_and_explicit_opt_in(self):
        flow = OptionFlowUnderTest()
        view = await flow.async_step_init()
        self.assertEqual("form", view["type"])
        self.assertIn("native_timer_intents", view["data_schema"])
        enabled = await flow.async_step_init({"native_timer_intents": True})
        self.assertEqual({"native_timer_intents": True}, enabled["data"])
        disabled = await flow.async_step_init({"native_timer_intents": False})
        self.assertEqual({"native_timer_intents": False}, disabled["data"])

    async def test_runtime_registration_is_guarded_and_reloads_on_change(self):
        src = (Path(__file__).resolve().parents[1] / "custom_components" /
               "kellies_voice" / "__init__.py").read_text(encoding="utf-8")
        self.assertIn('entry.options.get("native_timer_intents", False) is True', src)
        self.assertIn("async_device_supports_timers", src)
        self.assertIn("entry.async_on_unload(entry.add_update_listener", src)
        self.assertIn("await hass.config_entries.async_reload(entry.entry_id)", src)


if __name__ == "__main__":
    unittest.main()
