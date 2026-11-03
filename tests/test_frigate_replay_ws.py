"""TV-restricted Frigate event metadata WebSocket tests, no HA installation."""
import ast
from pathlib import Path
from types import SimpleNamespace
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "custom_components/kellies_voice/frigate_replay_ws.py"


class AccessDenied(Exception):
    pass


class MissingReplay(Exception):
    pass


class Errors:
    ERR_INVALID_FORMAT = "bad_request"
    ERR_UNAUTHORIZED = "unauthorized"


class Connection:
    def __init__(self):
        self.user = SimpleNamespace(id="tv-user")
        self.messages = []
        self.errors = []
    def send_result(self, req_id, data):
        self.messages.append((req_id, data))
    def send_error(self, req_id, error, description):
        self.errors.append((req_id, error, description))


class ReplayWSRuntime:
    def __init__(self):
        self.calls = []
        self.allowed = True
        self.after = False
        self.revoke_after = False
        self.scope = SimpleNamespace(
            tv_id="tv", camera_entity_id="camera.doorbell",
            frigate_entry_id="real-frigate", frigate_camera="front_door")
        self.hass = SimpleNamespace(data={
            "frigate": {"real-frigate": {
                "client": object(),
                "config": {"cameras": {"front_door": {}, "driveway": {}}},
            }},
        })
        self.connection = Connection()
        self.namespace = self.build()

    def authorize(self, hass, user, tv, entity, registry):
        self.calls.append(("auth", tv, entity))
        if not self.allowed or (self.after and self.revoke_after):
            raise AccessDenied()
        if tv != "tv" or entity != "camera.doorbell":
            raise AccessDenied()
        return self.scope

    async def lookup(self, api_client, **kwargs):
        self.calls.append(("fetch", kwargs))
        self.after = True
        return [SimpleNamespace(
            event_id="1730999999.123456-a9b8c7", label="person",
            started_at=1731000000.0, ended_at=1731000002.0,
        )]

    def build(self):
        source = ast.parse(SOURCE.read_text())
        defs = [n for n in source.body if isinstance(n, ast.AsyncFunctionDef)
                and n.name == "websocket_replay_metadata"]
        assert len(defs) == 1
        defs[0].decorator_list = []
        ns = {
            "authorize_replay_request": self.authorize,
            "ReplayAccessDenied": AccessDenied,
            "FrigateReplayUnavailable": MissingReplay,
            "fetch_recorded_event_metadata": self.lookup,
            "er": SimpleNamespace(async_get=lambda hass: object()),
            "websocket_api": Errors,
            "Mapping": __import__("collections.abc").abc.Mapping,
            "time": SimpleNamespace(time=lambda: 1731000100),
        }
        exec(compile(ast.Module(body=defs, type_ignores=[]), str(SOURCE), "exec"), ns)
        return ns["websocket_replay_metadata"]

    async def run(self, **payload):
        await self.namespace(
            self.hass, self.connection,
            {"id": 13, "device_id": "tv",
             "entity_id": "camera.doorbell", **payload}
        )


class ReplayMetadataWSTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.r = ReplayWSRuntime()

    async def test_only_authorized_camera_metadata_returns(self):
        await self.r.run(
            frigate_url="https://evil.example",
            frigate_camera="wrong_camera",
            auth_token="danger",
        )
        self.assertEqual([], self.r.connection.errors)
        self.assertEqual(1, len(self.r.connection.messages))
        req_id, reply = self.r.connection.messages[0]
        self.assertEqual(13, req_id)
        self.assertEqual("camera.doorbell", reply["camera_entity_id"])
        self.assertEqual(["person"], [e["label"] for e in reply["events"]])
        wire = repr(reply)
        for item in ("token", "url", "https", "http", "front_door", "driveway"):
            self.assertNotIn(item, wire)
        lookup = next(x[1] for x in self.r.calls if x[0] == "fetch")
        self.assertEqual("front_door", lookup["frigate_camera"])
        self.assertEqual(8, lookup["limit"])
        self.assertEqual(2, len([x for x in self.r.calls if x[0] == "auth"]))

    async def test_wrong_tv_or_camera_never_fetches_event(self):
        for payload in ({"device_id": "other-tv"}, {"entity_id": "camera.driveway"}):
            with self.subTest(payload=payload):
                runtime = ReplayWSRuntime()
                await runtime.run(**payload)
                self.assertEqual("unauthorized", runtime.connection.errors[0][1])
                self.assertFalse(any(x[0] == "fetch" for x in runtime.calls))
                self.assertEqual([], runtime.connection.messages)

    async def test_permission_revocation_during_request_blocks_result(self):
        self.r.revoke_after = True
        await self.r.run()
        self.assertEqual("unauthorized", self.r.connection.errors[0][1])
        self.assertEqual([], self.r.connection.messages)

    async def test_missing_client_and_unmapped_config_fail_closed(self):
        for state in ({}, {"client": object(), "config": {"cameras": {}}}):
            runtime = ReplayWSRuntime()
            runtime.hass.data = {"frigate": {"real-frigate": state}}
            await runtime.run()
            self.assertEqual("replay_unavailable", runtime.connection.errors[0][1])
            self.assertEqual([], runtime.connection.messages)
            self.assertFalse(any(x[0] == "fetch" for x in runtime.calls))

    async def test_invalid_limits_and_explicit_id_cannot_change_camera(self):
        for bad in (0, 11, True, "10"):
            runtime = ReplayWSRuntime()
            await runtime.run(limit=bad)
            self.assertEqual("bad_request", runtime.connection.errors[0][1])
        await self.r.run(event_id="1730999999.123456-a9b8c7", limit=1)
        lookup = next(x[1] for x in self.r.calls if x[0] == "fetch")
        self.assertEqual("1730999999.123456-a9b8c7", lookup["requested_event_id"])
        self.assertEqual("front_door", lookup["frigate_camera"])


if __name__ == "__main__":
    unittest.main()
