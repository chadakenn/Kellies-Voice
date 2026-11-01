"""Offline adversarial checks for token-free scoped TV capability decisions."""
from __future__ import annotations

from pathlib import Path
import importlib
import sys
import types
import unittest

ROOT = (Path(__file__).resolve().parents[1]
        / "qa" / "ha_v3qa" / "kellies_voice_v3qa")
PKG = "kv_v3qa_scoped_gate_tests"
TV1 = "123e4567-e89b-42d3-a456-426614174000"
TV2 = "123e4567-e89b-42d3-a456-426614174001"
SID1 = "AQIDBAUGBwgJCgsMDQ4PEA"
SID2 = "AQIDBAUGBwgJCgsMDQ4PEQ"


class ScopedGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pkg = types.ModuleType(PKG)
        pkg.__path__ = [str(ROOT)]
        sys.modules[PKG] = pkg
        cls.mod = importlib.import_module(PKG + ".scoped_tv_gate")
        cls.wire = importlib.import_module(PKG + ".pairing_v3_wire")
        cls.scope = importlib.import_module(PKG + ".credential_scope")

    @classmethod
    def tearDownClass(cls):
        for key in list(sys.modules):
            if key == PKG or key.startswith(PKG + "."):
                sys.modules.pop(key, None)

    def setUp(self):
        self.gate = self.mod.ScopedTvGate()
        self.conn1 = object()
        self.conn2 = object()
        self.scope1 = self.make_scope(TV1)
        self.tv1 = self.wire.VerifiedTv(tv_uuid=TV1, session=SID1)
        self.bind(self.conn1, self.tv1, self.scope1)

    def make_scope(self, tv):
        return self.scope.TvScopeProof(
            tv_uuid=tv, tv_user_id="dedicated-user-" + tv[-5:],
            alert_entity_id="select.tv_alert_position",
            assist_entity_ids=(
                "light.bedroom", "fan.office", "camera.doorbell",
                "lock.front_door", "switch.furnace",
            ),
        )

    def bind(self, conn, tv, scope, **kwargs):
        args = dict(
            connection=conn, verified_tv=tv,
            approved_owner_id="authenticated-owner",
            scope=scope, permitted_cameras=frozenset({"camera.doorbell"}),
            permitted_controls=frozenset({"light.bedroom", "fan.office"}),
            ttl_seconds=60, now=100,
        )
        args.update(kwargs)
        self.gate.bind_verified_channel(**args)

    def allow(self, *, conn=None, tv=TV1, sid=SID1, seq=1,
              op="entity.read", entity="light.bedroom", now=101):
        return self.gate.authorize(
            connection=self.conn1 if conn is None else conn,
            tv_uuid=tv, session=sid, sequence=seq,
            operation=op, entity_id=entity, now=now,
        )

    def reject(self, **kw):
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.allow(**kw)

    def test_explicit_read_alerts_camera_and_light_are_scoped(self):
        self.assertTrue(self.allow())
        self.assertTrue(self.allow(seq=2, op="camera.snapshot",
                                   entity="camera.doorbell"))
        self.assertTrue(self.allow(seq=3, op="entity.control",
                                   entity="light.bedroom"))
        self.assertTrue(self.allow(seq=4, op="alerts.subscribe", entity=None))

    def test_no_raw_uuid_or_different_connection_ever_authorizes(self):
        self.reject(conn=object())
        self.reject(conn=self.conn2)
        self.reject(tv=TV2)
        self.reject(sid=SID2)
        self.reject(conn="client-provided-identity")

    def test_no_generic_service_intent_script_unlock_or_broad_control(self):
        for op, entity in [
            ("homeassistant.call_service", "light.bedroom"),
            ("assist.execute", None),
            ("lock.unlock", "lock.front_door"),
            ("entity.control", "lock.front_door"),
            ("entity.control", "switch.furnace"),
            ("entity.control", "camera.doorbell"),
            ("entity.control", "light.other"),
            ("camera.snapshot", "camera.unknown"),
            ("entity.read", "lock.other"),
            ("alerts.subscribe", "camera.doorbell"),
        ]:
            with self.subTest(op=op, entity=entity):
                self.reject(op=op, entity=entity)

    def test_replay_and_expiry_and_sequence_type_guards(self):
        self.assertTrue(self.allow(seq=1))
        self.reject(seq=1)
        self.reject(seq=0)
        self.reject(seq=True)
        self.reject(seq=1.0)
        self.reject(seq=2**31)
        self.reject(now=160)
        self.assertEqual(self.gate.active_count(now=160), 0)

    def test_invalid_request_does_not_burn_next_valid_sequence(self):
        self.reject(seq=1, op="assist.execute", entity=None)
        self.assertTrue(self.allow(seq=1, op="entity.read",
                                   entity="light.bedroom"))

    def test_no_cross_tv_and_duplicate_binding(self):
        scope2 = self.make_scope(TV2)
        tv2 = self.wire.VerifiedTv(tv_uuid=TV2, session=SID2)
        self.bind(self.conn2, tv2, scope2)
        self.assertTrue(self.allow(conn=self.conn2, tv=TV2,
                                   sid=SID2, seq=1, entity="light.bedroom"))
        self.reject(conn=self.conn2, tv=TV1, sid=SID1)
        self.reject(conn=self.conn1, tv=TV2, sid=SID2, seq=2)
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(object(), tv2, scope2)
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(self.conn1, tv2, scope2)

    def test_disconnect_and_restart_revoke_without_device_response(self):
        self.gate.disconnect(self.conn1)
        self.reject()
        self.bind(self.conn1, self.tv1, self.scope1, now=105)
        self.assertTrue(self.allow(now=106))
        self.gate.reset()
        self.reject(now=107)
        self.assertEqual(self.gate.active_count(), 0)

    def test_fail_closed_for_foreign_scope_broad_controls_and_bad_ids(self):
        tv2 = self.wire.VerifiedTv(tv_uuid=TV2, session=SID2)
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(object(), tv2, self.scope1)
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(object(), self.tv1, self.scope1,
                      permitted_controls=frozenset({"lock.front_door"}))
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(object(), self.tv1, self.scope1,
                      permitted_cameras=frozenset({"camera.other"}))
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(object(), self.tv1, self.scope1,
                      ttl_seconds=float("inf"))
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind(object(), self.tv1, self.scope1, ttl_seconds=500)
        with self.assertRaises(self.mod.TvCapabilityDenied):
            self.bind("client-provided-connection", self.tv1, self.scope1)

    def test_model_is_a_dry_run_and_has_no_credential_endpoints(self):
        source = (ROOT / "scoped_tv_gate.py").read_text()
        for symbol in (
            "async_create_refresh_token(", "async_create_user(",
            "async_register_command(", "async_register_static_paths(",
            "async_create_entry(", "async_call(", "async_fire(",
        ):
            self.assertNotIn(symbol, source)
        self.assertEqual(self.gate.active_count(now=101), 1)


if __name__ == "__main__":
    unittest.main()
