"""Credential scope preflight must fail closed on default HA user group.

This is offline only: no HA owner data, tokens, HTTP or TV accessed.
"""
from __future__ import annotations

from pathlib import Path
import importlib.util
from types import SimpleNamespace
import unittest

PATH = (Path(__file__).resolve().parents[1] /
        "custom_components/kellies_voice_v3qa/credential_scope.py")
spec = importlib.util.spec_from_file_location("kv_v3qa_scope_policy_tests", PATH)
import sys
scope = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = scope
spec.loader.exec_module(scope)

TV = "123e4567-e89b-42d3-a456-426614174000"
ALERT = "select.tv_alert_position"
LAMP = "light.bedroom"
CAM = "camera.doorbell"
OTHER = "lock.front_door"


class FakePermissions:
    def __init__(self, grants, all_access=None, fault=False):
        self.grants = grants
        self.all_access = set(all_access or ())
        self.fault = fault

    def access_all_entities(self, action):
        if self.fault:
            raise RuntimeError("Unavailable")
        return action in self.all_access

    def check_entity(self, entity, action):
        if self.fault:
            raise RuntimeError("Unavailable")
        return action in self.grants.get(entity, set())


class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.policy = {
            "entities": {
                "entity_ids": {
                    ALERT: {"read": True},
                    LAMP: {"read": True, "control": True},
                    CAM: {"read": True, "control": True},
                }
            }
        }
        self.grants = {ALERT: {"read"}, LAMP: {"read", "control"},
                       CAM: {"read", "control"}}

    def user(self, *, group_id="kellies-explicit-tv", policy=None,
             grants=None, all_access=None, **kw):
        values = dict(id="tv-user-id", is_owner=False, is_admin=False,
                      system_generated=False, is_active=True, local_only=True)
        values.update(kw)
        values["groups"] = [SimpleNamespace(
            id=group_id, policy=self.policy if policy is None else policy)]
        values["permissions"] = FakePermissions(
            self.grants if grants is None else grants, all_access=all_access)
        return SimpleNamespace(**values)

    def check(self, user, **kw):
        args = dict(
            user=user, owner_id="real-owner-id", tv_uuid=TV,
            alert_entity_id=ALERT, assist_entity_ids=[LAMP, CAM],
            current_entity_ids=[ALERT, LAMP, CAM, OTHER],
        )
        args.update(kw)
        return scope.check_restricted_tv_scope(**args)

    def test_exact_custom_group_and_effective_permissions_only(self):
        user = self.user()
        proof = self.check(user)
        self.assertEqual(proof.tv_uuid, TV)
        self.assertEqual(proof.tv_user_id, "tv-user-id")
        self.assertEqual(proof.assist_entity_ids, (CAM, LAMP))
        self.assertNotIn("token", str(proof).lower())

    def test_default_ha_normal_user_is_not_least_privilege(self):
        for group in ("system-users", "system-admin", "system-read-only"):
            with self.subTest(group=group), self.assertRaises(scope.TvScopeRejected):
                self.check(self.user(group_id=group, all_access={"read", "control"}))
        self.assertRaises(scope.TvScopeRejected, self.check,
                          self.user(all_access={"read", "control"}))

    def test_owner_admin_system_user_foreign_and_remote_user_denied(self):
        for attr in (
            {"id": "real-owner-id"}, {"is_owner": True},
            {"is_admin": True}, {"system_generated": True},
            {"is_active": False}, {"local_only": False},
        ):
            with self.subTest(attr=attr), self.assertRaises(scope.TvScopeRejected):
                self.check(self.user(**attr))

    def test_broad_groups_wildcards_and_future_entity_grants_denied(self):
        policies = (
            {"entities": True},
            {"entities": {"all": {"read": True}}},
            {"entities": {"domains": {"light": True}}},
            {"entities": {"area_ids": {"livingroom": True}}},
            {"entities": {"device_ids": {"device": True}}},
            {"entities": {"entity_ids": {OTHER: {"read": True}}}},
            {"entities": {"entity_ids": {ALERT: True}}},
            {"entities": {"entity_ids": {ALERT: {"edit": True}}}},
            {"entities": {"entity_ids": {ALERT: {"read": 1}}}},
            {"entities": {"entity_ids": {ALERT: {"read": False}}}},
            {"entities": {"entity_ids": {ALERT: {"read": True}}}, "services": True},
            {},
        )
        for p in policies:
            with self.subTest(p=p), self.assertRaises(scope.TvScopeRejected):
                self.check(self.user(policy=p))
        # A second broad group also expands total permissions, even when
        # the first group is explicit and correct.
        user = self.user()
        user.groups.append(SimpleNamespace(
            id="extra", policy={"entities": {"all": {"read": True}}}))
        with self.assertRaises(scope.TvScopeRejected):
            self.check(user)

    def test_effective_permissions_extras_and_missing_grants_denied(self):
        bad = [
            {**self.grants, OTHER: {"read"}},
            {**self.grants, OTHER: {"control"}},
            {**self.grants, ALERT: {"read", "control"}},
            {**self.grants, CAM: {"read", "control", "edit"}},
            {**self.grants, LAMP: {"read"}},
            {**self.grants, LAMP: {"control"}},
            {**self.grants, ALERT: set()},
        ]
        for grants in bad:
            with self.subTest(grants=grants), self.assertRaises(scope.TvScopeRejected):
                self.check(self.user(grants=grants))

    def test_scope_errors_inventory_errors_and_missing_ha_permissions(self):
        user = self.user()
        for changes in (
            {"tv_uuid": "not-a-uuid"},
            {"tv_uuid": TV.upper()},
            {"alert_entity_id": "sensor.unrelated"},
            {"assist_entity_ids": [LAMP, LAMP]},
            {"assist_entity_ids": []},
            {"assist_entity_ids": [LAMP, CAM, OTHER]},
            {"current_entity_ids": [ALERT, LAMP, CAM]},
            {"current_entity_ids": [ALERT, LAMP, OTHER]},
            {"owner_id": ""},
        ):
            # Missing unrelated entity from inventory is not proof the
            # account has broader access, but the policy still pins exact IDs.
            if changes == {"current_entity_ids": [ALERT, LAMP, CAM]}:
                continue
            with self.subTest(changes=changes), self.assertRaises(scope.TvScopeRejected):
                self.check(user, **changes)
        user.permissions = FakePermissions(self.grants, fault=True)
        with self.assertRaises(scope.TvScopeRejected):
            self.check(user)

    def test_scope_proof_is_not_a_token_and_cannot_be_used_for_auth(self):
        p = self.check(self.user())
        self.assertEqual(
            set(vars(p)), {"tv_uuid", "tv_user_id", "alert_entity_id",
                           "assist_entity_ids"})
        self.assertFalse(hasattr(p, "access_token"))
        self.assertFalse(hasattr(p, "refresh_token"))


if __name__ == "__main__":
    unittest.main()
