"""Validate owner-managed HA Frigate replay camera mapping, without HA install."""
import importlib.util
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "custom_components/kellies_voice/frigate_replay_mapping.py"
spec = importlib.util.spec_from_file_location("test_frigate_mapping_module", SOURCE)
mapper = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = mapper
spec.loader.exec_module(mapper)


def record(entity_id, entry_id, disabled=None):
    return types.SimpleNamespace(
        entity_id=entity_id, config_entry_id=entry_id, disabled_by=disabled)


class ReplayMappingTests(unittest.TestCase):
    def setUp(self):
        self.registry = {
            "camera.doorbell": record("camera.doorbell", "frigate-entry-1"),
            "camera.driveway": record("camera.driveway", "frigate-entry-2"),
            "camera.generic": record("camera.generic", "other-entry"),
            "camera.disabled": record("camera.disabled", "frigate-entry-1", "user"),
            "light.living_room": record("light.living_room", "frigate-entry-1"),
        }
        self.instances = {"frigate-entry-1", "frigate-entry-2"}

    def test_lists_only_registered_frigate_camera_entities(self):
        choices = mapper.mapped_camera_options(self.registry, self.instances)
        self.assertEqual(["camera.doorbell", "camera.driveway"], choices)

    def test_maps_configured_entity_to_its_actual_frigate_entry(self):
        value = mapper.validate_replay_mapping(
            "camera.doorbell", "front_door",
            registry_entities=self.registry,
            frigate_entry_ids=self.instances,
        )
        self.assertEqual({
            "camera.doorbell": {
                "frigate_entry_id": "frigate-entry-1",
                "frigate_camera": "front_door",
            },
        }, value)
        self.assertEqual(("camera.doorbell", "front_door"),
                         mapper.current_replay_fields({"replay_camera_map": value}))

    def test_double_blank_disables_and_partial_input_is_error(self):
        self.assertEqual({}, mapper.validate_replay_mapping(
            "", "", registry_entities=self.registry,
            frigate_entry_ids=self.instances,
        ))
        for entity, camera in (("", "front_door"), ("camera.doorbell", ""),
                               ("camera.doorbell", "http://frigate.invalid"),
                               ("camera.doorbell", "../front"),
                               ("camera.doorbell", "front door"),
                               ("camera.generic", "generic"),
                               ("camera.disabled", "disabled"),
                               ("camera.unknown", "front"),
                               ("lock.front_door", "front")):
            with self.subTest(entity=entity,camera=camera), self.assertRaises(
                    mapper.ReplayMappingError):
                mapper.validate_replay_mapping(
                    entity, camera, registry_entities=self.registry,
                    frigate_entry_ids=self.instances
                )

    def test_cannot_select_frigate_entry_not_in_registry(self):
        with self.assertRaises(mapper.ReplayMappingError):
            mapper.validate_replay_mapping(
                "camera.doorbell", "front_door",
                registry_entities=self.registry,
                frigate_entry_ids={"other-entry"},
            )

    def test_does_not_recover_broken_or_old_config_as_valid_mapping(self):
        for options in (
            None, {}, {"replay_camera_map": []},
            {"replay_camera_map": {"camera.doorbell": "front"}},
            {"replay_camera_map": {"camera.doorbell": {"frigate_camera": "front"}}},
        ):
            self.assertEqual(("", ""), mapper.current_replay_fields(options))
        self.assertEqual([], mapper.mapped_camera_options([], self.instances))
        self.assertEqual([], mapper.mapped_camera_options(self.registry, []))

    def test_options_form_has_explicit_owner_mapping_and_never_constructs_url(self):
        code = (ROOT / "custom_components/kellies_voice/config_flow.py").read_text()
        self.assertIn('updated["replay_camera_map"] = mapping', code)
        self.assertIn("validate_replay_mapping(", code)
        self.assertIn('async_entries("frigate")', code)
        self.assertIn("mapped_camera_options(", code)
        self.assertNotIn("replay_frigate_url", code)


if __name__ == "__main__":
    unittest.main()
