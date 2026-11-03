"""Per-TV Frigate replay permission checks with fake HA registries."""
import importlib.util
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "kellies_voice"
sys.modules.setdefault("custom_components", types.ModuleType("custom_components"))
pkg = sys.modules.setdefault("custom_components.kellies_voice",
                             types.ModuleType("custom_components.kellies_voice"))
pkg.__path__ = [str(ROOT)]
ws_name = "custom_components.kellies_voice.websocket"
ws = sys.modules.setdefault(ws_name, types.ModuleType(ws_name))
ws.POLICY_READ = "read"
ws._tv_can_read = lambda hass, user, tv_id: getattr(user, "tv_allowed", False) and tv_id == "paired-tv"


def load(name):
    fq = "custom_components.kellies_voice." + name
    if fq in sys.modules:
        return sys.modules[fq]
    spec = importlib.util.spec_from_file_location(fq, ROOT / (name + ".py"))
    result = importlib.util.module_from_spec(spec)
    sys.modules[fq] = result
    spec.loader.exec_module(result)
    return result


load("const")
load("frigate_replay")
auth = load("frigate_replay_access")


class FakePermissions:
    def __init__(self, allowed=True):
        self.allowed = allowed
    def access_all_entities(self, policy):
        return False
    def check_entity(self, entity, policy):
        return self.allowed and entity == "camera.doorbell" and policy == "read"


def tv_entry(mapping):
    return types.SimpleNamespace(
        entry_id="tv_entry",
        data={"device_id": "paired-tv"},
        options={"replay_camera_map": mapping},
    )


class FakeHass:
    def __init__(self, mapping):
        self.tv = tv_entry(mapping)
        self.frigate = types.SimpleNamespace(entry_id="frigate_instance_one")
        self.config_entries = types.SimpleNamespace(async_entries=self.entries)
        self.states = types.SimpleNamespace(
            get=lambda entity: object() if entity == "camera.doorbell" else None
        )
    def entries(self, domain):
        return ([self.tv] if domain == "kellies_voice"
                else [self.frigate] if domain == "frigate" else [])


class ReplayAuthorizationTests(unittest.TestCase):
    def setUp(self):
        self.map = {"camera.doorbell": {
            "frigate_entry_id": "frigate_instance_one",
            "frigate_camera": "front_door"
        }}
        self.hass = FakeHass(self.map)
        self.user = types.SimpleNamespace(
            is_admin=False, permissions=FakePermissions(), tv_allowed=True
        )
        self.registry = types.SimpleNamespace(async_get=lambda entity:
            types.SimpleNamespace(config_entry_id="frigate_instance_one")
            if entity == "camera.doorbell" else None)

    def allow(self, entity="camera.doorbell", tv_id="paired-tv"):
        return auth.authorize_replay_request(
            self.hass, self.user, tv_id, entity, self.registry
        )

    def test_honors_exact_tv_entity_camera_and_frigate_instance(self):
        scope = self.allow()
        self.assertEqual("paired-tv", scope.tv_id)
        self.assertEqual("camera.doorbell", scope.camera_entity_id)
        self.assertEqual("front_door", scope.frigate_camera)
        self.assertEqual("frigate_instance_one", scope.frigate_entry_id)
        self.assertEqual(4, len(vars(scope)))
        with self.assertRaises(Exception):
            scope.tv_id = "other"

    def test_fails_closed_for_other_tv_and_restricted_camera(self):
        for identity in ("other-tv", "../paired-tv", "", None):
            with self.subTest(identity=identity), self.assertRaises(auth.ReplayAccessDenied):
                self.allow(tv_id=identity)
        self.user.tv_allowed = False
        with self.assertRaises(auth.ReplayAccessDenied):
            self.allow()
        self.user.tv_allowed = True
        self.user.permissions.allowed = False
        with self.assertRaises(auth.ReplayAccessDenied):
            self.allow()
        self.user.is_admin = True
        self.assertEqual("front_door", self.allow().frigate_camera)

    def test_mapping_required_and_no_friendly_name_guessing(self):
        for bad in ({}, {"camera.doorbell": "front_door"},
                    {"camera.doorbell": {
                        "frigate_entry_id": "frigate_instance_one",
                        "frigate_camera": "../front"
                    }},
                    {"camera.doorbell": {
                        "frigate_entry_id": "frigate_instance_one",
                        "frigate_camera": "front_door",
                        "url": "http://secret"
                    }}):
            with self.subTest(bad=bad), self.assertRaises(auth.ReplayAccessDenied):
                self.hass.tv = tv_entry(bad)
                self.allow()
        self.hass.tv = tv_entry(self.map)
        for bad in ("doorbell", "camera.front_door", "camera.doorbell/clip", None):
            with self.subTest(bad=bad), self.assertRaises(auth.ReplayAccessDenied):
                self.allow(entity=bad)

    def test_rejects_cross_instance_or_missing_entity(self):
        self.registry = types.SimpleNamespace(async_get=lambda _:
            types.SimpleNamespace(config_entry_id="other_frigate_instance"))
        with self.assertRaises(auth.ReplayAccessDenied):
            self.allow()
        self.registry = types.SimpleNamespace(async_get=lambda _: None)
        with self.assertRaises(auth.ReplayAccessDenied):
            self.allow()
        self.registry = types.SimpleNamespace(async_get=lambda _:
            types.SimpleNamespace(config_entry_id="frigate_instance_one"))
        self.hass.frigate.entry_id = "other_frigate_instance"
        with self.assertRaises(auth.ReplayAccessDenied):
            self.allow()
        self.hass.frigate.entry_id = "frigate_instance_one"
        self.hass.states.get = lambda _: None
        with self.assertRaises(auth.ReplayAccessDenied):
            self.allow()

    def test_no_secrets_and_no_url_constructed(self):
        scope = self.allow()
        self.assertNotIn("http", str(scope))
        self.assertNotIn("token", str(scope))
        self.assertFalse(hasattr(scope, "url"))
        self.assertFalse(hasattr(scope, "credentials"))


if __name__ == "__main__":
    unittest.main()
