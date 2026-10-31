"""TV shortcuts: strict HA-owned configuration and no arbitrary TV control."""
import ast
import importlib.util
from pathlib import Path
import sys
import time
import types
from types import MappingProxyType
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMP = ROOT / "custom_components" / "kellies_voice"
spec = importlib.util.spec_from_file_location("ha_home_shortcuts_test", COMP / "home_shortcuts.py")
assert spec and spec.loader
shortcuts = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = shortcuts
spec.loader.exec_module(shortcuts)


def options():
    return {
        "shortcut_1_label": "Living room on", "shortcut_1_entity": "light.living_room",
        "shortcut_1_action": "Turn on",
        "shortcut_2_label": "Movie Time", "shortcut_2_entity": "scene.movie_time",
        "shortcut_2_action": "Turn on",
        "shortcut_3_label": "Evening", "shortcut_3_entity": "script.evening",
        "shortcut_3_action": "Turn on",
    }


class ShortcutPolicyTests(unittest.TestCase):
    def test_readonly_home_assistant_config_entry_options(self):
        # Real HA ConfigEntry.options is MappingProxyType, not dict.
        readonly = MappingProxyType(options())
        values = shortcuts.entries_from_options(readonly)
        self.assertEqual([1, 2, 3], [item["slot"] for item in values])
        self.assertEqual("light.living_room", shortcuts.selection(readonly, 1)["entity_id"])
        self.assertEqual([], shortcuts.entries_from_options(None))
        self.assertEqual([], shortcuts.entries_from_options(["untrusted"]))

    def test_only_expected_domains_and_services(self):
        values = shortcuts.entries_from_options(options())
        self.assertEqual([1, 2, 3], [x["slot"] for x in values])
        self.assertEqual([
            ("light", "turn_on"), ("scene", "turn_on"), ("script", "turn_on")
        ], [(x["domain"], x["service"]) for x in values])
        self.assertIsNone(shortcuts.selection(options(), 4))

    def test_untrusted_service_and_security_entities_rejected(self):
        for entity in ("lock.front_door", "cover.garage", "alarm_control_panel.house",
                       "camera.doorbell", "switch.heater", "homeassistant.restart",
                       "light.livingroom;unlock", "light.room/../../lock",
                       "light.room extra", "light.", "light.VERYBIG", ""):
            with self.subTest(entity=entity):
                if entity:
                    with self.assertRaises(ValueError):
                        shortcuts.validate_slot("Unsafe", entity, "Turn on")
                else:
                    with self.assertRaises(ValueError):
                        shortcuts.validate_slot("Unsafe", entity, "Turn on")
        for domain in ("scene.movie", "script.evening"):
            with self.assertRaises(ValueError):
                shortcuts.validate_slot("Off", domain, "Turn off")
        with self.assertRaises(ValueError):
            shortcuts.validate_slot("A" * 27, "light.room", "Turn on")
        with self.assertRaises(ValueError):
            shortcuts.validate_slot("Bad\nLabel", "light.room", "Turn on")

    def test_missing_and_bad_slots_cannot_be_executed(self):
        config = options()
        config["shortcut_4_label"] = "Door"
        config["shortcut_4_entity"] = "lock.front_door"
        self.assertIsNone(shortcuts.selection(config, 4))
        for slot in (-1, 0, 5, "1", True, None):
            self.assertIsNone(shortcuts.selection(config, slot))
        self.assertEqual(4, shortcuts.COUNT)
        self.assertEqual(3, len(shortcuts.entries_from_options(config)))


class FakeUser:
    id = "ha-user"
    is_admin = False

    def __init__(self, allowed):
        self.allowed = allowed
        self.permissions = self

    def check_entity(self, entity, policy):
        return entity in self.allowed and policy == "control"


class FakeConnection:
    def __init__(self, user):
        self.user = user
        self.results = []
        self.errors = []

    def send_result(self, identifier, data=None):
        self.results.append((identifier, data))

    def send_error(self, identifier, code, message):
        self.errors.append((identifier, code, message))


class FakeServices:
    def __init__(self):
        self.calls = []

    async def async_call(self, domain, service, payload, **kwargs):
        self.calls.append((domain, service, payload, kwargs))


class FakeEntries:
    def __init__(self, entries):
        self.entries = entries

    def async_entries(self, domain):
        assert domain == "kellies_voice"
        return self.entries


class FakeStates:
    def get(self, entity_id):
        icons = {"light.living_room": "mdi:lamp", "scene.movie_time": "mdi:movie-open"}
        return types.SimpleNamespace(attributes={"icon": icons[entity_id]}) if entity_id in icons else None


class FakeHass:
    def __init__(self):
        self.config_entries = FakeEntries([
            types.SimpleNamespace(data={"device_id": "tv0001"}, options=MappingProxyType(options())),
            types.SimpleNamespace(
                data={"device_id": "tv0002"},
                options=MappingProxyType({"shortcut_1_label": "Other", "shortcut_1_entity": "light.other",
                         "shortcut_1_action": "Turn off"})),
        ])
        self.data = {}
        self.services = FakeServices()
        self.states = FakeStates()


def make_ws_functions():
    tree = ast.parse((COMP / "home_shortcuts_ws.py").read_text())
    methods = []
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name in (
            "_entry_for_tv", "_authorized_slot", "websocket_get_home_shortcuts",
            "websocket_press_home_shortcut",
        ):
            node.decorator_list = []
            methods.append(node)
    assert len(methods) == 4
    class Errors:
        ERR_INVALID_FORMAT = "bad_request"
        ERR_UNAUTHORIZED = "unauthorized"
    ns = {
        "DOMAIN": "kellies_voice",
        "selection": shortcuts.selection,
        "resolve_shortcut_icon": lambda override, actual, domain: (
            "mdi:lamp" if actual == "mdi:lamp" else
            ("mdi:movie-open" if actual == "mdi:movie-open" else "mdi:script-text")
        ),
        "entries_from_options": shortcuts.entries_from_options,
        "websocket_api": Errors,
        "_tv_can_read": lambda hass, user, tv_id: tv_id in ("tv0001", "tv0002"),
        "clean_tv_device_id": lambda x: x,
        "POLICY_CONTROL": "control",
        "Context": lambda user_id: {"user_id": user_id},
        "_REQUESTS": "shortcut_press_last",
        "time": time,
    }
    exec(compile(ast.Module(body=methods, type_ignores=[]), "home_shortcut_ws_test", "exec"), ns)
    return ns


class ShortcutWebsocketTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.ws = make_ws_functions()
        self.ha = FakeHass()
        self.user = FakeUser({"light.living_room", "scene.movie_time"})
        self.connection = FakeConnection(self.user)

    async def test_list_only_authorized_slots_and_no_entity_data(self):
        self.ws["websocket_get_home_shortcuts"](self.ha, self.connection, {
            "id": 4, "device_id": "tv0001",
        })
        self.assertEqual([(4, {"shortcuts": [
            {"slot": 1, "label": "Living room on", "icon": "mdi:lamp"},
            {"slot": 2, "label": "Movie Time", "icon": "mdi:movie-open"},
        ]})], self.connection.results)
        self.assertNotIn("entity_id", str(self.connection.results))

    async def test_press_cannot_supply_domain_or_target(self):
        await self.ws["websocket_press_home_shortcut"](self.ha, self.connection, {
            "id": 5, "device_id": "tv0001", "slot": 2,
            "domain": "lock", "entity_id": "lock.front_door", "service": "unlock",
        })
        self.assertEqual(1, len(self.ha.services.calls))
        domain, service, payload, kwargs = self.ha.services.calls[0]
        self.assertEqual(("scene", "turn_on", {"entity_id": "scene.movie_time"}),
                         (domain, service, payload))
        self.assertEqual({"user_id": "ha-user"}, kwargs["context"])
        self.assertTrue(kwargs["blocking"])

    async def test_repeated_press_and_disallowed_entities_fail(self):
        press = self.ws["websocket_press_home_shortcut"]
        data = {"id": 6, "device_id": "tv0001", "slot": 1}
        await press(self.ha, self.connection, data)
        await press(self.ha, self.connection, {**data, "id": 7})
        self.assertEqual(1, len(self.ha.services.calls))
        self.assertEqual("shortcut_busy", self.connection.errors[-1][1])
        await press(self.ha, self.connection, {"id": 8, "device_id": "tv0001", "slot": 3})
        self.assertEqual("unauthorized", self.connection.errors[-1][1])

    async def test_two_tvs_do_not_share_shortcuts(self):
        self.ws["websocket_get_home_shortcuts"](self.ha, self.connection, {
            "id": 9, "device_id": "tv0002",
        })
        self.assertEqual([], self.connection.results[-1][1]["shortcuts"])
        await self.ws["websocket_press_home_shortcut"](self.ha, self.connection, {
            "id": 10, "device_id": "tv0002", "slot": 1,
        })
        self.assertEqual([], self.ha.services.calls)


if __name__ == "__main__":
    unittest.main()
