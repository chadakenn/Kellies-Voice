"""Offline checks for #214 H1 (no owner credentials) and H4 (revocation).

No Home Assistant instance, OAuth exchange, internet or real token is used.
"""
from __future__ import annotations

from dataclasses import replace
import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock

PATH = Path(__file__).resolve().parents[1] / "custom_components/kellies_voice/pairing_device_token_guard.py"
SPEC = importlib.util.spec_from_file_location("kv_device_token_guard_tests", PATH)
guard = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = guard
SPEC.loader.exec_module(guard)
TV = "123e4567-e89b-42d3-a456-426614174000"
OTHER_TV = "223e4567-e89b-42d3-a456-426614174000"
CID = "https://ha.example/kellies-voice/tv/" + TV
ACCESS = "fake.access.value.not-a-real-token"
REFRESH = "fake.refresh.value.not-a-real-token"


class AuthFixture:
    def __init__(self, *, user_id="tv-user", admin=False, owner=False,
                 active=True, system=False, client=CID, token_type="normal"):
        self.user = SimpleNamespace(id=user_id, is_admin=admin,
                                    is_owner=owner, is_active=active,
                                    system_generated=system)
        self.token = SimpleNamespace(id="token-id-1", user=self.user,
                                     client_id=client, token_type=token_type)
        self.auth = SimpleNamespace(
            async_validate_access_token=MagicMock(return_value=self.token),
            async_get_refresh_token_by_token=MagicMock(return_value=self.token),
            async_get_refresh_token=MagicMock(return_value=self.token),
            async_remove_refresh_token=MagicMock(),
        )
        self.hass = SimpleNamespace(auth=self.auth)

    def verify(self, **changes):
        data = dict(access_token=ACCESS, refresh_token=REFRESH,
                    tv_uuid=TV, owner_user_id="ha-owner",
                    tv_user_id="tv-user", expected_client_id=CID)
        data.update(changes)
        return guard.verify_nonadmin_tv_candidate(self.hass, **data)


class CandidateTest(unittest.TestCase):
    def test_safe_candidate_yields_non_secret_durable_handle(self):
        fixture = AuthFixture()
        handle = fixture.verify()
        self.assertEqual("token-id-1", handle.token_id)
        self.assertEqual("tv-user", handle.user_id)
        self.assertEqual(TV, handle.tv_uuid)
        self.assertEqual(CID, handle.client_id)
        self.assertNotIn(ACCESS, repr(handle))
        self.assertNotIn(REFRESH, repr(handle))
        with self.assertRaises(AttributeError):
            handle.token_id = "changed"

    def test_rejects_owner_admin_or_system_identity(self):
        for change in (
            {"admin": True}, {"owner": True}, {"system": True},
            {"active": False}, {"user_id": "ha-owner"},
            {"client": "https://ha.example/"},
            {"token_type": "long_lived_access_token"},
        ):
            with self.subTest(change=change):
                fixture = AuthFixture(**change)
                with self.assertRaises(guard.TvCredentialRejected):
                    fixture.verify()

    def test_rejects_owner_self_authorization_even_if_not_admin(self):
        fixture = AuthFixture(user_id="ha-owner")
        with self.assertRaises(guard.TvCredentialRejected):
            fixture.verify(tv_user_id="ha-owner")

    def test_rejects_tampered_token_pair_and_missing_credentials(self):
        for which in ("access", "refresh", "different", "throws"):
            with self.subTest(which=which):
                fixture = AuthFixture()
                if which == "access":
                    fixture.auth.async_validate_access_token.return_value = None
                if which == "refresh":
                    fixture.auth.async_get_refresh_token_by_token.return_value = None
                if which == "different":
                    fixture.auth.async_validate_access_token.return_value = (
                        SimpleNamespace(id="other-token-id"))
                if which == "throws":
                    fixture.auth.async_validate_access_token.side_effect = RuntimeError(REFRESH)
                with self.assertRaises(guard.TvCredentialRejected) as e:
                    fixture.verify()
                self.assertNotIn(REFRESH, str(e.exception))
                self.assertNotIn(ACCESS, str(e.exception))

    def test_rejects_cross_tv_client_and_invalid_uuid(self):
        fixture = AuthFixture()
        for change in (
            {"tv_uuid": OTHER_TV},
            {"tv_uuid": TV.upper()},
            {"tv_uuid": "not-a-uuid"},
            {"expected_client_id": "https://ha.example/"},
            {"expected_client_id": "http://wrong.example/kellies-voice/tv/" + OTHER_TV},
        ):
            with self.subTest(change=change):
                with self.assertRaises(guard.TvCredentialRejected):
                    fixture.verify(**change)

    def test_proposed_client_id_is_per_tv_not_shared_owner_client(self):
        self.assertEqual(CID, guard.proposed_tv_client_id("https://ha.example/", TV))
        other = guard.proposed_tv_client_id("https://ha.example/", OTHER_TV)
        self.assertNotEqual(CID, other)
        for url in ("ftp://ha.example", "https://ha.example/path",
                    "http://user:password@ha.example",
                    "https://ha.example/?token=secret",
                    "https://ha.example/#fragment"):
            with self.subTest(url=url), self.assertRaises(guard.TvCredentialRejected):
                guard.proposed_tv_client_id(url, TV)


class RevocationTest(unittest.TestCase):
    def test_revoke_offline_by_exact_handle_and_idempotent(self):
        fixture = AuthFixture()
        handle = fixture.verify()
        self.assertTrue(guard.revoke_verified_device_handle(fixture.hass, handle))
        fixture.auth.async_remove_refresh_token.assert_called_once_with(fixture.token)
        fixture.auth.async_get_refresh_token.return_value = None
        self.assertFalse(guard.revoke_verified_device_handle(fixture.hass, handle))
        fixture.auth.async_remove_refresh_token.assert_called_once()

    def test_refuse_revoke_other_user_client_or_tv_handle(self):
        fixture = AuthFixture()
        handle = fixture.verify()
        for altered in (
            replace(handle, user_id="other-user"),
            replace(handle, client_id="https://another-client.example"),
            replace(handle, token_id="wrong-token-id"),
        ):
            with self.subTest(altered=altered):
                self.assertFalse(guard.revoke_verified_device_handle(
                    fixture.hass, altered))
                fixture.auth.async_remove_refresh_token.assert_not_called()
        fixture.auth.async_get_refresh_token.return_value = (
            SimpleNamespace(id="wrong-token-id", user=fixture.user,
                            client_id=CID))
        self.assertFalse(guard.revoke_verified_device_handle(fixture.hass, handle))
        fixture.auth.async_remove_refresh_token.assert_not_called()

    def test_no_owner_revoke_on_unverified_network_data(self):
        fixture = AuthFixture()
        with self.assertRaises(guard.TvCredentialRejected):
            guard.revoke_verified_device_handle(fixture.hass, {"token_id": "token-id-1"})
        fixture.auth.async_remove_refresh_token.assert_not_called()

    def test_revocation_failure_is_opaque_and_must_be_retried(self):
        fixture = AuthFixture()
        handle = fixture.verify()
        fixture.auth.async_remove_refresh_token.side_effect = RuntimeError(REFRESH)
        with self.assertRaises(guard.TvCredentialRejected) as e:
            guard.revoke_verified_device_handle(fixture.hass, handle)
        self.assertNotIn(REFRESH, str(e.exception))
        # The durable handle is intentionally NOT removed by this API.
        fixture.auth.async_get_refresh_token.assert_called_once_with(handle.token_id)


if __name__ == "__main__":
    unittest.main()
