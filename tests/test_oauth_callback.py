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
