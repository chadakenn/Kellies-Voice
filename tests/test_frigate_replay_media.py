"""Security boundary tests for scoped Frigate media proxy, no Home Assistant runtime."""
import ast
import re
from pathlib import Path
from types import SimpleNamespace
from urllib.parse import urlsplit
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "custom_components" / "kellies_voice" /
          "frigate_replay_media.py").read_text(encoding="utf-8")
ANDROID = (ROOT / "app" / "src" / "main" / "java" /
           "com" / "example" / "tvvoiceprobe" / "FrigateReplayActivity.java").read_text(
               encoding="utf-8")


class BadRequest(Exception):
    pass


class NotFound(Exception):
    pass


class Web:
    @staticmethod
    def HTTPBadRequest(**kwargs):
        return BadRequest()
    @staticmethod
    def HTTPNotFound(**kwargs):
        return NotFound()


def policy_functions():
    tree = ast.parse(SOURCE)
    functions = [node for node in tree.body
                 if isinstance(node, ast.FunctionDef)
                 and node.name in ("safe_range_header",
                                   "resolve_frigate_clip_origin")]
    assert len(functions) == 2
    scope = {
        "re": re,
        "_RANGE": re.compile(r"bytes=(?:[0-9]{1,12})?-(?:[0-9]{1,12})?\Z",
                             re.ASCII),
        "web": Web,
        "urlsplit": urlsplit,
        "URL": str,
    }
    exec(compile(ast.Module(body=functions, type_ignores=[]),
                 "test_replay_media_guard", "exec"), scope)
    return scope


class ReplayMediaSecurityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.funcs = policy_functions()

    def test_only_simple_byte_ranges_allowed(self):
        accept = self.funcs["safe_range_header"]
        self.assertIsNone(accept(None))
        for valid in ("bytes=0-", "bytes=10-123", "bytes=-100",
                      "bytes=000000000000-100", "bytes=0-0"):
            self.assertEqual(valid, accept(valid))
        for bad in ("bytes=-", "bytes=0-1,2-4", "bytes=0-1\nX-Header: secret",
                    "bytes=abc-def", "http://evil", "bytes=0-1;foo=bar",
                    "items=0-2", "bytes=0-9999999999999", 0, {}):
            with self.subTest(value=bad), self.assertRaises(BadRequest):
                accept(bad)

    def test_only_frigate_integration_url_accepted(self):
        allow = self.funcs["resolve_frigate_clip_origin"]
        self.assertEqual("https://frigate.local:8971",
                         allow("https://frigate.local:8971"))
        self.assertEqual("http://192.168.40.5:5000",
                         allow("http://192.168.40.5:5000"))
        for bad in ("", "file:///etc/passwd", "http://", "//evil.invalid",
                    "https://admin:secret@frigate.local",
                    "https://frigate.local/api/other",
                    "https://frigate.local/?token=secret",
                    "https://frigate.local/#fragment", None):
            with self.subTest(value=bad), self.assertRaises(NotFound):
                allow(bad)

    def test_media_view_requires_ha_user_and_scoped_camera_and_no_redirect(self):
        tree = ast.parse(SOURCE)
        view = next(n for n in tree.body if isinstance(n, ast.ClassDef)
                    and n.name == "FrigateReplayClipView")
        attrs = {target.id: node.value.value
                 for node in view.body if isinstance(node, ast.Assign)
                 for target in node.targets if isinstance(target, ast.Name)
                 and isinstance(node.value, ast.Constant)}
        self.assertTrue(attrs["requires_auth"])
        self.assertEqual(
            "/api/kellies_voice/replay_clip/{tv_id}/{entity_id}/{event_id}",
            attrs["url"],
        )
        get = next(n for n in view.body if isinstance(n, ast.AsyncFunctionDef)
                   and n.name == "get")
        source = ast.unparse(get)
        self.assertIn("request.get(", source)
        self.assertIn("hass_user", source)
        self.assertIn("authorize_replay_request(", source)
        self.assertGreaterEqual(source.count("authorize_replay_request("), 2)
        self.assertIn("requested_event_id=event_id", source)
        self.assertIn("allow_redirects=False", source)
        self.assertIn("get_auth_headers()", source)
        self.assertIn("_MAX_CLIP_BYTES", source)
        self.assertNotIn("async_get_clientsession(hass).get(request", source)

    def test_tv_only_builds_ha_path_and_mutes_on_open(self):
        self.assertIn(".addPathSegments(\"api/kellies_voice/replay_clip\")", ANDROID)
        self.assertIn("clipPlayer.setVolume(0f)", ANDROID)
        self.assertIn('headers.put("Authorization", "Bearer " + bearer)', ANDROID)
        self.assertIn("followRedirects(false)", ANDROID)
        self.assertIn("followSslRedirects(false)", ANDROID)
        self.assertIn('"recent_events".equals(action.id)',
                      (ROOT / "app/src/main/java/com/example/tvvoiceprobe/TvEventsService.java").read_text())
        self.assertIn("stopPlayer();", ANDROID)
        self.assertNotIn("api/events/", ANDROID)
        self.assertNotIn("frigate_token", ANDROID)


if __name__ == "__main__":
    unittest.main()
