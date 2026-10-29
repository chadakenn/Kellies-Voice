"""#193 proposed HA browser-owner OAuth authorization contract, offline only."""
from pathlib import Path
import importlib
import sys
import types
from urllib.parse import parse_qs, urlsplit
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PACKAGE = "kellies_owner_oauth_test"
module = types.ModuleType(PACKAGE)
module.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = module
oauth = importlib.import_module(PACKAGE + ".pairing_owner_oauth")

SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
CALLBACK = "/api/kellies_voice/pair/oauth_callback"


def deterministic_random(count: int):
    assert count == 32
    deterministic_random.calls += 1
    return bytes([deterministic_random.calls]) * 32


def make(url="http://192.168.1.10:8123/"):
    deterministic_random.calls = 0
    return oauth.OwnerAuthorizationAttempt.begin(
        url, SESSION, 1000,
        owner_requested=True, tv_ok_verified=True,
        random_bytes=deterministic_random,
    )


class OwnerOAuthTests(unittest.TestCase):
    def test_authorize_url_matches_home_assistant_pkce_and_same_origin(self):
        attempt = make()
        target = urlsplit(attempt.authorization_url())
        self.assertEqual("http", target.scheme)
        self.assertEqual("192.168.1.10:8123", target.netloc)
        self.assertEqual("/auth/authorize", target.path)
        args = parse_qs(target.query)
        self.assertEqual({"code", "state", "code_challenge",
                          "code_challenge_method", "response_type", "redirect_uri",
                          "client_id"} - {"code"}, set(args))
        self.assertEqual(["S256"], args["code_challenge_method"])
        self.assertEqual(["code"], args["response_type"])
        self.assertEqual(["http://192.168.1.10:8123/"], args["client_id"])
        self.assertEqual(["http://192.168.1.10:8123" + CALLBACK],
                         args["redirect_uri"])
        self.assertEqual([attempt.code_challenge], args["code_challenge"])
        self.assertNotIn("code_verifier", args)
        self.assertNotIn("refresh_token", target.query)

        result = attempt.consume_callback(
            received_state=args["state"][0], code="abcdefab12345678",
            tv_session=SESSION, now=1025,
        )
        self.assertEqual("abcdefab12345678", result["code"])
        self.assertEqual(43, len(result["code_verifier"]))
        self.assertEqual(attempt.client_id, result["client_id"])
        self.assertEqual(attempt.redirect_uri, result["redirect_uri"])
        with self.assertRaises(ValueError):
            attempt.consume_callback(received_state=args["state"][0],
                                     code="abcdefab12345678",
                                     tv_session=SESSION, now=1026)

    def test_owner_and_physically_verified_tv_are_both_required(self):
        for owner, tv in ((False, False), (True, False), (False, True)):
            with self.subTest(owner=owner, tv=tv):
                with self.assertRaises(ValueError):
                    oauth.OwnerAuthorizationAttempt.begin(
                        "http://10.0.0.8:8123/", SESSION, 100,
                        owner_requested=owner, tv_ok_verified=tv
                    )
        with self.assertRaises(ValueError):
            oauth.OwnerAuthorizationAttempt.begin(
                "http://10.0.0.8:8123/", "invalid", 100,
                owner_requested=True, tv_ok_verified=True
            )

    def test_only_local_http_or_https_instance_origin_accepted(self):
        for valid in (
            "http://10.5.1.2:8123/",
            "http://172.16.99.2:8123",
            "http://192.168.1.5:8123",
            "http://homeassistant.local:8123/",
            "http://homeassistant.home.arpa:8123",
            "https://my-ha.example.org/",
            "https://192.0.2.4:8123/",
            "http://[::1]:8123/",
        ):
            with self.subTest(valid=valid):
                a = make(valid)
                self.assertTrue(a.authorization_url().startswith(a.origin + "/auth/authorize"))
                self.assertTrue(a.redirect_uri.startswith(a.origin + "/api/"))
        for bad in (
            "http://example.org:8123", "http://192.0.2.15:8123",
            "http://100.64.1.2:8123", "http://192.168.1.5:8123/path",
            "http://192.168.1.5:8123/?secret=1", "http://user:pass@10.0.0.1/",
            "http://10.0.0.1/#a", "ftp://10.0.0.1:8123/",
            "https://evil.com@my-ha.com/",
            "https://my-ha.com\\@evil.test/", "http://0.0.0.0/",
            "http://169.254.10.20:8123", "https://ha.example.org:0/",
            "https://ha.example.org:99999/", "",
        ):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                make(bad)

    def test_bad_state_wrong_tv_cancel_and_expiry_burn_attempt(self):
        for change in ("state", "tv", "expiry", "code", "time"):
            with self.subTest(change=change):
                a = make()
                state = parse_qs(urlsplit(a.authorization_url()).query)["state"][0]
                args = {
                    "received_state": state,
                    "code": "1234567890abcd",
                    "tv_session": SESSION,
                    "now": 1005,
                }
                if change == "state": args["received_state"] = "wrong"
                if change == "tv": args["tv_session"] = "AQIDBAUGBwgJCgsMDQ4PEQ"
                if change == "expiry": args["now"] = 1120
                if change == "time": args["now"] = 999
                if change == "code": args["code"] = "code with spaces"
                with self.assertRaises(ValueError):
                    a.consume_callback(**args)
                with self.assertRaises(ValueError):
                    a.consume_callback(**args)

        a = make()
        a.cancel()
        with self.assertRaises(ValueError):
            a.authorization_url()
        with self.assertRaises(ValueError):
            a.consume_callback(received_state="any", code="abcdef12345",
                               tv_session=SESSION, now=1001)

    def test_never_exchange_or_log_actual_tokens(self):
        source = (ROOT / "pairing_owner_oauth.py").read_text()
        self.assertNotIn("async_call", source)
        self.assertNotIn("ClientSession(", source)
        self.assertNotIn("request.post", source)
        self.assertNotIn("CredentialStore", source)
        self.assertNotIn("print(", source)


if __name__ == "__main__":
    unittest.main()
