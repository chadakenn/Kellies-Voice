"""#213: tests for owner-approved OAuth callback state binding.

These tests cover the server-side state registry and rejection paths.
They do not import aiohttp or the full Home Assistant runtime.
"""

import unittest
from unittest.mock import MagicMock

from custom_components.kellies_voice.oauth_callback import (
    register_pending_authorization,
    consume_pending_authorization,
    clear_pending_authorizations,
)


class TestOAuthStateBinding(unittest.TestCase):
    def setUp(self):
        clear_pending_authorizations()

    def test_register_and_consume_round_trip(self):
        attempt = MagicMock()
        register_pending_authorization(
            "test-state-123",
            flow_id="flow-abc",
            owner_user_id="user-1",
            tv_session="tv-session-xyz",
            attempt=attempt,
        )
        binding = consume_pending_authorization("test-state-123")
        self.assertIsNotNone(binding)
        self.assertEqual(binding["flow_id"], "flow-abc")
        self.assertEqual(binding["owner_user_id"], "user-1")
        self.assertEqual(binding["tv_session"], "tv-session-xyz")
        self.assertIs(binding["attempt"], attempt)

        # Second consume must return None (single-use).
        self.assertIsNone(consume_pending_authorization("test-state-123"))

    def test_unknown_state_rejected(self):
        self.assertIsNone(consume_pending_authorization("never-registered"))

    def test_empty_state_rejected_on_register(self):
        with self.assertRaises(ValueError):
            register_pending_authorization(
                "",
                flow_id="f",
                owner_user_id="u",
                tv_session="t",
                attempt=MagicMock(),
            )

    def test_unknown_owner_rejected_on_register(self):
        with self.assertRaises(ValueError):
            register_pending_authorization(
                "state",
                flow_id="f",
                owner_user_id="unknown",
                tv_session="t",
                attempt=MagicMock(),
            )

    def test_missing_owner_rejected_on_register(self):
        with self.assertRaises(ValueError):
            register_pending_authorization(
                "state",
                flow_id="f",
                owner_user_id="",
                tv_session="t",
                attempt=MagicMock(),
            )


class TestOAuthCallbackHTTP(unittest.IsolatedAsyncioTestCase):
    """Exercise the callback view, not just the in-memory registry.

    This is an offline harness with a fake HTTP response. It cannot replace
    Home Assistant's real OAuth consent and browser integration tests.
    """

    def setUp(self):
        import sys
        import types
        from unittest.mock import AsyncMock, patch
        from custom_components.kellies_voice.oauth_callback import OAuthCallbackView

        clear_pending_authorizations()

        class Response:
            def __init__(self, *, text, content_type, status=200):
                self.text = text
                self.content_type = content_type
                self.status = status

        self._fake_web = types.ModuleType("aiohttp.web")
        self._fake_web.Response = Response
        self._fake_aiohttp = types.ModuleType("aiohttp")
        self._fake_aiohttp.web = self._fake_web
        self._patch = patch.dict(sys.modules, {
            "aiohttp": self._fake_aiohttp, "aiohttp.web": self._fake_web
        })
        self._patch.start()
        self.addCleanup(self._patch.stop)

        self.hass = MagicMock()
        self.hass.config_entries.flow.async_configure = AsyncMock()
        self.view = OAuthCallbackView(self.hass)

    async def test_valid_callback_resumes_exact_flow_once(self):
        from types import SimpleNamespace

        attempt = MagicMock()
        register_pending_authorization(
            "fake-random-state-abc", flow_id="flow-yes",
            owner_user_id="authenticated-owner", tv_session="verified-tv",
            attempt=attempt,
        )
        request = SimpleNamespace(query={
            "state": "fake-random-state-abc", "code": "fake-code-abc"
        })
        response = await self.view.get(request)
        self.assertEqual(200, response.status)
        self.hass.config_entries.flow.async_configure.assert_awaited_once()
        args, kwargs = self.hass.config_entries.flow.async_configure.await_args
        self.assertEqual("flow-yes", args[0])
        self.assertEqual("authenticated-owner", kwargs["user_input"]["bound_owner"])
        self.assertEqual("verified-tv", kwargs["user_input"]["bound_tv"])
        self.assertIs(attempt, kwargs["user_input"]["attempt"])
        self.assertEqual("fake-code-abc", kwargs["user_input"]["oauth_code"])
        self.assertNotIn("fake-code-abc", response.text)

        second = await self.view.get(request)
        self.assertEqual(400, second.status)
        self.hass.config_entries.flow.async_configure.assert_awaited_once()

    async def test_unknown_error_cancel_and_partial_callback_fail_closed(self):
        from types import SimpleNamespace

        for query in (
            {}, {"code": "fake-code"}, {"state": "unknown", "code": "fake-code"},
            {"state": "unknown", "error": "access_denied"},
        ):
            with self.subTest(query=query):
                reply = await self.view.get(SimpleNamespace(query=query))
                self.assertEqual(400, reply.status)
        self.hass.config_entries.flow.async_configure.assert_not_awaited()

        register_pending_authorization(
            "cancel-state", flow_id="cancelled-flow",
            owner_user_id="owner", tv_session="tv", attempt=MagicMock(),
        )
        reply = await self.view.get(SimpleNamespace(query={
            "state": "cancel-state", "error": "access_denied"
        }))
        self.assertEqual(400, reply.status)
        self.assertIsNone(consume_pending_authorization("cancel-state"))
        self.hass.config_entries.flow.async_configure.assert_not_awaited()

    async def test_failed_resume_consumes_state_and_does_not_leak_code(self):
        from types import SimpleNamespace

        register_pending_authorization(
            "server-failure-state", flow_id="flow-error",
            owner_user_id="owner", tv_session="tv", attempt=MagicMock(),
        )
        self.hass.config_entries.flow.async_configure.side_effect = RuntimeError(
            "private authorization code fake-code-not-for-display"
        )
        request = SimpleNamespace(query={
            "state": "server-failure-state", "code": "fake-code-not-for-display"
        })
        reply = await self.view.get(request)
        self.assertEqual(500, reply.status)
        self.assertNotIn("fake-code", reply.text)
        self.assertIsNone(consume_pending_authorization("server-failure-state"))
        again = await self.view.get(request)
        self.assertEqual(400, again.status)

