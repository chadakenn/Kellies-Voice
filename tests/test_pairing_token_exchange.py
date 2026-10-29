"""#193: OAuth token lifecycle tests use deliberately fake, nonsecret values.

No real HA instance, browser, user account, device or credentials are accessed.
"""
import importlib
import json
from pathlib import Path
import sys
import types
from urllib.parse import parse_qs, urlsplit
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice"
PACKAGE = "kellies_oauth_exchange_tests"
package = types.ModuleType(PACKAGE)
package.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = package
oauth = importlib.import_module(PACKAGE + ".pairing_owner_oauth")
tokens = importlib.import_module(PACKAGE + ".pairing_token_exchange")

TV_SESSION = "AQIDBAUGBwgJCgsMDQ4PEA"
HA_ORIGIN = "http://192.168.1.3:8123/"
LOOPBACK = "http://127.0.0.1:8123"
ACCESS = "fake.access.token.not-a-real-token"
REFRESH = "fake.refresh.token.not-a-real-token"


class FakeStream:
    def __init__(self, body):
        self.body = body

    async def read(self, size):
        chunk = self.body[:size]
        self.body = self.body[size:]
        return chunk


class FakeResponse:
    def __init__(self, status=200, body=None, mime="application/json"):
        self.status = status
        self.headers = {"Content-Type": mime}
        self.content = FakeStream(
            json.dumps(body).encode() if isinstance(body, dict)
            else body if isinstance(body, bytes) else b"",
        )

    async def __aenter__(self): return self
    async def __aexit__(self, *_): return False


class FakeClient:
    def __init__(self, replies):
        self.replies = list(replies)
        self.requests = []

    async def __aenter__(self): return self
    async def __aexit__(self, *_): return False

    def post(self, url, *, data, allow_redirects):
        assert allow_redirects is False
        self.requests.append((url, dict(data)))
        if not self.replies:
            raise RuntimeError("Test handler exhausted")
        return self.replies.pop(0)


def owner_attempt():
    values = iter((b"\x11" * 32, b"\x22" * 32))
    attempt = oauth.OwnerAuthorizationAttempt.begin(
        HA_ORIGIN, TV_SESSION, 200, owner_requested=True,
        tv_ok_verified=True, random_bytes=lambda count: next(values),
    )
    return attempt, parse_qs(urlsplit(attempt.authorization_url()).query)["state"][0]


class OAuthExchangeTests(unittest.IsolatedAsyncioTestCase):
    async def test_real_pkce_fields_and_single_use_under_fake_token_server(self):
        attempt, state = owner_attempt()
        response = FakeResponse(body={
            "access_token": ACCESS, "refresh_token": REFRESH,
            "expires_in": 1800, "token_type": "Bearer",
        })
        fake = FakeClient([response])
        exchange = tokens.HomeAssistantTokenExchange(
            HA_ORIGIN, LOOPBACK, _session_factory=lambda: fake)
        credentials = await exchange.exchange_approved_callback(
            attempt, state=state, code="fake_oauth_authorization_code",
            tv_session=TV_SESSION, now=201,
        )
        self.assertEqual(ACCESS, credentials.access_token)
        self.assertEqual(REFRESH, credentials.refresh_token)
        self.assertEqual(1800, credentials.expires_in)
        self.assertNotIn(ACCESS, repr(credentials))
        self.assertNotIn(REFRESH, repr(credentials))
        self.assertEqual(1, len(fake.requests))
        url, form = fake.requests[0]
        self.assertEqual(LOOPBACK + "/auth/token", url)
        self.assertEqual("authorization_code", form["grant_type"])
        self.assertEqual(HA_ORIGIN, form["client_id"])
        self.assertEqual("fake_oauth_authorization_code", form["code"])
        self.assertEqual(attempt.redirect_uri, form["redirect_uri"])
        self.assertEqual(43, len(form["code_verifier"]))
        self.assertNotIn("refresh_token", form)
        with self.assertRaises(tokens.OAuthTokenError):
            await exchange.exchange_approved_callback(
                attempt, state=state, code="fake_oauth_authorization_code",
                tv_session=TV_SESSION, now=202,
            )
        self.assertEqual(1, len(fake.requests))

    async def test_invalid_owner_state_or_wrong_tv_never_sends_http(self):
        for changed in ("state", "tv", "expired", "code"):
            with self.subTest(changed=changed):
                attempt, state = owner_attempt()
                fake = FakeClient([])
                exchange = tokens.HomeAssistantTokenExchange(
                    HA_ORIGIN, LOOPBACK, _session_factory=lambda: fake)
                arguments = dict(
                    state=state, code="fake_oauth_authorization_code",
                    tv_session=TV_SESSION, now=201,
                )
                if changed == "state": arguments["state"] = "imposter"
                if changed == "tv": arguments["tv_session"] = "AQIDBAUGBwgJCgsMDQ4PEQ"
                if changed == "expired": arguments["now"] = 320
                if changed == "code": arguments["code"] = "bad code"
                with self.assertRaises(tokens.OAuthTokenError):
                    await exchange.exchange_approved_callback(attempt, **arguments)
                self.assertEqual([], fake.requests)
                with self.assertRaises(tokens.OAuthTokenError):
                    await exchange.exchange_approved_callback(attempt, **arguments)

    async def test_refresh_and_revoke_use_only_same_local_endpoint(self):
        fake = FakeClient([
            FakeResponse(body={
                "access_token": ACCESS, "expires_in": 1800, "token_type": "Bearer",
            }),
            FakeResponse(status=200),
        ])
        exchange = tokens.HomeAssistantTokenExchange(
            HA_ORIGIN, LOOPBACK, _session_factory=lambda: fake)
        new = await exchange.refresh_access(REFRESH)
        self.assertEqual(REFRESH, new.refresh_token)
        self.assertEqual(ACCESS, new.access_token)
        self.assertEqual("refresh_token", fake.requests[0][1]["grant_type"])
        self.assertEqual(REFRESH, fake.requests[0][1]["refresh_token"])
        await exchange.revoke_refresh(REFRESH)
        self.assertEqual({"token": REFRESH}, fake.requests[1][1])
        self.assertEqual(LOOPBACK + "/auth/token", fake.requests[0][0])
        self.assertEqual(LOOPBACK + "/auth/revoke", fake.requests[1][0])
        for bad in ("", "bad", "really bad secret", True, None):
            with self.subTest(bad=bad):
                with self.assertRaises(tokens.OAuthTokenError):
                    await exchange.refresh_access(bad)
                with self.assertRaises(tokens.OAuthTokenError):
                    await exchange.revoke_refresh(bad)

    async def test_token_server_failures_are_opaque(self):
        for response in [
            FakeResponse(status=401, body={"access_token": ACCESS}),
            FakeResponse(status=302, body={"error": "redirect"}),
            FakeResponse(body={"access_token": ACCESS, "expires_in": 1800,
                               "token_type": "bearer", "refresh_token": REFRESH}),
            FakeResponse(body={"access_token": ACCESS, "expires_in": True,
                               "token_type": "Bearer", "refresh_token": REFRESH}),
            FakeResponse(body={"access_token": ACCESS, "expires_in": 0,
                               "token_type": "Bearer", "refresh_token": REFRESH}),
            FakeResponse(body={"access_token": ACCESS, "expires_in": 9999999,
                               "token_type": "Bearer", "refresh_token": REFRESH}),
            FakeResponse(body={"access_token": ACCESS, "expires_in": 1800,
                               "token_type": "Bearer"}),
            FakeResponse(mime="text/html", body=b"<b>private info</b>"),
            FakeResponse(body=b"secret" * 900),
        ]:
            with self.subTest(status=response.status):
                a, state = owner_attempt()
                fake = FakeClient([response])
                exchange = tokens.HomeAssistantTokenExchange(
                    HA_ORIGIN, LOOPBACK, _session_factory=lambda: fake)
                with self.assertRaises(tokens.OAuthTokenError) as raised:
                    await exchange.exchange_approved_callback(
                        a, state=state, code="fake_oauth_authorization_code",
                        tv_session=TV_SESSION, now=201)
                self.assertNotIn(ACCESS, str(raised.exception))
                self.assertNotIn(REFRESH, str(raised.exception))
                self.assertNotIn("private info", str(raised.exception))

    async def test_no_network_until_endpoint_origin_is_safe(self):
        for disallowed in (
            HA_ORIGIN,  # even private-network HTTP is NOT safe for tokens
            "http://192.168.1.30:8123", "http://10.0.0.30:8123",
            "http://homeassistant.local:8123",
            "http://evil.test:8123",
            "https://evil.test",
            "http://169.254.0.1:8123",
            "http://127.0.0.1:8123/evil",
        ):
            with self.subTest(disallowed=disallowed):
                with self.assertRaises((tokens.OAuthTokenError, ValueError)):
                    tokens.HomeAssistantTokenExchange(HA_ORIGIN, disallowed)
        other = tokens.HomeAssistantTokenExchange(
            "https://my-ha.example.org/", "https://my-ha.example.org/")
        self.assertEqual("https://my-ha.example.org/auth/token",
                         other._endpoint)

    async def test_wrong_client_id_cancels_auth_attempt(self):
        attempt, state = owner_attempt()
        exchange = tokens.HomeAssistantTokenExchange(
            "http://192.168.1.5:8123/", LOOPBACK)
        with self.assertRaises(tokens.OAuthTokenError):
            await exchange.exchange_approved_callback(
                attempt, state=state, code="fake_oauth_authorization_code",
                tv_session=TV_SESSION, now=201)
        with self.assertRaises(ValueError):
            attempt.authorization_url()


if __name__ == "__main__":
    unittest.main()
