"""Regression for HA's device Quick Action field refresh/reset bug.

A setter must merge against the current config entry and must not reload
device entities after every label, target and action edit.
"""
import ast
import asyncio
import importlib.util
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1]
DIR = ROOT / "custom_components" / "kellies_voice"
spec = importlib.util.spec_from_file_location("shortcut_hot_edit_policy", DIR / "home_shortcuts.py")
policy = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = policy
spec.loader.exec_module(policy)


def compile_function(filename, name, cls=None, globals_=None):
    src = ast.parse((DIR / filename).read_text())
    nodes = src.body
    if cls:
        nodes = next(node.body for node in nodes
                     if isinstance(node, ast.ClassDef) and node.name == cls)
    method = next(node for node in nodes if isinstance(node, ast.AsyncFunctionDef)
                  and node.name == name)
    method.decorator_list = []
    method.returns = None
    for item in method.args.args:
        item.annotation = None
    values = globals_ or {}
    exec(compile(ast.Module(body=[method], type_ignores=[]), filename, "exec"), values)
    return values[name]


class FakeConfigEntries:
    def __init__(self):
        self.entry = types.SimpleNamespace(
            entry_id="tv-qa", options={"native_timer_intents": False, "shortcut_4_action": "Turn on"}
        )
        self.reloads = []
        self.writes = 0

    def async_get_entry(self, entry_id):
        assert entry_id == "tv-qa"
        return self.entry

    def async_update_entry(self, entry, options):
        assert entry is self.entry
        entry.options = options
        self.writes += 1

    async def async_reload(self, entry_id):
        self.reloads.append(entry_id)


class HotEditTests(unittest.IsolatedAsyncioTestCase):
    async def test_label_target_action_edit_one_slot_without_losing_values(self):
        cfg = FakeConfigEntries()
        hass = types.SimpleNamespace(
            config_entries=cfg,
            auth=types.SimpleNamespace(
                async_get_user=self._get_admin
            ),
            data={"kellies_voice": {"native_timer_options_at_setup": {"tv-qa": False}}},
        )
        setter = compile_function(
            "quick_action_device.py", "_set", "QuickActionDeviceSetting",
            {"updated_device_option": policy.updated_device_option,
             "HomeAssistantError": ValueError,
             "DOMAIN": "kellies_voice",
             "QUICK_ACTION_CACHE": "quick_action_entry_cache"},
        )
        changed = compile_function(
            "__init__.py", "_options_updated", globals_={
                "DOMAIN": "kellies_voice",
                "_TIMER_OPTIONS_AT_SETUP": "native_timer_options_at_setup"
            },
        )
        entity = types.SimpleNamespace(
            hass=hass, _context=types.SimpleNamespace(user_id="owner"),
            _entry=types.SimpleNamespace(entry_id="tv-qa"),
            _slot=4, _field="label",
        )
        await setter(entity, "Movie Time")
        await changed(hass, cfg.entry)
        self.assertEqual("Movie Time", cfg.entry.options["shortcut_4_label"])
        self.assertEqual([], cfg.reloads)

        entity._field = "entity"
        await setter(entity, "scene.movie_night")
        await changed(hass, cfg.entry)
        self.assertEqual("Movie Time", cfg.entry.options["shortcut_4_label"])
        self.assertEqual("scene.movie_night", cfg.entry.options["shortcut_4_entity"])
        self.assertEqual([], cfg.reloads)

        entity._field = "action"
        await setter(entity, "Turn on")
        await changed(hass, cfg.entry)
        self.assertEqual(1, len(policy.entries_from_options(cfg.entry.options)))
        self.assertEqual("Movie Time", policy.entries_from_options(cfg.entry.options)[0]["label"])
        self.assertEqual(2, cfg.writes)  # Third write is unchanged default.
        self.assertEqual([], cfg.reloads)


    async def test_stale_config_snapshot_cannot_overwrite_recent_slot_edits(self):
        cfg = FakeConfigEntries()
        original = dict(cfg.entry.options)
        hass = types.SimpleNamespace(
            config_entries=cfg,
            auth=types.SimpleNamespace(async_get_user=self._get_admin),
            data={"kellies_voice": {"native_timer_options_at_setup": {"tv-qa": False},
                                     "quick_action_entry_cache": {"tv-qa": dict(original)}}},
        )
        setter = compile_function(
            "quick_action_device.py", "_set", "QuickActionDeviceSetting",
            {"updated_device_option": policy.updated_device_option,
             "HomeAssistantError": ValueError,
             "DOMAIN": "kellies_voice",
             "QUICK_ACTION_CACHE": "quick_action_entry_cache"},
        )
        entity = types.SimpleNamespace(
            hass=hass, _context=types.SimpleNamespace(user_id="owner"),
            _entry=types.SimpleNamespace(entry_id="tv-qa"),
            _slot=4, _field="label",
        )

        await setter(entity, "Movie Time")
        # Emulate a stale ConfigEntry options view on another native platform.
        cfg.entry.options = dict(original)
        entity._field = "entity"
        await setter(entity, "light.office_light")
        self.assertEqual("Movie Time", cfg.entry.options["shortcut_4_label"])
        self.assertEqual("light.office_light", cfg.entry.options["shortcut_4_entity"])

        cfg.entry.options = dict(original)
        entity._field = "action"
        await setter(entity, "Toggle")
        self.assertEqual(
            ("Movie Time", "light.office_light", "Toggle"),
            (cfg.entry.options["shortcut_4_label"],
             cfg.entry.options["shortcut_4_entity"],
             cfg.entry.options["shortcut_4_action"]),
        )
        self.assertEqual(3, cfg.writes)

    async def _get_admin(self, user_id):
        return types.SimpleNamespace(is_admin=user_id == "owner")

    async def test_native_timer_option_still_reloads(self):
        cfg = FakeConfigEntries()
        hass = types.SimpleNamespace(
            config_entries=cfg,
            data={"kellies_voice": {"native_timer_options_at_setup": {"tv-qa": False}}}
        )
        changed = compile_function(
            "__init__.py", "_options_updated", globals_={
                "DOMAIN": "kellies_voice",
                "_TIMER_OPTIONS_AT_SETUP": "native_timer_options_at_setup"
            },
        )
        cfg.entry.options["native_timer_intents"] = True
        await changed(hass, cfg.entry)
        self.assertEqual(["tv-qa"], cfg.reloads)


if __name__ == "__main__":
    unittest.main()
