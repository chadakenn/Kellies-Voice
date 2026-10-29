"""#213: tests for owner-approved OAuth callback state binding.

These tests cover the server-side state registry and rejection paths.
They do not perform a live token exchange against a real Home Assistant.
"""

import unittest
from unittest.mock import MagicMock, patch

from custom_components.kellies_voice.oauth_callback import (
    register_pending_authorization,
    consume_pending_authorization,
    OAuthCallbackView,
)
from custom_components.kellies_voice.pairing_owner_oauth import OwnerAuthorizationAttempt


class TestOAuthStateBinding(unittest.TestCase):
    def setUp(self):
        # Clear the module-level pending store between tests.
        from custom_components.kellies_voice import oauth_callback
        oauth_callback._PENDING.clear()

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


class TestOAuthCallbackViewRejection(unittest.TestCase):
    def setUp(self):
        from custom_components.kellies_voice import oauth_callback
        oauth_callback._PENDING.clear()
        self.hass = MagicMock()
        self.view = OAuthCallbackView(self.hass)

    async def test_missing_params_rejected(self):
        request = MagicMock()
        request.query = {}
        response = await self.view.get(request)
        self.assertEqual(response.status, 400)

    async def test_unknown_state_rejected(self):
        request = MagicMock()
        request.query = {"state": "nope", "code": "abc123"}
        response = await self.view.get(request)
        self.assertEqual(response.status, 400)

    async def test_error_query_rejected(self):
        request = MagicMock()
        request.query = {"error": "access_denied", "state": "x"}
        response = await self.view.get(request)
        self.assertEqual(response.status, 400)
